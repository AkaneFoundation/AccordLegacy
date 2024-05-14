package org.akanework.gramophone.logic.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * A [playlist] contains many [mediaItems].
 */
data class PlaylistWithMediaItem(
    @Embedded
    val playlist: Playlist,
    @Relation(
        parentColumn = Playlist.PLAYLIST_ID_COLUMN,
        entityColumn = MediaItem.MEDIA_ITEM_ID_COLUMN,
        associateBy = Junction(PlaylistMediaItemCrossRef::class)
    )
    val mediaItems: MutableList<MediaItem>
)
