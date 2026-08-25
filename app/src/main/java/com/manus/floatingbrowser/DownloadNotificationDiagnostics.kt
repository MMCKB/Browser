package com.manus.floatingbrowser

import android.content.Context
import android.content.SharedPreferences

object DownloadNotificationDiagnostics {
    private const val PREFS_NAME = "download_diagnostics"
    private const val KEY_LATEST = "latest_diagnostic"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(context: Context, message: String) {
        prefs(context).edit().putString(KEY_LATEST, message).apply()
    }

    fun latest(context: Context): String {
        return prefs(context).getString(KEY_LATEST, "暂无诊断记录")
            ?: "暂无诊断记录"
    }

    // 保留向后兼容（通知功能已移除，但代码引用仍在）
    fun postDownloadProbe(context: Context, downloadId: Long): Boolean = false
    fun cancelProgress(context: Context) {}
}
