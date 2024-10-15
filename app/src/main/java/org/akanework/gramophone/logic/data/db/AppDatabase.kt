package org.akanework.gramophone.logic.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.akanework.gramophone.logic.data.db.dao.MediaItemDao
import org.akanework.gramophone.logic.data.db.dao.PlaylistDao
import org.akanework.gramophone.logic.data.db.entity.MediaItem
import org.akanework.gramophone.logic.data.db.entity.Playlist
import org.akanework.gramophone.logic.data.db.entity.PlaylistMediaItemCrossRef

const val APP_DATABASE_FILE_NAME = "app.db"

@Database(
    entities = [
        Playlist::class,
        MediaItem::class,
        PlaylistMediaItemCrossRef::class,
    ],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun mediaItemDao(): MediaItemDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    APP_DATABASE_FILE_NAME
                )
                    .build()
                    .apply { instance = this }
            }
        }
    }
}
