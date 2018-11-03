package com.asr.nascompanion

import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.LocalBroadcastManager
import android.text.method.ScrollingMovementMethod
import android.util.Log
import com.evernote.android.job.Job
import com.evernote.android.job.JobCreator
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import jcifs.smb.*

import kotlinx.android.synthetic.main.content_main.*
import java.io.File
import java.util.concurrent.TimeUnit

class NasCompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        JobManager.create(this).addJobCreator(NasJobCreator())
    }
}

class NasJobCreator : JobCreator {
    override fun create(tag: String): Job? {
        when (tag) {
            NasSyncJob.TAG -> return NasSyncJob()
            else -> return null
        }
    }
}

class NasSyncJob : Job() {
    companion object {
        const val TAG = "nas_file_sync"
        fun scheduleJob() {
            val allRequests = JobManager.instance().getAllJobRequestsForTag(NasSyncJob.TAG)
            if (!allRequests.isEmpty()) {
                Log.d(TAG, "already running jobs, skip this time's request")
                return
            }

            JobRequest.Builder(NasSyncJob.TAG)
                .setUpdateCurrent(true)
                .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
                .setRequirementsEnforced(true)
                .setPeriodic(TimeUnit.HOURS.toMillis(3), TimeUnit.HOURS.toMillis(3)) // every 3 hours, but wait 6 hours before it runs again
                .build()
                .schedule()
        }
        fun runJobImmediatelly() {
            // JobManager.instance().cancelAllForTag(NasSyncJob.TAG)
            JobRequest.Builder(NasSyncJob.TAG)
                .setUpdateCurrent(true)
                .startNow()
                .build()
                .schedule()
        }
    }
    override fun onRunJob(params: Params): Result {
        // run our job here
        if (nasSyncMediaFiles())
            return Result.SUCCESS
        else return Result.RESCHEDULE
    }

    private fun getFileList(projection: Array<String>, target_uri:android.net.Uri): MutableList<Pair<Long, String>> {
        /*val projection = arrayOf(MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN)*/
        val data_col = projection[0]
        val date_mod_col = projection[1]
        val date_taken_col = projection[2]
        val fullList= mutableListOf<Pair<Long, String>>()
        val cursor = this.context.contentResolver.query(target_uri,
            projection,
            null,
            null,
            projection[0] + " DESC")
        if (cursor == null) return fullList
        assert(! cursor.moveToFirst())
        do {
            val n_list = cursor.getString(cursor.getColumnIndex(data_col)).split("/")
            val fileName = n_list[n_list.lastIndex-1]+"/"+n_list[n_list.lastIndex]
            Log.d(TAG, fileName+", taken on "+cursor.getString(cursor.getColumnIndex(date_taken_col)))

            fullList.add(Pair(cursor.getLong(cursor.getColumnIndex(date_mod_col)),
                cursor.getString(cursor.getColumnIndex(data_col))))

        } while(cursor.moveToNext())
        Log.d(TAG,"media count: " + cursor.count.toString())
        cursor.close()
        return fullList
    }
    private fun nasSyncMediaFiles(): Boolean {
        val projection = arrayOf(MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN)
/*        val selection = MediaStore.Images.Media.BUCKET_ID + " = ?"
        val CAMERA_IMAGE_BUCKET_NAME = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera"
        val CAMERA_IMAGE_BUCKET_ID = getBucketId(CAMERA_IMAGE_BUCKET_NAME)
        val selectionArgs = arrayOf(CAMERA_IMAGE_BUCKET_ID) */
        val fullList= mutableListOf<Pair<Long, String>>()

        var returnVal = true

        for (i in getFileList(arrayOf(MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN), MediaStore.Images.Media.EXTERNAL_CONTENT_URI) ) {
            fullList.add(i)
        }
        Log.d(TAG, "Phone media files count:" + fullList.size)
        for (i in getFileList(arrayOf(MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_TAKEN), MediaStore.Video.Media.EXTERNAL_CONTENT_URI) ) {
            fullList.add(i)
        }
        Log.d(TAG, "Phone media files count:" + fullList.size)
        for (i in getFileList(arrayOf(MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_MODIFIED), MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) ) {
            fullList.add(i)
        }
        Log.d(TAG, "Phone media files count:" + fullList.size)

        for (i in fullList) {
            copyToNas(i.second, i.first )
        }

        return returnVal
    }
    private fun getBucketId(path: String): String {
        return path.toLowerCase().hashCode().toString()
    }

