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
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    companion object {
        val FADE_COLORS_REVERSE = intArrayOf(Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, 0x24FFFFFF)
    }

    private var gradient: LinearGradient? = null
    private val localMatrix = Matrix()

//    override fun onAttachedToWindow() {
//        super.onAttachedToWindow()
//        doOnLayout {
//            initGradient()
//        }
//    }

    fun setProgress(percent: Float) {
//        Log.d("Percent", "$percent")
        if (gradient == null) {
            gradient = LinearGradient(
                -width / 1f,
                0f,
                0f,
                0f,
                FADE_COLORS_REVERSE,
                null,
                Shader.TileMode.CLAMP
            )
        }
        localMatrix.setTranslate(percent * width, height.toFloat())
        gradient!!.setLocalMatrix(localMatrix)
        invalidate()
    }

//    private fun initGradient() {
//        gradient = LinearGradient(
//            -width / 1f,
//            0f,
//            0f,
//            0f,
//            FADE_COLORS_REVERSE,
//            null,
//            Shader.TileMode.CLAMP
//        )
//    }

    override fun onDraw(canvas: Canvas) {
//        val currentCanvas = canvas.saveLayer(null, null)
//        super.onDraw(canvas)
//        canvas.restoreToCount(currentCanvas)
        paint.setShader(gradient)
        super.onDraw(canvas)
        paint.setShader(null)
    }
}