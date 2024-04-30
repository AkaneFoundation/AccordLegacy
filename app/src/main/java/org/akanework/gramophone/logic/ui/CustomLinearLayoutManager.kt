package org.akanework.gramophone.logic.ui

import android.content.Context
import androidx.fluidrecyclerview.widget.LinearLayoutManager
import androidx.fluidrecyclerview.widget.RecyclerView
import org.akanework.gramophone.logic.dpToPx

class CustomLinearLayoutManager(private val context: Context) : LinearLayoutManager(context) {
    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        extraLayoutSpace[0] = 136.dpToPx(context)
        extraLayoutSpace[1] = 476.dpToPx(context)
    }
}