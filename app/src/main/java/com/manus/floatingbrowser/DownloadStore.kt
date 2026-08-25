package com.manus.floatingbrowser

import android.content.Context

object DownloadStore {
    private const val PREFERENCES_NAME = "browser_preferences"
    private const val KEY_DOWNLOAD_IDS = "download_ids"

    fun add(context: Context, id: Long) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val values = preferences.getStringSet(KEY_DOWNLOAD_IDS, emptySet()).orEmpty().toMutableSet()
        values.add(id.toString())
        preferences.edit().putStringSet(KEY_DOWNLOAD_IDS, values).apply()
    }

    fun ids(context: Context): LongArray {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_DOWNLOAD_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toLongArray()
    }

    fun remove(context: Context, id: Long) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val values = preferences.getStringSet(KEY_DOWNLOAD_IDS, emptySet()).orEmpty().toMutableSet()
        values.remove(id.toString())
        preferences.edit().putStringSet(KEY_DOWNLOAD_IDS, values).apply()
    }
}
