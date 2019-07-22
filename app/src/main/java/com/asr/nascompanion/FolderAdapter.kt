package com.asr.nascompanion

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.folder_tile.view.*
import org.jetbrains.anko.*

class FolderAdapter(val fullList:  MutableList<String>, val folderNoSynced: MutableList<String>, val longPathList: MutableList<String>) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {
    inner class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
        val vcheckbox = view.checkBox
        val vFolderName = view.folderName
        val vImageView = view.imageView
        val v = view

        init {
            view.setOnClickListener {
                val name = vFolderName.text.toString()
                //NasCompanionApp.applicationContext().toast("test")
                if (folderNoSynced.contains(name)) {
                    folderNoSynced.remove(name)
                    //Log.d(UTILS_TAG, "removing %s".format(name))
                }
                else {
                    folderNoSynced.add(name)
                    //Log.d(UTILS_TAG, "adding %s".format(name))
                }
                // save the folder list to storage device.
                //Log.d(UTILS_TAG, "full list is: %s".format(folderNoSynced.joinToString(separator =", ", prefix = "[",postfix="]")))
                storeSyncFolderList(folderNoSynced)
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
        holder.vcheckbox?.isChecked = !folderNoSynced.contains(fullList[position])

        //Log.d(UTILS_TAG, longPathList.joinToString())
        Glide.with(holder.v).load(longPathList[position]).into(holder.vImageView)
    }
}
