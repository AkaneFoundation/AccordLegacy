package org.akanework.gramophone.ui.adapters

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fluidrecyclerview.widget.DiffUtil
import androidx.fluidrecyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.findBaseWrapperFragment
import org.akanework.gramophone.logic.ui.CenteredImageSpan
import org.akanework.gramophone.logic.ui.coolCrossfade
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.fragments.BaseFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import org.akanework.gramophone.ui.fragments.LibraryFragment
import org.akanework.gramophone.ui.fragments.LibrarySongSubFragment

class LibraryHomeAdapter(
    private val fragment: BaseFragment,
    private val context: Context
) : RecyclerView.Adapter<LibraryHomeAdapter.ViewHolder>() {

    private val innerAlbumClass: MutableList<MediaStoreUtils.Album> = mutableListOf()

    private val defaultCover: Int = R.drawable.ic_default_cover

    private val imageSize = 16.dpToPx(context)

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.cover)
        val title: TextView = view.findViewById(R.id.title)
        val artist: TextView = view.findViewById(R.id.artist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.adapter_grid_card, parent, false)
        )
    }

    override fun getItemCount(): Int = innerAlbumClass.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (innerAlbumClass[position] is LibraryFragment.PlaceHolder) {
            Log.d("TAG", "Tagged special.")
            holder.cover.load(R.drawable.ic_default_cover_favourite)
            val text = context.getString(R.string.playlist_favourite) + " "
            val spannableString = SpannableString(text)

            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_star_filled)
            drawable?.setBounds(0, 0, imageSize, imageSize)

            drawable?.let {
                val imageSpan = CenteredImageSpan(it)
                spannableString.setSpan(imageSpan, text.length - 1, text.length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }

            holder.title.text = spannableString
            holder.artist.text = ""
            holder.itemView.setOnClickListener {
                fragment.findBaseWrapperFragment()?.replaceFragment(LibrarySongSubFragment())
            }
        } else {
            Log.d("TAG", "Normal item.")
            val album = innerAlbumClass[position]
            holder.cover.load(album.cover) {
                coolCrossfade(true)
                placeholder(defaultCover)
                error(defaultCover)
            }
            holder.title.text = album.title
            holder.artist.text = album.artist ?: context.getString(R.string.unknown_artist)
            holder.itemView.setOnClickListener {
                fragment.findBaseWrapperFragment()?.replaceFragment(GeneralSubFragment()) {
                    putInt("Position", position)
                    putInt("Item", R.id.special_album)
                }
            }
        }
    }

    fun updateList(newList: MutableList<MediaStoreUtils.Album>) {
        val diffResult = DiffUtil.calculateDiff(SongDiffCallback(innerAlbumClass, newList))
        innerAlbumClass.clear()
        innerAlbumClass.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    private class SongDiffCallback(
        private val oldList: MutableList<MediaStoreUtils.Album>,
        private val newList: MutableList<MediaStoreUtils.Album>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition].id == newList[newItemPosition].id

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition].songList == newList[newItemPosition].songList
    }
}
