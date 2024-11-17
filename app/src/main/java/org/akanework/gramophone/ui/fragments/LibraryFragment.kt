package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.applyGeneralMenuItem
import org.akanework.gramophone.logic.data.db.entity.PlaylistWithMediaItem
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.adapters.HeaderAdapter
import org.akanework.gramophone.ui.adapters.LibraryCategoryAdapter
import org.akanework.gramophone.ui.adapters.LibraryHomeAdapter
import org.akanework.gramophone.ui.components.GridPaddingDecorationLibrary

class LibraryFragment : BaseFragment(null), Observer<List<PlaylistWithMediaItem>> {
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var libraryCategoryAdapter: LibraryCategoryAdapter
    private lateinit var libraryHomeAdapter: LibraryHomeAdapter
    private lateinit var libraryConcatAdapter: ConcatAdapter
    private lateinit var recentlyAddedHeaderAdapter: HeaderAdapter
    private var isOccupied = false

    data class PlaceHolder(
        override val id: Long? = null,
        override val title: String? = null,
        override val artist: String? = null,
        override var artistId: Long? = null,
        override val albumYear: Int? = null,
        override var cover: Uri? = null,
        override val songList: MutableList<androidx.media3.common.MediaItem> = mutableListOf()
    ) : MediaStoreUtils.Album

    data class AlbumLibrary(
        override val id: Long?,
        override val title: String?,
        override val artist: String?,
        override var artistId: Long?,
        override val albumYear: Int?,
        override var cover: Uri?,
        override val songList: MutableList<androidx.media3.common.MediaItem>
    ) : MediaStoreUtils.Album

    @SuppressLint("StringFormatInvalid", "StringFormatMatches")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = layoutInflater.inflate(R.layout.fragment_library, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val recyclerView = rootView.findViewById<RecyclerView>(R.id.recyclerview)

        val spans = if (requireContext().resources.configuration.orientation
            == Configuration.ORIENTATION_PORTRAIT
        ) 2 else 4

        libraryCategoryAdapter = LibraryCategoryAdapter(requireContext(), this)
        libraryHomeAdapter = LibraryHomeAdapter(this, requireContext())
        recentlyAddedHeaderAdapter = HeaderAdapter(R.layout.recently_added)
        libraryConcatAdapter = ConcatAdapter(libraryCategoryAdapter, recentlyAddedHeaderAdapter, libraryHomeAdapter)

        appBarLayout = rootView.findViewById(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        topAppBar.overflowIcon = AppCompatResources.getDrawable(
            requireContext(), R.drawable.ic_more_vert_bold
        )!!.apply {
            setTint(
                resources.getColor(R.color.contrast_themeColor, null)
            )
        }

        recyclerView.adapter = libraryConcatAdapter
        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.layoutManager = GridLayoutManager(context, spans).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    // BaseDecorAdapter always is full width
                    return if (position < libraryCategoryAdapter.itemCount + 1) spans else 1
                }
            }
        }

        recyclerView.addItemDecoration(GridPaddingDecorationLibrary(requireContext()))

        libraryViewModel.privatePlaylistList.observeForever(this)

        topAppBar.applyGeneralMenuItem(this, libraryViewModel)

        return rootView
    }

    override fun onDestroyView() {
        libraryViewModel.privatePlaylistList.removeObserver(this)
        super.onDestroyView()
    }

    private fun updateInternalAlbumList() {
        if (isOccupied) return
        isOccupied = true
        libraryViewModel.privateAlbumList.clear()
        libraryViewModel.privateAlbumList.add(PlaceHolder())

        CoroutineScope(Dispatchers.IO).launch {
            val mediaItemMap = libraryViewModel.mediaItemList.value?.associateBy { it.mediaId.toLong() }
            val albumItemMap = libraryViewModel.albumItemList.value?.associateBy { it.title }

            libraryViewModel.privatePlaylistList.value
                ?.flatMap { it.mediaItems }
                ?.mapNotNull { id ->
                    mediaItemMap?.get(id.mediaItemId)
                }
                ?.forEach { song ->
                    val targetAlbum = libraryViewModel.privateAlbumList.find { it.title == song.mediaMetadata.albumTitle }
                    if (targetAlbum != null) {
                        targetAlbum.songList.add(song)
                    } else {
                        val originalAlbumMetadataImpl = albumItemMap?.get(song.mediaMetadata.albumTitle)
                        originalAlbumMetadataImpl?.let {
                            if (it.songList.any { it.mediaId == song.mediaId }) {
                                libraryViewModel.privateAlbumList.add(
                                    AlbumLibrary(
                                        id = it.id,
                                        albumYear = it.albumYear,
                                        artist = it.artist,
                                        artistId = it.artistId,
                                        cover = it.cover ?: song.mediaMetadata.artworkUri,
                                        title = it.title,
                                        songList = mutableListOf(song)
                                    )
                                )
                            }
                        }
                    }
                }

            if (this@LibraryFragment::libraryConcatAdapter.isInitialized) {
                withContext(Dispatchers.Main) {
                    (libraryConcatAdapter.adapters[2] as LibraryHomeAdapter).updateList(
                        libraryViewModel.privateAlbumList
                    )
                }
            }
            isOccupied = false
        }
    }

    override fun onChanged(value: List<PlaylistWithMediaItem>) {
        updateInternalAlbumList()
    }
}