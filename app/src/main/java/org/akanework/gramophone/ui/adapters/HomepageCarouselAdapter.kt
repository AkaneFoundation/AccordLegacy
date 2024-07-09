package org.akanework.gramophone.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fluidrecyclerview.widget.RecyclerView
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.resourceUri
import org.akanework.gramophone.logic.utils.MediaStoreUtils

class HomepageCarouselAdapter(
    private val context: Context
) : RecyclerView.Adapter<HomepageCarouselAdapter.ViewHolder>() {

    val carouselList = mutableListOf(
        MediaStoreUtils.HomepageCarouselHolder(
            MediaStoreUtils.CarouselType.CUSTOM,
            cover = context.resourceUri(R.drawable.accord_mix_3),
            banner = context.resourceUri(R.drawable.accord_mix_3_banner),
            songList = mutableListOf(),
            hint = ContextCompat.getString(context, R.string.daily_shuffle)
        ),
        MediaStoreUtils.HomepageCarouselHolder(
            MediaStoreUtils.CarouselType.CUSTOM,
            cover = context.resourceUri(R.drawable.accord_mix_2),
            banner = context.resourceUri(R.drawable.accord_mix_2_banner),
            songList = mutableListOf(),
            hint = ContextCompat.getString(context, R.string.daily_shuffle)
        ),
        MediaStoreUtils.HomepageCarouselHolder(
            MediaStoreUtils.CarouselType.CUSTOM,
            cover = context.resourceUri(R.drawable.accord_mix_1),
            banner = context.resourceUri(R.drawable.accord_mix_1_banner),
            songList = mutableListOf(),
            hint = ContextCompat.getString(context, R.string.daily_shuffle)
        ),
        MediaStoreUtils.HomepageCarouselHolder(
            MediaStoreUtils.CarouselType.CUSTOM,
            cover = context.resourceUri(R.drawable.accord_mix_4),
            banner = context.resourceUri(R.drawable.accord_mix_4_banner),
            songList = mutableListOf(),
            hint = ContextCompat.getString(context, R.string.daily_shuffle)
        ),
    )

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val coverImageView: ImageView = view.findViewById(R.id.cover)
        val bannerImageView: ImageView = view.findViewById(R.id.banner)
        val hintTextView: TextView = view.findViewById(R.id.hint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.homepage_carousel, parent, false))

    override fun getItemCount(): Int = carouselList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.coverImageView.setImageURI(carouselList[position].cover)
        holder.bannerImageView.setImageURI(carouselList[position].banner)
        holder.hintTextView.text = carouselList[position].hint
    }
}