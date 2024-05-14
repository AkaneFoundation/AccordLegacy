package org.akanework.gramophone.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

const val MEDIA_ITEM_TABLE_NAME = "mediaItemTable"

@Entity(tableName = MEDIA_ITEM_TABLE_NAME)
data class MediaItem(
    @PrimaryKey
    @ColumnInfo(name = MEDIA_ITEM_ID_COLUMN)
    val mediaItemId: Long,
) {
    companion object {
        const val MEDIA_ITEM_ID_COLUMN = "mediaItemId"
    }
}