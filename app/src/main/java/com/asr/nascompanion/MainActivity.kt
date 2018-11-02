package com.asr.nascompanion

import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity;
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
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
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
        val wifiManager:WifiManager = this.context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val wifiInfo  = wifiManager.connectionInfo

        val ssid = wifiInfo.ssid
        Log.d(TAG, "ssid:"+ssid)
        if (ssid == "xxxx") { /* todo: change the ssid with preferenceFragment */
            NasSyncMediaFiles()
            return Result.SUCCESS
        }
        return Result.RESCHEDULE
    }
    private fun NasSyncMediaFiles(): Pair<Long, String>? {
        val CAMERA_IMAGE_BUCKET_NAME = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera"
        val CAMERA_IMAGE_BUCKET_ID = getBucketId(CAMERA_IMAGE_BUCKET_NAME)
        val projection = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DISPLAY_NAME)
/*        val selection = MediaStore.Images.Media.BUCKET_ID + " = ?"
        val selectionArgs = arrayOf(CAMERA_IMAGE_BUCKET_ID)*/
        var cameraPair: Pair<Long, String>? = null

        var cursor = this.context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Files.FileColumns.DATA + " DESC")
        if (cursor == null) return null
        if (cursor.moveToFirst()) {
            cameraPair = Pair(cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)),
                cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)))
        }
        do {
            val n_list = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)).split("/")
            val picName = n_list[n_list.lastIndex-1]+"/"+n_list[n_list.lastIndex]
            Log.d(TAG, picName)
            copyToNas(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)))
        } while(cursor.moveToNext())
        //toast(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)))
        Log.d(TAG,cursor.count.toString())
        return cameraPair
    }
    private fun getBucketId(path: String): String {
        return path.toLowerCase().hashCode().toString()
    }

    private fun copyToNas(path: String): Boolean {
        val intent = Intent()
        intent.action = "com.asr.nascompanion.updateStatus"

        val server_addr = "smb://192.168.0.2/public/"+Build.MODEL
        val n_list = path.split("/")
        val folder_name = n_list[n_list.lastIndex-1]
        val file_name = n_list[n_list.lastIndex]

        var fileResult = "Done"

        Log.d(TAG, "To create file " + server_addr +"/"+folder_name+"/"+file_name)
/*      test to write local file.
        val to_file = openFileOutput("test.test", MODE_PRIVATE)
        from_file.copyTo(to_file)*/

        intent.putExtra("msg", "\nCopying "+folder_name+"/"+file_name+"...")
        LocalBroadcastManager.getInstance(this.context).sendBroadcastSync(intent)

        try {
            var smb_path = SmbFile(server_addr, NtlmPasswordAuthentication.ANONYMOUS)
            if (!smb_path.exists()) {
                smb_path.mkdir()
            }
            smb_path = SmbFile(server_addr + "/" + folder_name, NtlmPasswordAuthentication.ANONYMOUS)
            if (!smb_path.exists()) {
                smb_path.mkdir()
            }
            smb_path = SmbFile(server_addr + "/" + folder_name + "/" + file_name, NtlmPasswordAuthentication.ANONYMOUS)
            if (!smb_path.exists()) {
                val from_file = File(path).inputStream()
                from_file.use { input ->
                    smb_path.outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, from_file.available().toString())
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
                    Log.d(TAG, "received Broadcast for: "+intent.getStringExtra("msg"))
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
            R.id.action_settings -> true
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
