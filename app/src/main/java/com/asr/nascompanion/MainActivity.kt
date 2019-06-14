package com.asr.nascompanion

import android.os.Bundle
import android.provider.MediaStore
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
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
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PersistableBundle
import android.preference.PreferenceManager
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.text.method.ScrollingMovementMethod
import android.util.Log
import com.evernote.android.job.Job
import com.evernote.android.job.JobCreator
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import com.evernote.android.job.util.support.PersistableBundleCompat
import jcifs.smb.*

import kotlinx.android.synthetic.main.content_main.*
import net.grandcentrix.tray.AppPreferences
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

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
        const val TAG = "NasCompJob"

        fun scheduleJob(interval: Int, user: String, pass: String, addr: String, ssidList: String) {
            /*val allRequests = JobManager.instance().getAllJobRequestsForTag(NasSyncJob.TAG)
            if (!allRequests.isEmpty()) {
                Log.d(TAG, "already running jobs, skip this time's request")
                return
            }*/
            Log.d(TAG, "scheduleJob: Sync interval set to "+interval.toString())
            val extras = PersistableBundleCompat()
            extras.putString("server_user", user)
            extras.putString("server_pass", pass)
            extras.putString("server_url", addr)
            extras.putString("wifi_ssid", ssidList)

            if (interval < 0)
                JobManager.instance().cancelAllForTag(NasSyncJob.TAG)
            else
                JobRequest.Builder(NasSyncJob.TAG)
                    .setUpdateCurrent(true)
                    .setExtras(extras)
                    .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
                    .setRequirementsEnforced(true)
                    .setPeriodic(TimeUnit.MINUTES.toMillis(interval.toLong()), TimeUnit.MINUTES.toMillis(5.toLong())) // every 180 minutes hours, but wait 5 minutes hours before runs again
                    .build()
                    .schedule()
        }
        fun runJobImmediatelly(user: String, pass: String, addr: String, ssidList: String) {
            // JobManager.instance().cancelAllForTag(NasSyncJob.TAG)
            Log.d(TAG, "runJobImmediatelly: start now.")
            val extras = PersistableBundleCompat()
            extras.putString("server_user", user)
            extras.putString("server_pass", pass)
            extras.putString("server_url", addr)
            extras.putString("wifi_ssid", ssidList)

            JobRequest.Builder(NasSyncJob.TAG)
                .setUpdateCurrent(true)
                .setExtras(extras)
                .startNow()
                .build()
                .schedule()
        }
    }
    override fun onRunJob(params: Params): Result {

        val intent = Intent()
        intent.action = "com.asr.nascompanion.updateTime"
        intent.putExtra("startTime", System.currentTimeMillis())
        LocalBroadcastManager.getInstance(this.context).sendBroadcastSync(intent)

/*
        var prefs = PreferenceManager.getDefaultSharedPreferences(this.context)
        prefs.edit()
            .putLong("LastSyncStartDateLong", System.currentTimeMillis())
            .apply()*/
        // run our job here
        Log.d(TAG, "onRunJob: nasSyncMediaFiles starts. ")
        val result = nasSyncMediaFiles(params)
        Log.d(TAG, "onRunJob: nasSyncMediaFiles done. ")

        intent.action = "com.asr.nascompanion.updateTime"
        intent.putExtra("endTime", System.currentTimeMillis())
        LocalBroadcastManager.getInstance(this.context).sendBroadcastSync(intent)
/*
        prefs.edit()
            .putLong("LastSyncEndDateLong", System.currentTimeMillis())
            .apply()
*/
        if (result)
            return Result.SUCCESS
        else return Result.RESCHEDULE
    }

    private fun getFileList(projection: Array<String>, target_uri:android.net.Uri): MutableList<Pair<Long, String>> {
        /*val projection = arrayOf(MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN)*/
        val data_col = projection[0]
        val date_mod_col = projection[1]
        //val date_taken_col = projection[2]
        val fullList= mutableListOf<Pair<Long, String>>()
        val cursor = this.context.contentResolver.query(target_uri,
            projection,
            null,
            null,
            projection[0] + " DESC")
        if (cursor == null) return fullList
        assert(! cursor.moveToFirst())
        Log.d(TAG, cursor.columnNames.toString() )
        do {
            val dataItem = cursor.getString(cursor.getColumnIndex(data_col))
            //Log.d(TAG, dataItem)
            val n_list = dataItem.split("/")
            val fileName = n_list[n_list.lastIndex-1]+"/"+n_list[n_list.lastIndex]
            // Log.d(TAG, fileName+", taken on "+cursor.getString(cursor.getColumnIndex(date_taken_col)))

            fullList.add(Pair(cursor.getLong(cursor.getColumnIndex(date_mod_col)),
                cursor.getString(cursor.getColumnIndex(data_col))))

        } while(cursor.moveToNext())
        Log.d(TAG,"media count: " + cursor.count.toString())
        cursor.close()
        return fullList
    }
    private fun nasSyncMediaFiles(params: Params): Boolean {
/*        val selection = MediaStore.Images.Media.BUCKET_ID + " = ?"
        val CAMERA_IMAGE_BUCKET_NAME = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera"
        val CAMERA_IMAGE_BUCKET_ID = getBucketId(CAMERA_IMAGE_BUCKET_NAME)
        val selectionArgs = arrayOf(CAMERA_IMAGE_BUCKET_ID) */
        val file = File(context.filesDir, "medialist.bin")


        val fullList= mutableListOf<Pair<Long, String>>()
/*        ObjectOutputStream(FileOutputStream(file)).use {
                it -> it.writeObject(fullList)
        }*/

        var returnVal = true

        for (i in getFileList(arrayOf(MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN), MediaStore.Images.Media.EXTERNAL_CONTENT_URI) ) {
            fullList.add(i)
        }
        Log.d(TAG, "Phone img files count:" + fullList.size)
        for (i in getFileList(arrayOf(MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_TAKEN), MediaStore.Video.Media.EXTERNAL_CONTENT_URI) ) {
            fullList.add(i)
        }
        Log.d(TAG, "Phone img/video files count:" + fullList.size)
        /*for (i in getFileList(arrayOf(MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_MODIFIED), MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) ) {
            fullList.add(i)
        }
        Log.d(TAG, "Phone img/video/audio files count:" + fullList.size)*/

        val origFullList = ArrayList<Pair<Long, String>>()

        if (! file.exists() ) {
            ObjectOutputStream(FileOutputStream(file)).use {
                    it -> it.writeObject(origFullList)
            }
        }
        ObjectInputStream(FileInputStream(file)).use{ it ->
            val medialist = it.readObject()
            when (medialist) {
                is ArrayList<*> -> {
                    Log.d(TAG, "Loading from file medialist.bin succeeded.")
                    origFullList.addAll (medialist as ArrayList<Pair<Long, String>>)
                }
                else -> Log.e(TAG, "Loading from file medialist.bin failed!")
            }

            Log.d(TAG, medialist.toString())
        }


        for (i in fullList) {
            if (i in origFullList) {
                /*Log.d(TAG, i.second + " file already synced in history.")*/
                val intent = Intent()
                intent.action = "com.asr.nascompanion.updateStatus"
                intent.putExtra("msg", "\n"+i.second+" already synced before.")
                LocalBroadcastManager.getInstance(this.context).sendBroadcastSync(intent)
            }
            else {
                if (copyToNas(i.second, i.first, params))
                    origFullList.add(i)
            }
        }

        ObjectOutputStream(FileOutputStream(file)).use {
                it -> it.writeObject(origFullList)
        }

        val intent = Intent()
        intent.action = "com.asr.nascompanion.updateStatus"
        intent.putExtra("msg", "\n"+" Sync finished\n")
        LocalBroadcastManager.getInstance(this.context).sendBroadcastSync(intent)

        return returnVal
    }
    private fun getBucketId(path: String): String {
        return path.toLowerCase().hashCode().toString()
    }

    private fun copyToNas(path: String, date_taken: Long, params: Params): Boolean {
        var ret = true
        val intent = Intent()
        intent.action = "com.asr.nascompanion.updateStatus"

        val wifiManager:WifiManager = this.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        //val targetSsidList = prefs.getString("wifi_ssid", this.context.getString(R.string.pref_default_wifi_ssid)).split(",").toTypedArray()
        val targetSsidList = params.extras.getString("wifi_ssid", this.context.getString(R.string.pref_default_wifi_ssid)).split(",").toTypedArray()
        val ssid  = wifiManager.connectionInfo.ssid
        if (ssid.substring(1,ssid.lastIndex) !in targetSsidList) {
            return false
        }

        var smb_auth = NtlmPasswordAuthentication.ANONYMOUS
        if ("anonymous" != params.extras.getString("server_user", this.context.getString(R.string.pref_default_server_user))) {
            smb_auth = NtlmPasswordAuthentication(
                null,
                params.extras.getString("server_user", this.context.getString(R.string.pref_default_server_user)),
                params.extras.getString("server_pass", this.context.getString(R.string.pref_default_server_pass)))
        }

        val server_addr = params.extras.getString("server_url", this.context.getString(R.string.pref_default_server_url)) +"/nas_" + Build.MODEL.replace(" ","_")
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
                val fileModifiedTime = File(path).lastModified()
                smb_path.lastModified = fileModifiedTime /* this must be done after outputstream.close() */

                /*Log.d(TAG, "Created new file time (taken) is: " +smb_path.lastModified().toString())
                Log.d(TAG, "file mod time compare (taken) is: "+path+":" + fileModifiedTime + " vs. "+date_taken.toLong()+ "," + if (fileModifiedTime.toLong() == date_taken.toLong().toLong()*1000) "Same" else "Diff" )
                Log.d(TAG, "file size compare (taken) is: "+path+":" + fileSize + " vs. "+smb_path.contentLength + "," + if (fileSize.toInt() == smb_path.contentLength) "Same" else "Diff" )*/
            } else {
                fileResult = "Already exists"
                /* use this if want to force update modified time of smb existing files.
                val fileModifiedTime = File(path).lastModified()
                smb_path.lastModified = fileModifiedTime */

            }
        } catch (e: Exception) {
            when (e) {
                is SmbException -> {
                    Log.d(TAG, "Samba connection met issue:"+e.localizedMessage)
                    fileResult = "Network down"
                    ret = false
                }
                is java.io.FileNotFoundException -> {
                    Log.d(TAG, "Local file not found:"+e.localizedMessage)
                    fileResult = "Local file not found"
                    ret = false
                }
                else -> throw e
            }
        }
        intent.putExtra("msg", fileResult)
        LocalBroadcastManager.getInstance(this.context).sendBroadcastSync(intent)

        return ret
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
                "com.asr.nascompanion.updateTime" -> {
                    var sTime = intent.getLongExtra("startTime", 0)
                    var eTime = intent.getLongExtra("endTime", 0)
                    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                    if (sTime == 0L)
                        sTime = prefs.getLong("LastSyncStartDateLong", 0)
                    if (eTime == 0L)
                        eTime = prefs.getLong("LastSyncEndDateLong",0 )
                    prefs.edit()
                        .putLong("LastSyncEndDateLong", eTime)
                        .putLong("LastSyncStartDateLong", sTime)
                        .apply()

                    runOnUiThread { ->
                        val mStart = convertLongToTime(prefs.getLong("LastSyncStartDateLong",0))
                        val mEnd = convertLongToTime(prefs.getLong("LastSyncEndDateLong",0))
                        textTime.text = "Last Sync: "+ mStart + " -> "+ mEnd
                    }
                }
            }
        }
    }
    var NetworkConnectChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "NetConn changed: received intent " + intent?.action.toString())
            when (intent?.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION ->  {
                    //Log.d(TAG, "received Broadcast for: "+intent.getStringExtra("msg"))
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0)
                    Log.d(TAG, "WIFI state changed: " +
                        when (wifiState) {
                            WifiManager.WIFI_STATE_DISABLED -> "WIFI disabled"
                            WifiManager.WIFI_STATE_ENABLED -> "WIFI enabled"
                            else -> "Other state"
                        }
                    )
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    Log.d(TAG, "Network state changed!")
                }
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    Log.d(TAG, "connectivity state changed!")

                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textBox.movementMethod = ScrollingMovementMethod()

        val localFilter = IntentFilter()
        localFilter.addAction("com.asr.nascompanion.updateStatus")
        localFilter.addAction("com.asr.nascompanion.updateTime")
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(broadCastReceiver, localFilter)

        val filter = IntentFilter()
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        this.application.registerReceiver(NetworkConnectChangedReceiver, filter)
        setSupportActionBar(toolbar)
        toast("start running")

        val i = Intent(this, FgService::class.java)
        startForegroundService(i)

        /* request for permissions */
        val REQUEST_READ_STORAGE_REQUEST_CODE = 112
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_READ_STORAGE_REQUEST_CODE)

        checkPermission()

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val server_user = prefs.getString("server_user", applicationContext.getString(R.string.pref_default_server_user))
        val server_pass = prefs.getString("server_pass", applicationContext.getString(R.string.pref_default_server_pass))
        val server_addr = prefs.getString("server_url", applicationContext.getString(R.string.pref_default_server_url))
        val targetSsidList = prefs.getString("wifi_ssid", applicationContext.getString(R.string.pref_default_wifi_ssid))

        NasSyncJob.scheduleJob(interval = prefs.getString("sync_frequency", "180").toInt(), user = server_user,
            pass = server_pass, addr = server_addr, ssidList = targetSsidList)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            if ( checkPermission() ) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val server_user = prefs.getString("server_user", applicationContext.getString(R.string.pref_default_server_user))
                val server_pass = prefs.getString("server_pass", applicationContext.getString(R.string.pref_default_server_pass))
                val server_addr = prefs.getString("server_url", applicationContext.getString(R.string.pref_default_server_url))
                val targetSsidList = prefs.getString("wifi_ssid", applicationContext.getString(R.string.pref_default_wifi_ssid))
                NasSyncJob.runJobImmediatelly(user = server_user,
                    pass = server_pass, addr = server_addr, ssidList = targetSsidList)
            }
            else
                longToast("No permission to read storage files. Need to be granted.")
            //applicationContext.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }

    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val mStart = convertLongToTime(prefs.getLong("LastSyncStartDateLong",0))
        val mEnd = convertLongToTime(prefs.getLong("LastSyncEndDateLong",0))
        textTime.text = "Last Sync: "+ mStart + " -> "+ mEnd
    }
    private fun convertLongToTime(time: Long): String {
        val date = Date(time)
        val format = SimpleDateFormat("yyyy.MM.dd HH:mm")
        return format.format(date)
    }
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver((broadCastReceiver))
        this.application.unregisterReceiver(NetworkConnectChangedReceiver)

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
            textBox.append("Permissions granted, ready to sync photos\n")
        else
            textBox.append("Permissions not granted, please manually grant READ_EXTERNAL_STORAGE and ACCESS_COARSE_LOCATION permission\n")
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
