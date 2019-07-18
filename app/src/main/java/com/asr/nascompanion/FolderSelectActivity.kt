package com.asr.nascompanion

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_folder_select.*

class FolderSelectActivity : AppCompatActivity() {

    companion object {
        const val TAG = "FolderSel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_select)

        val folderList= mutableListOf<String>()
        var folderSynced = loadSyncFolderList()
        for (i in getAllMediaFiles()) {
            val nList = i.second.split("/")
            val folderName = nList[nList.lastIndex-1]
            //Log.d(TAG, "foldername:" + folderName)

            if ( !folderList.contains(folderName))
                folderList.add(folderName)
        }

        if (folderSynced.isEmpty()){
            storeSyncFolderList(folderList)
            folderSynced = folderList
        }
        rv_folders.layoutManager = LinearLayoutManager(this)
        rv_folders.adapter = FolderAdapter(folderList, folderSynced)
    }
}
