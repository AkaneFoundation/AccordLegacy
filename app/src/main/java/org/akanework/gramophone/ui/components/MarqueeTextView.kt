package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * [MarqueeTextView] is a kind of [AppCompatTextView]
 * keeps the focus of the textView all the time so marquee
 * can be displayed properly without needing to set focus
 * manually.
 *
 * Noteworthy, when the mainThread is doing something, marquee
 * will reload and cause a fake "jitter". Use this wisely, don't
 * make it everywhere.
 */
class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var isMarqueeEnabled = false

    init {
        // Set default properties
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        isFocusable = false // Disable focus when not scrolling
        isHorizontalFadingEdgeEnabled = false // Disable fading edge
        setLayerType(LAYER_TYPE_NONE, null) // Use software layer when not scrolling
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        post { checkMarquee() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { checkMarquee() }
    }

    private fun checkMarquee() {
        val textWidth = paint.measureText(text.toString())
        val viewWidth = width - paddingLeft - paddingRight
        val shouldBeMarquee = textWidth > viewWidth

        if (isMarqueeEnabled != shouldBeMarquee) {
            isMarqueeEnabled = shouldBeMarquee
            if (shouldBeMarquee) {
                setHorizontallyScrolling(true)
                isSelected = true
                isFocusable = true // Enable focus when scrolling
                isHorizontalFadingEdgeEnabled = true // Enable fading edge when scrolling
                setLayerType(LAYER_TYPE_SOFTWARE, null) // Use hardware layer when scrolling
            } else {
                setHorizontallyScrolling(false)
                isSelected = false
                isFocusable = false
                isHorizontalFadingEdgeEnabled = false
                setLayerType(LAYER_TYPE_NONE, null) // Use software layer when not scrolling
            }
        }
    }
}


