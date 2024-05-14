package org.akanework.gramophone.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

const val PLAYLIST_TABLE_NAME = "playlistTable"

@Entity(tableName = PLAYLIST_TABLE_NAME)
data class Playlist(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = PLAYLIST_ID_COLUMN)
    val playlistId: Long = 0,
    @ColumnInfo(name = NAME_COLUMN)
    val name: String,
    @ColumnInfo(name = PLAYLIST_COVER_COLUMN)
    val playlistCover: String?,
) {
    companion object {
        const val PLAYLIST_ID_COLUMN = "playlistId"
        const val NAME_COLUMN = "name"
        const val PLAYLIST_COVER_COLUMN = "playlistCover"
    }
}