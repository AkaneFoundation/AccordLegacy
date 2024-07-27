package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.data.db.entity.PlaylistWithMediaItem
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.adapters.SongAdapter

class LibraryAlbumSubFragment : BaseFragment(), Observer<List<PlaylistWithMediaItem>>{
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_general_sub, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val collapsingToolbarLayout =
            rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()

        if (libraryViewModel.albumItemList.value == null) {
            // (still better than crashing, though)
            requireParentFragment().childFragmentManager.popBackStack()
            return null
        }

        val playlist = libraryViewModel.privatePlaylistList.value!!.find {
            it.playlist.name == "favourite"
        }!!

        val filteredAndSortedList = playlist.mediaItems.mapNotNull { id ->
            libraryViewModel.mediaItemList.value!!.find { it.mediaId.toLong() == id.mediaItemId }
        }

        // Show title text.
        collapsingToolbarLayout.title = ContextCompat.getString(requireContext(), R.string.category_songs)

        songAdapter =
            SongAdapter(
                this,
                songList = filteredAndSortedList,
                true,
                null,
                ownsView = true,
                isSubFragment = true
            )

        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.setAppBar(appBarLayout)
        recyclerView.adapter = songAdapter.concatAdapter

        // Build FastScroller.
        recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

        topAppBar.setNavigationOnClickListener {
            Log.d("TAG", "ok${requireParentFragment().childFragmentManager.fragments.size}")
            (requireParentFragment() as BaseWrapperFragment).childFragmentManager.popBackStack()
        }

        libraryViewModel.privatePlaylistList.observeForever(this)

        return rootView
    }

    override fun onChanged(value: List<PlaylistWithMediaItem>)  {
        val favouritePlaylist = value.find { it1 -> it1.playlist.name == "favourite" }
        favouritePlaylist?.let { it1 ->
            songAdapter.updateList(
                it1.mediaItems.mapNotNull { mediaItem ->
                    libraryViewModel.mediaItemList.value
                        ?.find { it2 -> it2.mediaId.toLong() == mediaItem.mediaItemId }
                },
                now = false,
                canDiff = true
            )
        }
    }

    override fun onDestroy() {
        libraryViewModel.privatePlaylistList.removeObserver(this)
        super.onDestroy()
    }

}