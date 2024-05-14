package org.akanework.gramophone.logic.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.data.db.AppDatabase
import org.akanework.gramophone.logic.data.db.entity.MediaItem
import org.akanework.gramophone.logic.data.db.entity.Playlist
import org.akanework.gramophone.logic.data.db.entity.PlaylistWithMediaItem
import org.akanework.gramophone.ui.LibraryViewModel

object DatabaseUtils {

    suspend fun getPrivatePlaylist(
        libraryViewModel: LibraryViewModel,
        context: Context,
    ) {
        try {
            val internalList: MutableList<PlaylistWithMediaItem>
            var favouritePlaylistId: Long = 0
            withContext(Dispatchers.IO) {
                val database = AppDatabase.getInstance(context)
                val playlistDao = database.playlistDao()
                internalList = playlistDao.getAllPlaylists().toMutableList()
                val favouritePlaylist = internalList.find { it.playlist.name == "favourite" }
                if (favouritePlaylist == null) {
                    val newPlaylist = Playlist(0, "favourite", null)
                    playlistDao.addPlaylist(newPlaylist)
                    internalList.add(
                        PlaylistWithMediaItem(
                            newPlaylist,
                            mutableListOf()
                        )
                    )
                    favouritePlaylistId = 0
                } else {
                    favouritePlaylistId = favouritePlaylist.playlist.playlistId
                }
                withContext(Dispatchers.Main) {
                    libraryViewModel.privatePlaylistList.value = internalList
                    libraryViewModel.privatePlaylistId = favouritePlaylistId
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseUtils", "Error getting private playlist", e)
        }
    }

    suspend fun addSongToPlaylist(
        mediaItemId: Long,
        playlistId: Long,
        libraryViewModel: LibraryViewModel,
        context: Context,
    ) {
        try {
            withContext(Dispatchers.IO) {
                val database = AppDatabase.getInstance(context)
                val mediaItemDao = database.mediaItemDao()
                mediaItemDao.addMediaItem(MediaItem(mediaItemId))
                mediaItemDao.addMediaItemToPlaylist(playlistId, mediaItemId)
            }
            withContext(Dispatchers.Main) {
                val targetPlaylist = libraryViewModel.privatePlaylistList.value?.find {
                    it.playlist.playlistId == playlistId
                }
                targetPlaylist?.let {
                    it.mediaItems.add(MediaItem(mediaItemId))
                    // Manually trigger LiveData update
                    libraryViewModel.privatePlaylistList.value = libraryViewModel.privatePlaylistList.value
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseUtils", "Error adding song to playlist", e)
        }
    }

    suspend fun removeFromPlaylist(
        mediaItemId: Long,
        playlistId: Long,
        libraryViewModel: LibraryViewModel,
        context: Context
    ) {
        try {
            withContext(Dispatchers.IO) {
                val database = AppDatabase.getInstance(context)
                val mediaItemDao = database.mediaItemDao()
                mediaItemDao.removeMediaItemFromPlaylist(playlistId, mediaItemId)
            }
            withContext(Dispatchers.Main) {
                val targetPlaylist = libraryViewModel.privatePlaylistList.value?.find {
                    it.playlist.playlistId == playlistId
                }
                targetPlaylist?.let {
                    it.mediaItems.removeIf { mediaItem -> mediaItem.mediaItemId == mediaItemId }
                    // Manually trigger LiveData update
                    libraryViewModel.privatePlaylistList.value = libraryViewModel.privatePlaylistList.value
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseUtils", "Error removing song from playlist", e)
        }
    }

    suspend fun favouriteSong(
        mediaItemId: Long,
        libraryViewModel: LibraryViewModel,
        context: Context
    ) {
        addSongToPlaylist(mediaItemId, libraryViewModel.privatePlaylistId, libraryViewModel, context)
    }

    suspend fun removeFavouriteSong(
        mediaItemId: Long,
        libraryViewModel: LibraryViewModel,
        context: Context
    ) {
        removeFromPlaylist(mediaItemId, libraryViewModel.privatePlaylistId, libraryViewModel, context)
    }

    fun checkIfFavourite(
        mediaItemId: Long,
        libraryViewModel: LibraryViewModel
    ) : Boolean {
        return isFavourite(mediaItemId, libraryViewModel)
    }

    fun isFavourite(
        mediaItemId: Long,
        libraryViewModel: LibraryViewModel
    ) : Boolean {
        return libraryViewModel.privatePlaylistList.value?.find {
            it.playlist.playlistId == libraryViewModel.privatePlaylistId
        }?.mediaItems?.any {
            it.mediaItemId == mediaItemId
        } ?: false
    }
}