    private fun copyToNas(path: String, date_taken: Long): Boolean {
        val intent = Intent()
        intent.action = "com.asr.nascompanion.updateStatus"

        val prefs = PreferenceManager.getDefaultSharedPreferences(this.context)

        val wifiManager:WifiManager = this.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val targetSsidList = prefs.getString("wifi_ssid", this.context.getString(R.string.pref_default_wifi_ssid)).split(",").toTypedArray()
        val ssid  = wifiManager.connectionInfo.ssid
        var i = targetSsidList[0]
        if (ssid.substring(1,ssid.lastIndex) !in targetSsidList) {
            return false
        }

        var smb_auth = NtlmPasswordAuthentication.ANONYMOUS
        if ("anonymous" != prefs.getString("server_user", this.context.getString(R.string.pref_default_server_user))) {
            smb_auth = NtlmPasswordAuthentication(
                null,
                prefs.getString("server_user", this.context.getString(R.string.pref_default_server_user)),
                prefs.getString("server_pass", this.context.getString(R.string.pref_default_server_pass)))
        }


        val server_addr = prefs.getString("server_url", this.context.getString(R.string.pref_default_server_url)) +"/nas_" + Build.MODEL
        val n_list = path.split("/")
        val folder_name = n_list[n_list.lastIndex-1]
        val file_name = n_list[n_list.lastIndex]

        var fileResult = "Done"

        /*Log.d(TAG, "To create file " + server_addr +"/"+folder_name+"/"+file_name)*/

/*      test to write local file.
        val to_file = openFileOutput("test.test", MODE_PRIVATE)
        from_file.copyTo(to_file)*/

        intent.putExtra("msg", "\nCopying "+folder_name+"/"+file_name+"...")
        LocalBroadcastManager.getInstance(this.context).sendBroadcastSync(intent)

        try {
            var smb_path = SmbFile(server_addr, smb_auth)
            if (!smb_path.exists()) {
                smb_path.mkdir()
            }
            smb_path = SmbFile(server_addr + "/" + folder_name, smb_auth)
            if (!smb_path.exists()) {
                smb_path.mkdir()
            }
            smb_path = SmbFile(server_addr + "/" + folder_name + "/" + file_name, smb_auth)
            /* check if file exists and size is same as original file */
            val fileSize = File(path).length()
            if (!smb_path.exists() || fileSize != smb_path.length()) {
                if (smb_path.exists()) smb_path.delete()
                val fromFile = File(path).inputStream()
                fromFile.use{ input ->
                    smb_path.outputStream.use {output ->
                        input.copyTo(output)
                    }
                }
                smb_path.lastModified = date_taken.toLong() /* this must be done after outputstream.close() */
                /*Log.d(TAG, "Created new file time (taken) is: " +smb_path.lastModified().toString()) */
                Log.d(TAG, "file size compare (taken) is: "+path+":" + fileSize + " vs. "+smb_path.contentLength + "," + if (fileSize.toInt() == smb_path.contentLength) "Same" else "Diff" )
            } else {
                fileResult = "Already exists"
            }
        } catch (e: SmbException) {
            Log.d(TAG, "Samba connection met issue:"+e.localizedMessage)
            fileResult = "Network down"
            Thread.sleep(2000)
        }
        intent.putExtra("msg", fileResult)
        LocalBroadcastManager.getInstance(this.context).sendBroadcastSync(intent)

        return true
    }
}

class MainActivity : AppCompatActivity() {
    var broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.asr.nascompanion.updateStatus" ->  {
                    //Log.d(TAG, "received Broadcast for: "+intent.getStringExtra("msg"))
                    runOnUiThread { -> textBox.append(intent.getStringExtra("msg"))}
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textBox.movementMethod = ScrollingMovementMethod()

        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(broadCastReceiver, IntentFilter("com.asr.nascompanion.updateStatus"))

        setSupportActionBar(toolbar)
        toast("start running")

        /* request for permissions */
        val REQUEST_READ_STORAGE_REQUEST_CODE = 112
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_READ_STORAGE_REQUEST_CODE)

        checkPermission()

        NasSyncJob.scheduleJob()

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            if ( checkPermission() ) {
                NasSyncJob.runJobImmediatelly()
            }
            else
                longToast("No permission to read storage files. Need to be granted.")
            //applicationContext.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver((broadCastReceiver))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                val i = Intent(this, SettingsActivity::class.java)
                startActivity(i)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) textBox.text = "Error: Android SDK version doesn't meet requirement"
        else if (checkPermission())
            textBox.text = "Permissions granted, ready to sync photos"
        else
            textBox.text = "Permissions not granted, please manually grant READ_EXTERNAL_STORAGE and ACCESS_COARSE_LOCATION permission"
    }
    private fun checkPermission(): Boolean {
        /* referred to https://github.com/googlesamples/android-RuntimePermissions */
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) return false
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            return true
        return false
    }

    companion object {
        private val TAG = "NasComp"
    }
}
