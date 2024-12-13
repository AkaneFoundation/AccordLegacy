package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class CustomTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    val colors: IntArray = intArrayOf(Color.WHITE),
    val durationStart: Long = -1L,
    val durationEnd: Long = -1L,
    val contentHash: Int = 10721
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var gradient: LinearGradient? = null
    val localMatrix = Matrix()
    var currentProgress = 0f

    fun setProgress(
        percent: Float,
        invalidate: Boolean = true
    ) {
        currentProgress = percent
        localMatrix.setTranslate(percent * width, height.toFloat())
        gradient?.setLocalMatrix(localMatrix)
        if (invalidate) invalidate()
    }

    fun setDefaultGradient() = updateGradient(colors)

    fun updateGradient(colors: IntArray) {
        gradient = LinearGradient(
            -width / 1f,
            0f,
            0f,
            0f,
            colors,
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        paint.setShader(gradient)
        super.onDraw(canvas)
        paint.setShader(null)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        setDefaultGradient()
    }
}