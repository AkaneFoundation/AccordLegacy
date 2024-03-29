package org.akanework.gramophone.logic.utils

import android.content.SharedPreferences
import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.ArtistAdapter
import org.akanework.gramophone.ui.adapters.BaseAdapter
import org.akanework.gramophone.ui.adapters.DateAdapter
import org.akanework.gramophone.ui.adapters.GenreAdapter
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter

object FileOpUtils {
    fun getAdapterType(adapter: BaseAdapter<*>) =
        when (adapter) {
            is AlbumAdapter -> {
                0
            }

            is ArtistAdapter -> {
                1
            }

            is DateAdapter -> {
                2
            }

            is GenreAdapter -> {
                3
            }

            is PlaylistAdapter -> {
                4
            }

            is SongAdapter -> {
                5
            }

            else -> {
                throw IllegalArgumentException()
            }
        }

    fun readHashMapFromSharedPreferences(
        sharedPreferences: SharedPreferences,
        key: String
    ): HashMap<String, Boolean> {
        val stringSet = sharedPreferences.getStringSet(key, HashSet()) ?: HashSet()
        val hashMap = HashMap<String, Boolean>()
        for (item in stringSet) {
            val keyValue = item.split(":")
            if (keyValue.size == 2) {
                hashMap[keyValue[0]] = keyValue[1].toBoolean()
            }
        }
        return hashMap
    }

    fun writeHashMapToSharedPreferences(
        sharedPreferences: SharedPreferences,
        key: String,
        hashMap: HashMap<String, Boolean>
    ) {
        val stringSet = HashSet<String>()
        for (entry in hashMap.entries) {
            stringSet.add("${entry.key}:${entry.value}")
        }
        sharedPreferences.edit().putStringSet(key, stringSet).apply()
    }
}