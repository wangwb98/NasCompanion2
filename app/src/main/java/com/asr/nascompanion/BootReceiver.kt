package com.asr.nascompanion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.jetbrains.anko.toast

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // This method is called when the BroadcastReceiver is receiving an Intent broadcast.
        // Log.d("BootReceiver", "received {${intent.action}}")
        // context.toast("received {${intent.action}}")
        val i = Intent(context, MainActivity::class.java)
        Thread.sleep(20*1000)
        context.startActivity(i)
    }
}
