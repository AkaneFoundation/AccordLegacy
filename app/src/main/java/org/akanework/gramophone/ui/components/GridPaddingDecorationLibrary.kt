package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import androidx.fluidrecyclerview.widget.ConcatAdapter
import androidx.fluidrecyclerview.widget.RecyclerView
import org.akanework.gramophone.R

class GridPaddingDecorationLibrary(context: Context) : RecyclerView.ItemDecoration() {
    private var mPadding = context.resources.getDimensionPixelSize(R.dimen.grid_card_side_padding)
    private val columnSize = if (context.resources.configuration.orientation
        == Configuration.ORIENTATION_PORTRAIT
    ) 2 else 4

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        var itemPosition = parent.getChildAdapterPosition(view)
        itemPosition -= ((parent.adapter as ConcatAdapter).adapters[0].itemCount + (parent.adapter as ConcatAdapter).adapters[1].itemCount)
        if (itemPosition < 0) return
        (parent.adapter as ConcatAdapter).adapters[1]?.let {
            if (itemPosition % columnSize == 0) {
                outRect.left = mPadding
            } else if (itemPosition % columnSize - 1 == 0) {
                outRect.right = mPadding
            } else {
                return@getItemOffsets
            }
        }
    }
}