package com.manus.floatingbrowser

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class DownloadProgressService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadProgressService::class.java)
            intent.putExtra("download_id", downloadId)
            context.startService(intent)
        }
    }
}
