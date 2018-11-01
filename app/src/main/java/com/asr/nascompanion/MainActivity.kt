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
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.support.v4.app.ActivityCompat
import android.util.Log

import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        toast("start running")

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            if ( checkPermission() )
                listMediaFiles()
            else
                longToast("No permission to read storage files. Need to be granted.")
            //applicationContext.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
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

    private fun checkPermission(): Boolean {
        /* referred to https://github.com/googlesamples/android-RuntimePermissions */
        val REQUEST_READ_STORAGE_REQUEST_CODE = 112
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) return false
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            return true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_READ_STORAGE_REQUEST_CODE)
        return true
    }

    private fun listMediaFiles(): Pair<Long, String>? {
        val CAMERA_IMAGE_BUCKET_NAME = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera"
        val CAMERA_IMAGE_BUCKET_ID = getBucketId(CAMERA_IMAGE_BUCKET_NAME)
        val projection = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = MediaStore.Images.Media.BUCKET_ID + " = ?"
        val selectionArgs = arrayOf(CAMERA_IMAGE_BUCKET_ID)
        var cameraPair: Pair<Long, String>? = null

        var cursor = applicationContext.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
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
            Log.d(TAG, cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)))
            copyToNas(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)))
        } while(cursor.moveToNext())
        //toast(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)))
        toast(cursor.count.toString())

        return cameraPair
    }

    private fun copyToNas(path: String): Boolean {
        val server_addr = "smb://192.168.0.2/public/"+Build.MODEL
        val n_list = path.split("/")
        val folder_name = n_list[n_list.lastIndex-1]
        val file_name = n_list[n_list.lastIndex]
        Log.d(TAG, "To create file " + server_addr +"/"+folder_name+"/"+file_name)
/*        val to_file = openFileOutput("test.test", MODE_PRIVATE)
        from_file.copyTo(to_file)*/

        var smb_path = SmbFile(server_addr, NtlmPasswordAuthentication.ANONYMOUS)
        if (! smb_path.exists()) {
            smb_path.mkdir()
        }
        smb_path = SmbFile(server_addr +"/"+folder_name, NtlmPasswordAuthentication.ANONYMOUS)
        if (! smb_path.exists()) {
            smb_path.mkdir()
        }
        smb_path = SmbFile(server_addr +"/"+folder_name+"/"+file_name, NtlmPasswordAuthentication.ANONYMOUS)
        val from_file = File(path).inputStream()
        Log.d(TAG, from_file.available().toString())
        from_file.copyTo(smb_path.outputStream)
        from_file.close()
        return true
    }
    private fun getBucketId(path: String): String {
        return path.toLowerCase().hashCode().toString()
    }

    companion object {
        private val TAG = "NasComp"
    }
}
