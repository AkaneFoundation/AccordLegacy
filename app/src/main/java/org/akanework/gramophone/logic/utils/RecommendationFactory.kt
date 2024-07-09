package org.akanework.gramophone.logic.utils

import android.content.Context
import androidx.media3.common.MediaItem
import org.akanework.gramophone.ui.LibraryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecommendationFactory(
    context: Context,
    private val libraryViewModel: LibraryViewModel
) {

    enum class RecommendationType {
        HISTORY,
        FAVORITE,
        RECENTLY_ADDED,
        GENRE,
        ARTIST,
        NONE
    }

    private val sharedPreferences = context.getSharedPreferences("recommendation", Context.MODE_PRIVATE)

    private fun getCurrentDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun isArtistTypeAvailable(): Boolean {
        return libraryViewModel.artistItemList.value?.any { it.songList.size >= 4 && it.title != null } == true
    }

    private fun isGenreTypeAvailable(): Boolean {
        return libraryViewModel.genreItemList.value?.any { it.songList.size >= 4 && it.title != null } == true
    }

    interface RecommendFetcher {
        fun getRecommendation(): Pair<Int, List<Long>>
    }

    inner class GenreFetcher: RecommendFetcher {
        override fun getRecommendation(): Pair<Int, List<Long>> {
            val genreList = libraryViewModel.genreItemList.value ?: return Pair(0, emptyList())
            val index = genreList.indices.random()
            val genre = genreList[index]
            val genreIndexList = genre.songList.shuffled().take(4).map { it.mediaId.toLong() }
            return Pair(index, genreIndexList)
        }
    }

    inner class ArtistFetcher: RecommendFetcher {
        override fun getRecommendation(): Pair<Int, List<Long>> {
            val artistList = libraryViewModel.artistItemList.value ?: return Pair(0, emptyList())
            val index = artistList.indices.random()
            val artist = artistList[index]
            val artistIndexList = artist.songList.shuffled().take(4).map { it.mediaId.toLong() }
            return Pair(index, artistIndexList)
        }
    }

    interface TitleFetcher {
        fun getTitle(): String
    }

    class GenreTitleFetcher(
        private val recommendList: RecommendList,
        private val libraryViewModel: LibraryViewModel
    ): TitleFetcher {
        override fun getTitle(): String =
            libraryViewModel.genreItemList.value!![recommendList.recommendationObjectId].title!!
    }

    class ArtistTitleFetcher(
        private val recommendList: RecommendList,
        private val libraryViewModel: LibraryViewModel
    ): TitleFetcher {
        override fun getTitle(): String =
            libraryViewModel.artistItemList.value!![recommendList.recommendationObjectId].title!!
    }

    data class RawRecommendList (
        val recommendationType: RecommendationType,
        val recommendationObjectId: Int,
        val recommendationList: List<Long>
    )

    data class RecommendList(
        val recommendationType: RecommendationType,
        val recommendationObjectId: Int,
        val recommendationList: List<MediaItem>
    ) {
        fun getTitle(libraryViewModel: LibraryViewModel) =
            when (recommendationType) {
                RecommendationType.ARTIST -> {
                    ArtistTitleFetcher(this, libraryViewModel)
                }
                RecommendationType.GENRE -> {
                    GenreTitleFetcher(this, libraryViewModel)
                }
                RecommendationType.NONE -> {
                    object : TitleFetcher {
                        override fun getTitle(): String = ""
                    }
                }
                else -> {
                    throw IllegalArgumentException("Invalid recommendation type")
                }
            }.getTitle()
    }

    private fun reInstanceRecommendList(rawRecommendList: RawRecommendList): RecommendList {
        val objectList = when (rawRecommendList.recommendationType) {
            RecommendationType.GENRE -> libraryViewModel.genreItemList.value
            RecommendationType.ARTIST -> libraryViewModel.artistItemList.value
            else -> null
        }?.getOrNull(rawRecommendList.recommendationObjectId)?.songList ?: emptyList()

        val finalMediaItemList = objectList.filter { it.mediaId.toLong() in rawRecommendList.recommendationList }

        return RecommendList(
            rawRecommendList.recommendationType,
            rawRecommendList.recommendationObjectId,
            finalMediaItemList
        )
    }

    fun fetchRecommendList(): RecommendList {
        val savedDate = sharedPreferences.getString("date", null)
        if (getCurrentDateString() == savedDate) {
            val recommendationList = sharedPreferences.getString("recommendation", null)?.split(",")?.map { it.toLong() } ?: emptyList()
            return reInstanceRecommendList(
                RawRecommendList(
                    RecommendationType.valueOf(sharedPreferences.getString("type", "NONE")!!),
                    sharedPreferences.getInt("id", 0),
                    recommendationList
                )
            )
        }

        val genreTypeAvailable = isGenreTypeAvailable()
        val artistTypeAvailable = isArtistTypeAvailable()
        if (!genreTypeAvailable && !artistTypeAvailable) {
            return RecommendList(
                RecommendationType.NONE,
                0,
                emptyList()
            )
        }

        val availableList = mutableListOf<RecommendationType>().apply {
            if (genreTypeAvailable) add(RecommendationType.GENRE)
            if (artistTypeAvailable) add(RecommendationType.ARTIST)
        }

        val recommendationType = availableList.random()
        val fetcher = when (recommendationType) {
            RecommendationType.GENRE -> GenreFetcher()
            RecommendationType.ARTIST -> ArtistFetcher()
            else -> null
        } ?: run {
            return RecommendList(
                RecommendationType.NONE,
                0,
                emptyList()
            )
        }
        val recommendationList = fetcher.getRecommendation()

        sharedPreferences.edit().apply {
            putString("date", getCurrentDateString())
            putString("type", recommendationType.name)
            putString("recommendation", recommendationList.second.joinToString(","))
            putInt("id", recommendationList.first)
            apply()
        }

        return reInstanceRecommendList(
            RawRecommendList(
                recommendationType,
                recommendationList.first,
                recommendationList.second
            )
        )
    }
}
