package com.manus.floatingbrowser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object DownloadNotificationDiagnostics {
    const val CHANNEL_ID = "browser_download_live_updates_v3"
    const val PROGRESS_NOTIFICATION_ID = 4101
    private const val TEST_NOTIFICATION_ID = 4198
    private const val PREFERENCES_NAME = "browser_preferences"
    private const val KEY_LAST_STATUS = "download_notification_last_status"

    fun record(context: Context, message: String) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_STATUS, "${System.currentTimeMillis()}|$message")
            .apply()
    }

    fun latest(context: Context): String {
        val raw = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_STATUS, null)
            ?: return "尚未执行下载通知测试"
        return raw.substringAfter('|', raw)
    }

    fun canPost(context: Context): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "下载实时进度（诊断）",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用户发起下载时显示真实进度；v2.15 用于诊断通知可见性。"
            }
        )
    }

    fun postDownloadProbe(context: Context, downloadId: Long): Boolean {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            record(context, "通知探针未发送：POST_NOTIFICATIONS 未授予")
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            record(context, "通知探针未发送：系统已关闭本应用的通知")
            return false
        }
        return try {
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationManagerCompat.from(context).notify(
                PROGRESS_NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("下载实时通知已启动")
                    .setContentText("正在连接下载任务 #$downloadId")
                    .setSubText("浮悬浏览器 · 下载实时进度")
                    .setContentIntent(contentIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setRequestPromotedOngoing(true)
                    .setProgress(100, 0, true)
                    .build()
            )
            record(context, "探针通知已直接发布；正在启动前台进度服务")
            true
        } catch (error: Exception) {
            record(context, "探针通知发布失败：${error.javaClass.simpleName}: ${error.message ?: "未知错误"}")
            false
        }
    }

    fun postTest(context: Context): Boolean {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            record(context, "测试通知未发送：POST_NOTIFICATIONS 未授予")
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            record(context, "测试通知未发送：系统已关闭本应用的通知")
            return false
        }
        return try {
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationManagerCompat.from(context).notify(
                TEST_NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("下载实时通知测试")
                    .setContentText("若此通知可见，应用通知渠道可用。")
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
            )
            record(context, "测试通知已直接发布；请检查通知栏")
            true
        } catch (error: Exception) {
            record(context, "测试通知发布失败：${error.javaClass.simpleName}: ${error.message ?: "未知错误"}")
            false
        }
    }

    fun cancelProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
    }
}
