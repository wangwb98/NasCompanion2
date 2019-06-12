package com.asr.nascompanion

import android.app.NotificationChannel
import android.app.Service
import android.content.Intent
import android.os.IBinder

class FgService : Service() {

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
