package org.akanework.gramophone.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.akanework.gramophone.logic.data.db.entity.MEDIA_ITEM_TABLE_NAME
import org.akanework.gramophone.logic.data.db.entity.MediaItem
import org.akanework.gramophone.logic.data.db.entity.MediaItemWithPlaylist
import org.akanework.gramophone.logic.data.db.entity.PLAYLIST_MEDIA_ITEM_CROSS_REF_TABLE_NAME
import org.akanework.gramophone.logic.data.db.entity.PlaylistMediaItemCrossRef

@Dao
interface MediaItemDao {
    /**
     * Add song to database.
     */
    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addMediaItem(mediaItem: MediaItem)

    /**
     * Delete song.
     */
    @Transaction
    @Delete
    fun removeMediaItem(mediaItem: MediaItem): Int

    /**
     * Delete song with given media ID.
     */
    @Transaction
    @Query("DELETE FROM `$MEDIA_ITEM_TABLE_NAME` WHERE ${MediaItem.MEDIA_ITEM_ID_COLUMN} = :mediaItemId")
    fun removeMediaItemById(mediaItemId: Long): Int

    /**
     * Get song with given media ID.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM `$MEDIA_ITEM_TABLE_NAME` 
        WHERE ${MediaItem.MEDIA_ITEM_ID_COLUMN} = :mediaItemId
        """
    )
    fun getMediaItems(mediaItemId: Long): MediaItem


    /**
     * Get media item with given ID and playlists containing it.
     */
    @Transaction
    @Query("SELECT * FROM `$MEDIA_ITEM_TABLE_NAME` WHERE `${MediaItem.MEDIA_ITEM_ID_COLUMN}` = :mediaItemId")
    fun getMediaItemWithPlaylists(mediaItemId: Long): List<MediaItemWithPlaylist>

    /**
     * Add given song to playlist with given media ID.
     */
    @Transaction
    @Query(
        """
        INSERT OR REPLACE INTO `$PLAYLIST_MEDIA_ITEM_CROSS_REF_TABLE_NAME` 
        (`${PlaylistMediaItemCrossRef.PLAYLIST_ID_COLUMN}`,`${PlaylistMediaItemCrossRef.MEDIA_ITEM_ID_COLUMN}`) 
        VALUES (:playlistId, :mediaItemId)
        """
    )
    fun addMediaItemToPlaylist(playlistId: Long, mediaItemId: Long)

    /**
     * Remove this song from playlist with given media ID.
     */
    @Transaction
    @Query(
        """
        DELETE FROM `$PLAYLIST_MEDIA_ITEM_CROSS_REF_TABLE_NAME` 
        WHERE `${PlaylistMediaItemCrossRef.PLAYLIST_ID_COLUMN}` = :playlistId AND
        `${PlaylistMediaItemCrossRef.MEDIA_ITEM_ID_COLUMN}` = :mediaItemId
        """
    )
    fun removeMediaItemFromPlaylist(playlistId: Long, mediaItemId: Long)

    /**
     * Remove this song from all playlists with media ID.
     */
    @Transaction
    @Query(
        """
        DELETE FROM `$PLAYLIST_MEDIA_ITEM_CROSS_REF_TABLE_NAME` 
        WHERE `${PlaylistMediaItemCrossRef.MEDIA_ITEM_ID_COLUMN}` = :mediaItemId
        """
    )
    fun removeMediaItemFromPlaylist(mediaItemId: Long)
}