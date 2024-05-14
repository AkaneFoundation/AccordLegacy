package org.akanework.gramophone.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

const val PLAYLIST_MEDIA_ITEM_CROSS_REF_TABLE_NAME = "playlistMediaItemCrossRef"

@Entity(
    tableName = PLAYLIST_MEDIA_ITEM_CROSS_REF_TABLE_NAME,
    primaryKeys = [
        PlaylistMediaItemCrossRef.PLAYLIST_ID_COLUMN,
        PlaylistMediaItemCrossRef.MEDIA_ITEM_ID_COLUMN,
    ],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = [Playlist.PLAYLIST_ID_COLUMN],
            childColumns = [PlaylistMediaItemCrossRef.PLAYLIST_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = [MediaItem.MEDIA_ITEM_ID_COLUMN],
            childColumns = [PlaylistMediaItemCrossRef.MEDIA_ITEM_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(PlaylistMediaItemCrossRef.MEDIA_ITEM_ID_COLUMN),
    ]
)
data class PlaylistMediaItemCrossRef(
    @ColumnInfo(name = PLAYLIST_ID_COLUMN)
    val playlistId: Long,
    @ColumnInfo(name = MEDIA_ITEM_ID_COLUMN)
    val mediaItemId: Long,
) {
    companion object {
        const val PLAYLIST_ID_COLUMN = "playlistId"
        const val MEDIA_ITEM_ID_COLUMN = "mediaItemId"
    }
}