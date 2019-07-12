package com.asr.nascompanion

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_folder_select.*

class FolderSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_select)

        rv_folders.layoutManager = LinearLayoutManager(this)
        rv_folders.adapter = FolderAdapter(this)
    }
}
