package com.asr.nascompanion

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

fun convertLongToTime(time: Long): String {
    val date = Date(time)
    val format = SimpleDateFormat("yyyy.MM.dd HH:mm")
    return format.format(date)
}

fun getFileList(projection: Array<String>, target_uri:android.net.Uri): MutableList<Pair<Long, String>> {
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
    Log.d(NasSyncJob.TAG,"media count: " + cursor.count.toString())
    cursor.close()
    return fullList
}
