package com.manus.floatingbrowser

import android.content.Context
import android.content.SharedPreferences

object DownloadStore {
    private const val PREFS_NAME = "download_store"
    private const val KEY_IDS = "download_ids"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(context: Context, id: Long) {
        val ids = ids(context).toMutableSet()
        ids.add(id)
        prefs(context).edit().putStringSet(KEY_IDS, ids.map { it.toString() }.toSet()).apply()
    }

    fun remove(context: Context, id: Long) {
        val ids = ids(context).toMutableSet()
        ids.remove(id)
        prefs(context).edit().putStringSet(KEY_IDS, ids.map { it.toString() }.toSet()).apply()
    }

    fun ids(context: Context): List<Long> {
        val raw = prefs(context).getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.sorted()
    }
}
