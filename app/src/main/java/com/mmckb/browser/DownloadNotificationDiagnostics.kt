package com.mmckb.browser

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object DownloadNotificationDiagnostics {
    const val CHANNEL_ID = "mmckb_browser_download_progress"
    const val PROGRESS_NOTIFICATION_ID = 4101
    const val TERMINAL_NOTIFICATION_ID = 4102

    private const val PREFS_NAME = "download_diagnostics"
    private const val KEY_LATEST = "latest_diagnostic"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(context: Context, message: String) {
        prefs(context).edit().putString(KEY_LATEST, message).apply()
    }

    fun latest(context: Context): String =
        prefs(context).getString(KEY_LATEST, "暂无诊断记录") ?: "暂无诊断记录"

    fun canPost(context: Context): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "下载实时进度",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "显示浏览器内置下载器的真实文件名和下载进度。"
            }
        )
    }

    @SuppressLint("MissingPermission") // canPost() 已同时检查运行时权限和应用级通知总开关。
    fun postDownloadProbe(context: Context, downloadId: Long): Boolean {
        createChannel(context)
        if (!canPost(context)) {
            record(context, "通知探针未发送：系统通知权限或应用通知开关未启用")
            return false
        }
        return try {
            NotificationManagerCompat.from(context).notify(
                PROGRESS_NOTIFICATION_ID,
                baseBuilder(context)
                    .setContentTitle("正在启动下载")
                    .setContentText("正在连接下载任务 #$downloadId")
                    .setSubText("浮悬浏览器 · 下载实时进度")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setProgress(100, 0, true)
                    .build()
            )
            record(context, "下载探针通知已发布，准备启动 dataSync 前台服务")
            true
        } catch (error: Exception) {
            record(context, "下载探针通知发布失败：${error.javaClass.simpleName}: ${error.message ?: "未知错误"}")
            false
        }
    }

    fun baseBuilder(context: Context): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setShowWhen(false)
            // Android 16+ 在系统允许时可将持续下载进度提升为实时动态通知。
            .setRequestPromotedOngoing(true)
    }

    fun cancelProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        NotificationManagerCompat.from(context).cancel(TERMINAL_NOTIFICATION_ID)
    }
}
