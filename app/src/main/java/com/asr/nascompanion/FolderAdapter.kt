package com.asr.nascompanion

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.folder_tile.view.*
import org.jetbrains.anko.*

class FolderAdapter(val fullList:  MutableList<String>, val folderSynced: MutableList<String>) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {
    inner class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
        val vcheckbox = view.checkBox
        val vFolderName = view.folderName

        init {
            view.setOnClickListener {
                val name = vFolderName.text.toString()
                //NasCompanionApp.applicationContext().toast("test")
                if (folderSynced.contains(name)) {
                    folderSynced.remove(name)
                    //Log.d(UTILS_TAG, "removing %s".format(name))
                }
                else {
                    folderSynced.add(name)
                    //Log.d(UTILS_TAG, "adding %s".format(name))
                }
                // save the folder list to storage device.
                //Log.d(UTILS_TAG, "full list is: %s".format(folderSynced.joinToString(separator =", ", prefix = "[",postfix="]")))
                storeSyncFolderList(folderSynced)
                vcheckbox.toggle()
            }
        }
    }

    override fun getItemCount(): Int {
        return fullList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.folder_tile, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.vFolderName.text = fullList[position]
        holder.vcheckbox?.isChecked = folderSynced.contains(fullList[position])
    }
}

