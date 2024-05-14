package org.akanework.gramophone.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.akanework.gramophone.logic.data.db.entity.PLAYLIST_TABLE_NAME
import org.akanework.gramophone.logic.data.db.entity.Playlist
import org.akanework.gramophone.logic.data.db.entity.PlaylistWithMediaItem

@Dao
interface PlaylistDao {
    /**
     * Add a new playlist.
     */
    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addPlaylist(playlist: Playlist)

    /**
     * Delete playlist.
     */
    @Transaction
    @Delete
    fun removePlaylist(playlist: Playlist): Int

    /**
     * Delete playlist with playlist ID.
     */
    @Transaction
    @Query("DELETE FROM `$PLAYLIST_TABLE_NAME` WHERE ${Playlist.PLAYLIST_ID_COLUMN} = :playlistId")
    fun removePlaylistById(playlistId: Long): Int

    /**
     * Get all playlist and their media items.
     */
    @Transaction
    @Query("SELECT * FROM `$PLAYLIST_TABLE_NAME`")
    fun getAllPlaylists(): List<PlaylistWithMediaItem>

    /**
     * Get playlist and its song with given playlist ID.
     */
    @Transaction
    @Query("SELECT * FROM `$PLAYLIST_TABLE_NAME` WHERE `${Playlist.PLAYLIST_ID_COLUMN}` = :playlistId")
    fun getPlaylistWithMediaItems(playlistId: Long): PlaylistWithMediaItem
}