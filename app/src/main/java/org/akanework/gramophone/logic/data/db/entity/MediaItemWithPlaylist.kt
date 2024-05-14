package org.akanework.gramophone.logic.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * A [mediaItem] contains many [playlists].
 */
data class MediaItemWithPlaylist(
    @Embedded
    val mediaItem: MediaItem,
    @Relation(
        parentColumn = MediaItem.MEDIA_ITEM_ID_COLUMN,
        entityColumn = Playlist.PLAYLIST_ID_COLUMN,
        associateBy = Junction(PlaylistMediaItemCrossRef::class)
    )
    val playlists: List<Playlist>,
)
