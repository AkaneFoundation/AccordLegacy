package org.akanework.gramophone.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.coolCrossfade
import org.akanework.gramophone.ui.MainActivity

class RecommendAdapter(
    private val mainActivity: MainActivity
) : RecyclerView.Adapter<RecommendAdapter.ViewHolder>() {

    private val defaultCover: Int = R.drawable.ic_default_cover

    val mediaItemList: MutableList<MediaItem> = mutableListOf()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coverImageView: ImageView = view.findViewById(R.id.cover)
        val titleTextView: TextView = view.findViewById(R.id.title)
        val subtitleTextView: TextView = view.findViewById(R.id.subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.homepage_recommend_card, parent, false)
        )

    override fun getItemCount(): Int = mediaItemList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.coverImageView.load(mediaItemList[position].mediaMetadata.artworkUri) {
            coolCrossfade(true)
            placeholder(defaultCover)
            error(defaultCover)
        }
        holder.titleTextView.text = mediaItemList[position].mediaMetadata.title
        holder.subtitleTextView.text = mediaItemList[position].mediaMetadata.artist
        holder.itemView.setOnClickListener {
            val mediaController = mainActivity.getPlayer()
            mediaController?.apply {
                setMediaItems(mediaItemList, position, C.TIME_UNSET)
                prepare()
                play()
            }
        }
    }

    fun updateList(newList: MutableList<MediaItem>) {
        val diffResult = DiffUtil.calculateDiff(SongDiffCallback(mediaItemList, newList))
        mediaItemList.clear()
        mediaItemList.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    private class SongDiffCallback(
        private val oldList: MutableList<MediaItem>,
        private val newList: MutableList<MediaItem>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition].mediaId == newList[newItemPosition].mediaId

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition].mediaMetadata.title == newList[newItemPosition].mediaMetadata.title
    }
}