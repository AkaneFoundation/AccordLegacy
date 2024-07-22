package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import org.akanework.gramophone.R
import kotlin.math.min

class FadingVerticalEdgeLayout : FrameLayout {
    private var fadeTop = false
    private var fadeBottom = false
    private var gradientSizeTop = 0
    private var gradientSizeBottom = 0
    private var gradientPaintTop: Paint? = null
    private var gradientPaintBottom: Paint? = null
    private var gradientRectTop: Rect? = null
    private var gradientRectBottom: Rect? = null
    private var gradientDirtyFlags = 0

    private val colorMatrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1.7f, 0f
        )
    )

    private val overlayColorFilter = ColorMatrixColorFilter(colorMatrix)

    constructor(context: Context?) : super(context!!) {
        init(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    ) {
        init(attrs)
    }

    private val overlayPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        colorFilter = overlayColorFilter
    }

    private fun init(attrs: AttributeSet?) {
        val defaultSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, DEFAULT_GRADIENT_SIZE_DP.toFloat(),
            resources.displayMetrics
        ).toInt()
        if (attrs != null) {
            val arr =
                context.obtainStyledAttributes(attrs, R.styleable.FadingVerticalEdgeLayout, 0, 0)
            val flags = arr.getInt(R.styleable.FadingVerticalEdgeLayout_fel_edge, 0)
            fadeTop = flags and FADE_EDGE_TOP == FADE_EDGE_TOP
            fadeBottom = flags and FADE_EDGE_BOTTOM == FADE_EDGE_BOTTOM
            gradientSizeTop =
                arr.getDimensionPixelSize(R.styleable.FadingVerticalEdgeLayout_fel_size_top, defaultSize)
            gradientSizeBottom =
                arr.getDimensionPixelSize(R.styleable.FadingVerticalEdgeLayout_fel_size_bottom, defaultSize)
            if (fadeTop && gradientSizeTop > 0) {
                gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP
            }
            if (fadeBottom && gradientSizeBottom > 0) {
                gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM
            }
            arr.recycle()
        } else {
            gradientSizeBottom = defaultSize
            gradientSizeTop = gradientSizeBottom
        }
        val mode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        gradientPaintTop = Paint(Paint.ANTI_ALIAS_FLAG)
        gradientPaintTop!!.setXfermode(mode)
        gradientPaintBottom = Paint(Paint.ANTI_ALIAS_FLAG)
        gradientPaintBottom!!.setXfermode(mode)
        gradientRectTop = Rect()
        gradientRectBottom = Rect()
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (paddingTop != top) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP
        }
        if (paddingBottom != bottom) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM
        }
        super.setPadding(left, top, right, bottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        doOnLayout {
            updateGradientIfNecessary()
        }
    }

    private var previousPaddingBottom = 0
    private var previousPaddingTop = 0
    private var shouldDrawOverlayLayer: Boolean = false

    fun changeOverlayVisibility(visible: Boolean) {
        shouldDrawOverlayLayer = visible
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (previousPaddingBottom != paddingBottom || previousPaddingTop != paddingTop) {
            updateGradientIfNecessary()
            previousPaddingBottom = paddingBottom
            previousPaddingTop = paddingTop
        }
        if (shouldDrawOverlayLayer) {
            val count1 = canvas.saveLayer(null, overlayPaint)
            super.dispatchDraw(canvas)
            drawFade(canvas)
            canvas.restoreToCount(count1)
        }
        val count = canvas.saveLayer(null, null)
        super.dispatchDraw(canvas)
        drawFade(canvas)
        canvas.restoreToCount(count)
    }

    private fun updateGradientIfNecessary() {
        if (gradientDirtyFlags and DIRTY_FLAG_TOP == DIRTY_FLAG_TOP) {
            gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_TOP.inv()
            initTopGradient()
        }
        if (gradientDirtyFlags and DIRTY_FLAG_BOTTOM == DIRTY_FLAG_BOTTOM) {
            gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_BOTTOM.inv()
            initBottomGradient()
        }
    }

    private fun drawFade(canvas: Canvas) {
        if (fadeTop && gradientSizeTop > 0) {
            canvas.drawRect(gradientRectTop!!, gradientPaintTop!!)
        }
        if (fadeBottom && gradientSizeBottom > 0) {
            canvas.drawRect(gradientRectBottom!!, gradientPaintBottom!!)
        }
    }

    private fun initTopGradient() {
        val actualHeight = height - paddingTop - paddingBottom
        val size =
            min(gradientSizeTop.toDouble(), actualHeight.toDouble()).toInt()
        val l = getPaddingLeft()
        val t = paddingTop
        val r = width - getPaddingRight()
        val b = t + size
        gradientRectTop!![l, t, r] = b
        val gradient = LinearGradient(
            l.toFloat(),
            t.toFloat(),
            l.toFloat(),
            b.toFloat(),
            FADE_COLORS,
            null,
            Shader.TileMode.CLAMP
        )
        gradientPaintTop!!.setShader(gradient)
    }

    private fun initBottomGradient() {
        val actualHeight = height - paddingTop - paddingBottom
        val size =
            min(gradientSizeBottom.toDouble(), actualHeight.toDouble()).toInt()
        val l = getPaddingLeft()
        val t = paddingTop + actualHeight - size
        val r = width - getPaddingRight()
        val b = t + size
        gradientRectBottom!![l, t, r] = b
        val gradient = LinearGradient(
            l.toFloat(),
            t.toFloat(),
            l.toFloat(),
            b.toFloat(),
            FADE_COLORS_REVERSE,
            null,
            Shader.TileMode.CLAMP
        )
        gradientPaintBottom!!.setShader(gradient)
    }

    companion object {
        private const val DEFAULT_GRADIENT_SIZE_DP = 80
        const val FADE_EDGE_TOP = 1
        const val FADE_EDGE_BOTTOM = 2
        private const val DIRTY_FLAG_TOP = 1
        private const val DIRTY_FLAG_BOTTOM = 2
        private val FADE_COLORS = intArrayOf(Color.TRANSPARENT, Color.BLACK)
        private val FADE_COLORS_REVERSE = intArrayOf(Color.BLACK, Color.TRANSPARENT)
    }
}