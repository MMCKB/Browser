package com.mmckb.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class DownloadProgressService : Service() {
    companion object {
        private const val EXTRA_DOWNLOAD_ID = "download_id"

        fun start(context: Context, downloadId: Long) {
            DownloadNotificationDiagnostics.record(context, "请求启动 dataSync 前台服务，任务 #$downloadId")
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadProgressService::class.java).putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            )
        }
    }

    private lateinit var downloadManager: DownloadManager
    private val monitoredIds = linkedSetOf<Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundReady = false
    private var stopped = false
    private var lastSummary = ""
    private data class SpeedSample(val bytes: Long, val atMillis: Long)
    private val speedSamples = mutableMapOf<Long, SpeedSample>()

    private val pollDownloads = object : Runnable {
        override fun run() {
            publishActualDownloadProgress()
            if (!stopped) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        DownloadNotificationDiagnostics.createChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                DownloadNotificationDiagnostics.PROGRESS_NOTIFICATION_ID,
                DownloadNotificationDiagnostics.baseBuilder(this)
                    .setContentTitle("正在准备下载")
                    .setContentText("正在连接浏览器下载任务")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, 0, true)
                    .build(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
            foregroundReady = true
            DownloadNotificationDiagnostics.record(this, "dataSync 前台服务已启动")
        } catch (error: Exception) {
            DownloadNotificationDiagnostics.record(
                this,
                "dataSync 前台服务启动失败：${error.javaClass.simpleName}: ${error.message ?: "未知错误"}"
            )
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady) return START_NOT_STICKY
        val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        if (id >= 0L) monitoredIds.add(id)
        if (monitoredIds.isEmpty()) {
            stopProgressService()
            return START_NOT_STICKY
        }
        stopped = false
        handler.removeCallbacks(pollDownloads)
        handler.post(pollDownloads)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacks(pollDownloads)
        super.onDestroy()
    }

    private fun publishActualDownloadProgress() {
        var activeCount = 0
        var completedCount = 0
        var failedCount = 0
        var leadingName = "下载文件"
        var leadingDownloaded = 0L
        var leadingTotal: Long? = null
        var leadingSpeed: Long? = null
        val removeIds = mutableListOf<Long>()

        monitoredIds.forEach { id ->
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
            if (cursor == null) {
                removeIds += id
                return@forEach
            }
            cursor.use {
                if (!it.moveToFirst()) {
                    removeIds += id
                    return@use
                }
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val name = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty().ifBlank { "下载文件" }
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> {
                        activeCount += 1
                        leadingName = name
                        leadingDownloaded = downloaded
                        leadingTotal = total.takeIf { size -> size > 0L }
                        leadingSpeed = if (status == DownloadManager.STATUS_RUNNING) updateSpeed(id, downloaded) else null
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        speedSamples.remove(id)
                        completedCount += 1
                    }
                    DownloadManager.STATUS_FAILED -> {
                        speedSamples.remove(id)
                        failedCount += 1
                    }
                }
            }
        }
        removeIds.forEach {
            monitoredIds.remove(it)
            speedSamples.remove(it)
        }

        if (activeCount > 0) {
            val progress = leadingTotal?.let { total -> ((leadingDownloaded * 100L) / total).toInt().coerceIn(0, 100) }
            val speedText = formatSpeed(leadingSpeed)
            val detail = if (progress != null && leadingTotal != null) {
                "$progress% · ${formatBytes(leadingDownloaded)} / ${formatBytes(leadingTotal!!)} · $speedText"
            } else {
                "正在下载 · ${formatBytes(leadingDownloaded)} · $speedText"
            }
            val summary = "$leadingName|$detail|$activeCount"
            if (summary != lastSummary) {
                postProgressNotification(leadingName, detail, progress, activeCount)
                lastSummary = summary
            }
            return
        }

        val title = when {
            failedCount > 0 && completedCount > 0 -> "下载已结束：部分失败"
            failedCount > 0 -> "下载失败"
            completedCount > 0 -> "下载完成"
            else -> "下载任务已结束"
        }
        if (completedCount > 0 || failedCount > 0) {
            postTerminalNotification(title, completedCount, failedCount)
        }
        stopProgressService()
    }

    @SuppressLint("MissingPermission") // 方法开头使用 canPost() 检查 POST_NOTIFICATIONS 与总开关。
    private fun postProgressNotification(fileName: String, detail: String, progress: Int?, count: Int) {
        if (!DownloadNotificationDiagnostics.canPost(this)) {
            DownloadNotificationDiagnostics.record(this, "进度通知更新被阻止：通知权限不可用")
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(
                DownloadNotificationDiagnostics.PROGRESS_NOTIFICATION_ID,
                DownloadNotificationDiagnostics.baseBuilder(this)
                    .setContentTitle(if (count > 1) "$fileName · $count 个任务" else fileName)
                    .setContentText(detail)
                    .setSubText("浏览器内置下载器 · 实时进度")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, progress ?: 0, progress == null)
                    .build()
            )
            DownloadNotificationDiagnostics.record(this, "已发布真实下载进度：$fileName · $detail")
        } catch (error: Exception) {
            DownloadNotificationDiagnostics.record(this, "进度通知发布失败：${error.javaClass.simpleName}: ${error.message ?: "未知错误"}")
        }
    }

    @SuppressLint("MissingPermission") // 方法开头使用 canPost() 检查 POST_NOTIFICATIONS 与总开关。
    private fun postTerminalNotification(title: String, completed: Int, failed: Int) {
        if (!DownloadNotificationDiagnostics.canPost(this)) return
        val detail = when {
            completed > 0 && failed > 0 -> "$completed 个文件完成，$failed 个文件失败"
            completed > 0 -> "$completed 个文件已完成"
            else -> "$failed 个文件未完成"
        }
        NotificationManagerCompat.from(this).notify(
            DownloadNotificationDiagnostics.TERMINAL_NOTIFICATION_ID,
            DownloadNotificationDiagnostics.baseBuilder(this)
                .setContentTitle(title)
                .setContentText(detail)
                .setOngoing(false)
                .setAutoCancel(true)
                .setProgress(0, 0, false)
                .build()
        )
        DownloadNotificationDiagnostics.record(this, "$title：$detail")
    }

    private fun updateSpeed(id: Long, bytes: Long): Long? {
        val now = SystemClock.elapsedRealtime()
        val previous = speedSamples[id]
        speedSamples[id] = SpeedSample(bytes, now)
        if (previous == null || now <= previous.atMillis || bytes < previous.bytes) return null
        return ((bytes - previous.bytes) * 1_000L / (now - previous.atMillis)).coerceAtLeast(0L)
    }

    private fun formatSpeed(speed: Long?): String = speed?.let { "${formatBytes(it)}/s" } ?: "计算速度中"

    private fun stopProgressService() {
        stopped = true
        handler.removeCallbacks(pollDownloads)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
