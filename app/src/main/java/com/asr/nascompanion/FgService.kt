package com.asr.nascompanion

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.evernote.android.job.Job
import com.evernote.android.job.JobCreator
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import com.evernote.android.job.util.support.PersistableBundleCompat
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class FgService : Service() {
    companion object {
        val CHANNEL_ID = "123"
        val notificationId = 111
    }

    override fun onCreate() {
        super.onCreate()
        JobManager.create(this).addJobCreator(NasJobCreator())
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun getMyNotification(text: String?) : Notification {
        createNotificationChannel()
        // Create an explicit intent for an Activity in your app
        val intent2 = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // magically we should not use requestCode 0, otherwise MainActivity will be created again.
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 1, intent2, 0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            //notify(notificationId, builder.build())
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val mEnd = prefs.getLong("LastSyncEndDateLong",0)
            if (mEnd == 0L ) {
                startForeground(notificationId, getMyNotification("INFO: Never synced on this device."))
            }
            else
                startForeground(notificationId, getMyNotification(getString(R.string.notification_content).format(convertLongToTime(mEnd))))
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val server_user = prefs.getString("server_user", applicationContext.getString(R.string.pref_default_server_user))
        val server_pass = prefs.getString("server_pass", applicationContext.getString(R.string.pref_default_server_pass))
        val server_addr = prefs.getString("server_url", applicationContext.getString(R.string.pref_default_server_url))
        val targetSsidList = prefs.getString("wifi_ssid", applicationContext.getString(R.string.pref_default_wifi_ssid))

        NasSyncJob.scheduleJob(interval = prefs.getString("sync_frequency", "180").toInt(), user = server_user,
            pass = server_pass, addr = server_addr, ssidList = targetSsidList)

        return START_STICKY
    }
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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

    fun updateFgNotification(text: String?) {
        // Create an explicit intent for an Activity in your app
        // magically we should not use requestCode 0, otherwise MainActivity will be created again.
        val intent2 = Intent(context, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // magically we should not use requestCode 0, otherwise MainActivity will be created again.
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 1, intent2, 0)

        val notif = NotificationCompat.Builder(context, FgService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(text)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .build()

        val mNotifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        mNotifManager!!.notify(FgService.notificationId, notif)
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

        val t = context.getString(R.string.notification_update).format(if (result) "pass" else "fail",convertLongToTime(System.currentTimeMillis()))
        updateFgNotification(t)

//        FgService.updateFg

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

        for (i in getFileList(arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN), MediaStore.Images.Media.EXTERNAL_CONTENT_URI) ) {
            fullList.add(i)
        }
        Log.d(TAG, "Phone img files count:" + fullList.size)
        for (i in getFileList(arrayOf(
            MediaStore.Video.Media.DATA,
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

            //Log.d(TAG, medialist.toString())
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

        val wifiManager: WifiManager = this.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
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


