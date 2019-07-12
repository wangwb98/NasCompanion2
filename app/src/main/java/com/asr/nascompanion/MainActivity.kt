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
import androidx.recyclerview.widget.LinearLayoutManager
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
                    Log.d(TAG, "write last start time %s, end time %s".format(convertLongToTime(sTime), convertLongToTime(eTime)))

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
