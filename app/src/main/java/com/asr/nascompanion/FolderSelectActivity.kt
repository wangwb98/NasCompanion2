package com.asr.nascompanion

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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
        val longPathList= mutableListOf<String>()
        var folderNoSynced = loadSyncFolderList()
        if (folderNoSynced.isEmpty()){
            storeSyncFolderList(folderNoSynced)
        }
        for (i in getAllMediaFiles()) {
            val nList = i.second.split("/")
            val folderName = nList[nList.lastIndex-1]
            //Log.d(TAG, "foldername:" + folderName)

            if ( !folderList.contains(folderName)) {
                if (folderNoSynced.contains(folderName)) {// folders in noSync list will be at the end of the list
                    folderList.add(folderName)
                    longPathList.add(i.second)
                }
                else {
                    folderList.add(0, folderName)
                    longPathList.add(0,i.second)
                }
            }
        }

        rv_folders.layoutManager = LinearLayoutManager(this)
        rv_folders.adapter = FolderAdapter(folderList, folderNoSynced, longPathList)
    }
}
