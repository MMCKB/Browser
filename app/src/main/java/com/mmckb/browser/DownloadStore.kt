package com.mmckb.browser

import android.content.Context
import android.content.SharedPreferences

object DownloadStore {
    private const val PREFS_NAME = "download_store"
    private const val KEY_IDS = "download_ids"
    private const val KEY_DISPLAY_NAMES = "download_display_names"

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
        val names = displayNames(context).toMutableMap()
        names.remove(id.toString())
        prefs(context).edit()
            .putStringSet(KEY_IDS, ids.map { it.toString() }.toSet())
            .putStringSet(KEY_DISPLAY_NAMES, names.map { "${it.key}\t${it.value}" }.toSet())
            .apply()
    }

    fun ids(context: Context): List<Long> {
        val raw = prefs(context).getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.sorted()
    }

    fun displayName(context: Context, id: Long): String? = displayNames(context)[id.toString()]

    fun setDisplayName(context: Context, id: Long, name: String) {
        val names = displayNames(context).toMutableMap()
        names[id.toString()] = name
        prefs(context).edit()
            .putStringSet(KEY_DISPLAY_NAMES, names.map { "${it.key}\t${it.value}" }.toSet())
            .apply()
    }

    private fun displayNames(context: Context): Map<String, String> =
        (prefs(context).getStringSet(KEY_DISPLAY_NAMES, emptySet()) ?: emptySet())
            .mapNotNull { entry -> entry.substringBefore('\t', "").takeIf { it.isNotBlank() }?.let { key -> key to entry.substringAfter('\t', "") } }
            .toMap()
}
