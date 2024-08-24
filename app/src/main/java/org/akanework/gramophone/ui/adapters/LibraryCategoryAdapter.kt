package org.akanework.gramophone.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.findBaseWrapperFragment
import org.akanework.gramophone.ui.fragments.BaseFragment
import org.akanework.gramophone.ui.fragments.LibrarySongSubFragment

class LibraryCategoryAdapter(
    private val context: Context,
    private val fragment: BaseFragment
) : RecyclerView.Adapter<LibraryCategoryAdapter.ViewHolder>() {

    data class LibraryEntry(
        val entryType: EntryType,
        val entryIcon: Int,
        val entryVal: String
    )

    enum class EntryType {
        PLAYLIST, ARTIST, ALBUM, SONG
    }

    private val libraryEntryList: List<LibraryEntry> = listOf(
        LibraryEntry(
            EntryType.PLAYLIST,
            R.drawable.ic_library_link_icon_playlists,
            ContextCompat.getString(context, R.string.category_playlists)
        ),
        LibraryEntry(
            EntryType.ARTIST,
            R.drawable.ic_library_link_icon_artists,
            ContextCompat.getString(context, R.string.category_artists)
        ),
        LibraryEntry(
            EntryType.ALBUM,
            R.drawable.ic_library_link_icon_album,
            ContextCompat.getString(context, R.string.category_albums)
        ),
        LibraryEntry(
            EntryType.SONG,
            R.drawable.ic_library_link_icon_songs,
            ContextCompat.getString(context, R.string.category_songs)
        )
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageview)
        val textView: TextView = view.findViewById(R.id.text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.library_entry, parent, false)
        )

    override fun getItemCount(): Int = 4

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = libraryEntryList[position].entryVal
        holder.imageView.setImageResource(libraryEntryList[position].entryIcon)
        holder.itemView.setOnClickListener {
            when (libraryEntryList[position].entryType) {
                EntryType.SONG -> {
                    fragment.findBaseWrapperFragment()!!
                        .replaceFragment(LibrarySongSubFragment())
                }
                EntryType.PLAYLIST -> {

                }
                EntryType.ARTIST -> {

                }
                EntryType.ALBUM -> {

                }
            }
        }
    }
}