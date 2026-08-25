package com.manus.floatingbrowser

import android.app.DownloadManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class DownloadProgressService : Service() {
    companion object {
        private const val TERMINAL_NOTIFICATION_ID = 4102
        private const val EXTRA_DOWNLOAD_ID = "download_id"

        fun start(context: Context, downloadId: Long) {
            DownloadNotificationDiagnostics.record(context, "请求启动 dataSync 前台服务，任务 #$downloadId")
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadProgressService::class.java)
                    .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            )
        }
    }

    private lateinit var downloadManager: DownloadManager
    private val handler = Handler(Looper.getMainLooper())
    private val monitoredIds = linkedSetOf<Long>()
    private var lastSummary = ""
    private var foregroundReady = false
    private var isStopped = false
    private val monitor = object : Runnable {
        override fun run() {
            updateNotificationFromDownloads()
            if (!isStopped) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        DownloadNotificationDiagnostics.record(this, "dataSync 前台服务已进入 onCreate")
        try {
            DownloadNotificationDiagnostics.createChannel(this)
            ServiceCompat.startForeground(
                this,
                DownloadNotificationDiagnostics.PROGRESS_NOTIFICATION_ID,
                buildNotification("下载实时通知已启动", null, null, true, 1),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
            foregroundReady = true
            DownloadNotificationDiagnostics.record(this, "dataSync 前台服务已成功调用 startForeground")
        } catch (error: Exception) {
            DownloadNotificationDiagnostics.record(
                this,
                "dataSync 前台服务 startForeground 失败：${error.javaClass.simpleName}: ${error.message ?: "未知错误"}"
            )
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady) return START_NOT_STICKY
        val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        if (id >= 0L) {
            monitoredIds.add(id)
            DownloadNotificationDiagnostics.record(this, "dataSync 正在监控下载任务 #$id")
        }
        isStopped = false
        handler.removeCallbacks(monitor)
        handler.post(monitor)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isStopped = true
        handler.removeCallbacks(monitor)
        DownloadNotificationDiagnostics.record(this, "dataSync 前台服务已停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotificationFromDownloads() {
        if (monitoredIds.isEmpty()) {
            stopProgressService()
            return
        }
        var activeCount = 0
        var completedCount = 0
        var failedCount = 0
        var leadingTitle = "下载任务"
        var leadingDownloaded: Long? = null
        var leadingTotal: Long? = null
        val missingIds = mutableListOf<Long>()

        monitoredIds.forEach { id ->
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
            if (cursor == null) {
                missingIds += id
                return@forEach
            }
            cursor.use {
                if (!it.moveToFirst()) {
                    missingIds += id
                    return@use
                }
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty().ifBlank { "下载文件" }
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> {
                        activeCount += 1
                        leadingTitle = title
                        leadingDownloaded = downloaded
                        leadingTotal = total.takeIf { it > 0L }
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> completedCount += 1
                    DownloadManager.STATUS_FAILED -> failedCount += 1
                }
            }
        }
        missingIds.forEach(monitoredIds::remove)

        if (activeCount > 0) {
            val progress = leadingTotal?.let { total -> ((leadingDownloaded ?: 0L) * 100L / total).toInt().coerceIn(0, 100) }
            val summary = "$leadingTitle|$progress|$leadingDownloaded|$leadingTotal|$activeCount"
            if (summary != lastSummary) {
                postNotification(
                    DownloadNotificationDiagnostics.PROGRESS_NOTIFICATION_ID,
                    buildNotification(leadingTitle, progress, leadingDownloaded, true, activeCount, leadingTotal)
                )
                lastSummary = summary
            }
            return
        }

        val terminalTitle = when {
            failedCount > 0 && completedCount > 0 -> "下载已结束：部分任务失败"
            failedCount > 0 -> "下载失败"
            completedCount > 0 -> "下载完成"
            else -> "下载任务已结束"
        }
        postNotification(TERMINAL_NOTIFICATION_ID, buildNotification(terminalTitle, 100, null, false, completedCount + failedCount))
        stopProgressService()
    }

    private fun stopProgressService() {
        isStopped = true
        handler.removeCallbacks(monitor)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(
        title: String,
        progress: Int?,
        downloaded: Long?,
        ongoing: Boolean,
        count: Int,
        total: Long? = null
    ): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val description = when {
            ongoing && progress != null && downloaded != null && total != null ->
                "下载进度 $progress% · ${formatBytes(downloaded)} / ${formatBytes(total)}"
            ongoing -> "正在获取 DownloadManager 真实进度"
            title == "下载完成" -> "下载任务已完成，点击查看下载管理"
            else -> "点击返回浏览器查看下载管理"
        }
        return NotificationCompat.Builder(this, DownloadNotificationDiagnostics.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (count > 1) "$title · $count 个任务" else title)
            .setContentText(description)
            .setSubText(if (ongoing) "下载实时进度" else "浮悬浏览器")
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setRequestPromotedOngoing(ongoing)
            .setShortCriticalText(if (ongoing && progress != null) "$progress%" else null)
            .setProgress(100, progress ?: 0, ongoing && progress == null)
            .build()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun postNotification(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            DownloadNotificationDiagnostics.record(this, "通知更新被阻止：POST_NOTIFICATIONS 未授予")
            return
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            DownloadNotificationDiagnostics.record(this, "通知更新被阻止：系统已关闭本应用的通知")
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
            DownloadNotificationDiagnostics.record(this, "进度通知已发布到系统通知栏")
        } catch (error: Exception) {
            DownloadNotificationDiagnostics.record(
                this,
                "进度通知发布失败：${error.javaClass.simpleName}: ${error.message ?: "未知错误"}"
            )
        }
    }
}
