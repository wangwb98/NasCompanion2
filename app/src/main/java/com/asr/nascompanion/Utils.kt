package com.asr.nascompanion

import android.provider.MediaStore
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

const val UTILS_TAG = "NasUtils"
fun convertLongToTime(time: Long): String {
    val date = Date(time)
    val format = SimpleDateFormat("yyyy.MM.dd HH:mm")
    return format.format(date)
}

fun loadSyncFolderList(): MutableList<String> {
    val file = File(NasCompanionApp.applicationContext().filesDir, "syncfl.bin")
    val syncFolderList = ArrayList<String>()

    if (! file.exists() ) {
        ObjectOutputStream(FileOutputStream(file)).use {
                it -> it.writeObject(syncFolderList)
        }
    }
    ObjectInputStream(FileInputStream(file)).use{ it ->
        val flist = it.readObject()
        when (flist) {
            is ArrayList<*> -> {
                syncFolderList.addAll (flist as ArrayList<String>)
            }
            else -> Log.e(UTILS_TAG, "Loading from file syncfl.bin failed!")
        }
        //Log.d(TAG, medialist.toString())
    }
    return syncFolderList
/*
    ObjectOutputStream(FileOutputStream(file)).use {
            it -> it.writeObject(origFullList)
    }*/
}

fun storeSyncFolderList(syncFolderList: MutableList<String>) {
    val file = File(NasCompanionApp.applicationContext().filesDir, "syncfl.bin")
    ObjectOutputStream(FileOutputStream(file)).use {
            it -> it.writeObject(syncFolderList)
    }
}

fun getAllMediaFiles(): MutableList <Pair <Long, String>> {
    val fullList= mutableListOf<Pair<Long, String>>()

    for (i in getFileList(arrayOf(
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.DATE_TAKEN), MediaStore.Images.Media.EXTERNAL_CONTENT_URI) ) {
        fullList.add(i)
    }
    Log.d(UTILS_TAG, "Phone img files count:" + fullList.size)
    for (i in getFileList(arrayOf(
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.DATE_TAKEN), MediaStore.Video.Media.EXTERNAL_CONTENT_URI) ) {
        fullList.add(i)
    }
    Log.d(UTILS_TAG, "Phone img/video files count:" + fullList.size)
    /*for (i in getFileList(arrayOf(MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.DATE_MODIFIED), MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) ) {
        fullList.add(i)
    }
    Log.d(TAG, "Phone img/video/audio files count:" + fullList.size)*/
    return fullList
}

private fun getFileList(projection: Array<String>, target_uri:android.net.Uri): MutableList<Pair<Long, String>> {
    /*val projection = arrayOf(MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.DATE_TAKEN)*/
    val data_col = projection[0]
    val date_mod_col = projection[1]
    //val date_taken_col = projection[2]
    val fullList= mutableListOf<Pair<Long, String>>()
    val cursor = NasCompanionApp.applicationContext().contentResolver.query(target_uri,
        projection,
        null,
        null,
        projection[0] + " DESC")
    if (cursor == null) return fullList
    assert(! cursor.moveToFirst())
    Log.d(NasSyncJob.TAG, cursor.columnNames.toString() )
    do {
        val dataItem = cursor.getString(cursor.getColumnIndex(data_col))
        //Log.d(TAG, dataItem)
        val n_list = dataItem.split("/")
        val fileName = n_list[n_list.lastIndex-1]+"/"+n_list[n_list.lastIndex]
        // Log.d(TAG, fileName+", taken on "+cursor.getString(cursor.getColumnIndex(date_taken_col)))

        fullList.add(Pair(cursor.getLong(cursor.getColumnIndex(date_mod_col)),
            cursor.getString(cursor.getColumnIndex(data_col))))

    } while(cursor.moveToNext())
    Log.d(UTILS_TAG,"media count: " + cursor.count.toString())
    cursor.close()
    return fullList
}
