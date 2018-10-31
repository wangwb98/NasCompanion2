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
import android.support.v4.app.ActivityCompat

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
        val SCREENSHOTS_IMAGE_BUCKET_NAME = Environment.getExternalStorageDirectory().toString() + "/Pictures/Screenshots"
        val CAMERA_IMAGE_BUCKET_ID = getBucketId(CAMERA_IMAGE_BUCKET_NAME)
        val SCREENSHOTS_IMAGE_BUCKET_ID = getBucketId(SCREENSHOTS_IMAGE_BUCKET_NAME)
        val projection = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_MODIFIED)
        val selection = MediaStore.Images.Media.BUCKET_ID + " = ?"
        val selectionArgs = arrayOf(CAMERA_IMAGE_BUCKET_ID)
        val selectionArgsForScreenshots = arrayOf(SCREENSHOTS_IMAGE_BUCKET_ID)
        var cameraPair: Pair<Long, String>? = null

        var cursor = applicationContext.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")
        if (cursor == null) return null
        if (cursor.moveToFirst()) {
            cameraPair = Pair(cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)),
                cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)))
        }

        var screenshotsPair: Pair<Long, String>? = null
        cursor = applicationContext.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgsForScreenshots,
            MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")
        if (cursor == null) return null
        if (cursor.moveToFirst()) {
            screenshotsPair = Pair(
                cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)),
                cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
            )
        }

        if (!cursor.isClosed) {
            cursor.close()
        }
        if (cameraPair != null && screenshotsPair != null) {
            return if (cameraPair.first > screenshotsPair.first) {
                screenshotsPair = null
                cameraPair
            } else {
                cameraPair = null
                screenshotsPair
            }
        } else if (cameraPair != null && screenshotsPair == null) {
            return cameraPair
        } else if (cameraPair ==null && screenshotsPair != null) {
            return screenshotsPair
        }
        return null
    }
    private fun getBucketId(path: String): String {
        return path.toLowerCase().hashCode().toString()
    }
}
