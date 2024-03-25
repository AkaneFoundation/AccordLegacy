package org.akanework.gramophone.ui.components

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.blurview.BlurView
import org.akanework.gramophone.ui.components.blurview.RenderEffectBlur
import org.akanework.gramophone.ui.components.blurview.RenderScriptBlur
import java.io.FileNotFoundException
import java.io.InputStream

class BlendBackgroundView(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private val imageViewTS: ImageView
    private val imageViewTE: ImageView
    private val imageViewBS: ImageView
    private val imageViewBE: ImageView
    private val imageViewC: ImageView
    private val imageViewBG: ImageView
    private val rotateFrame: ConstraintLayout
    private val blurView: BlurView

    private val objectAnimatorList: MutableList<ObjectAnimator> = mutableListOf()

    init {
        inflate(context, R.layout.blend_background, this)
        imageViewTS = findViewById(R.id.type1)
        imageViewTE = findViewById(R.id.type2)
        imageViewBE = findViewById(R.id.type3)
        imageViewBS = findViewById(R.id.type4)
        imageViewC = findViewById(R.id.type5)
        imageViewBG = findViewById(R.id.bg)
        rotateFrame = findViewById(R.id.rotate_frame)
        blurView = findViewById(R.id.blur_view)
        initializeRotationAnimation(imageViewTS, 0f, 360f, 64000)
        initializeRotationAnimation(imageViewTE, 40f, 400f, 60000)
        initializeRotationAnimation(imageViewBS, 120f, 480f, 68000)
        initializeRotationAnimation(imageViewBE, 80f, 440f, 70000)
        initializeRotationAnimation(imageViewC, 360f, 0f, 107000)
        initializeRotationAnimation(rotateFrame, 360f, 0f, 107000)
        setUpBlurView(blurView, this, 160f)
    }

    fun setImageUri(uri: Uri) {
        val originalBitmap = getBitmapFromUri(context.contentResolver, uri)
        if (originalBitmap != null) {
            enhanceSaturation(originalBitmap, 3f).let {
                imageViewTS.setImageBitmap(cropTopLeftQuarter(it))
                imageViewTS.scaleType = ImageView.ScaleType.CENTER_CROP
                imageViewTE.setImageBitmap(cropTopRightQuarter(it))
                imageViewTE.scaleType = ImageView.ScaleType.CENTER_CROP
                imageViewBS.setImageBitmap(cropBottomLeftQuarter(it))
                imageViewBS.scaleType = ImageView.ScaleType.CENTER_CROP
                imageViewBE.setImageBitmap(cropBottomRightQuarter(it))
                imageViewBE.scaleType = ImageView.ScaleType.CENTER_CROP
                imageViewC.setImageBitmap(cropCenterHalf(it))
                imageViewC.scaleType = ImageView.ScaleType.CENTER_CROP
                imageViewBG.setImageBitmap(it)
                imageViewBG.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
    }

    fun animateBlurRadius(enlarge: Boolean, duration: Long) {
        val fromVal = if (enlarge) 80f else 160f
        val toVal = if (enlarge) 160f else 80f
        val animator = ValueAnimator.ofFloat(fromVal, toVal)
        animator.apply {
            addUpdateListener {
                val radius = it.animatedValue as Float
                blurView.setBlurRadius(radius)
            }
            this.duration = duration
        }
        animator.start()
    }

    private fun initializeRotationAnimation(
        view: View,
        fromDegrees: Float,
        toDegrees: Float,
        duration: Long
    ) {
        val objectAnimator = ObjectAnimator.ofFloat(view, "rotation", fromDegrees, toDegrees)
        objectAnimator.duration = duration
        objectAnimator.repeatCount = Animation.INFINITE
        objectAnimator.interpolator = LinearInterpolator()
        objectAnimator.start()
        objectAnimator.pause()
        objectAnimatorList.add(objectAnimator)
    }

    fun startRotationAnimation() {
        objectAnimatorList.forEach {
            if (it.isStarted) {
                it.resume()
            } else {
                it.start()
            }
        }
    }

    fun stopRotationAnimation() {
        objectAnimatorList.forEach {
            it.pause()
        }
    }

    private fun getBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        try {
            inputStream = contentResolver.openInputStream(uri)

            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
            }

            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            return bitmap
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } finally {
            inputStream?.close()
        }
        return null
    }

    private fun enhanceSaturation(bitmap: Bitmap, saturationFactor: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        enhancedBitmap.density = bitmap.density

        val paint = Paint()

        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(saturationFactor)

        paint.colorFilter = ColorMatrixColorFilter(saturationMatrix)

        val canvas = Canvas(enhancedBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return enhancedBitmap
    }

    private fun setUpBlurView(
        blurView: BlurView,
        rootView: ViewGroup,
        blurRadius: Float) {
        blurView.setupWith(
            rootView,
            if (Build.VERSION.SDK_INT >= 31)
                RenderEffectBlur()
            else
                RenderScriptBlur(context)
        )
            .setBlurRadius(blurRadius)
    }

    private fun cropCenterHalf(bitmap: Bitmap): Bitmap {
        val width = bitmap.width / 2
        val height = bitmap.height / 2

        val x = (bitmap.width - width) / 2
        val y = (bitmap.height - height) / 2

        val croppedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(croppedBitmap)
        val srcRect = Rect(x, y, x + width, y + height)
        val destRect = Rect(0, 0, width, height)
        canvas.drawBitmap(bitmap, srcRect, destRect, null)

        return croppedBitmap
    }

    private fun cropTopLeftQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        val croppedBitmap = Bitmap.createBitmap(quarterWidth, quarterHeight, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(croppedBitmap)
        val srcRect = Rect(0, 0, quarterWidth, quarterHeight)
        val destRect = Rect(0, 0, quarterWidth, quarterHeight)
        canvas.drawBitmap(bitmap, srcRect, destRect, null)

        return croppedBitmap
    }

    private fun cropTopRightQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        val croppedBitmap = Bitmap.createBitmap(quarterWidth, quarterHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(croppedBitmap)
        val srcRect = Rect(quarterWidth, 0, bitmap.width, quarterHeight)
        val destRect = Rect(0, 0, quarterWidth, quarterHeight)
        canvas.drawBitmap(bitmap, srcRect, destRect, null)
        return croppedBitmap
    }

    private fun cropBottomLeftQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        val croppedBitmap = Bitmap.createBitmap(quarterWidth, quarterHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(croppedBitmap)
        val srcRect = Rect(0, quarterHeight, quarterWidth, bitmap.height)
        val destRect = Rect(0, 0, quarterWidth, quarterHeight)
        canvas.drawBitmap(bitmap, srcRect, destRect, null)
        return croppedBitmap
    }

    private fun cropBottomRightQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        val croppedBitmap = Bitmap.createBitmap(quarterWidth, quarterHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(croppedBitmap)
        val srcRect = Rect(quarterWidth, quarterHeight, bitmap.width, bitmap.height)
        val destRect = Rect(0, 0, quarterWidth, quarterHeight)
        canvas.drawBitmap(bitmap, srcRect, destRect, null)
        return croppedBitmap
    }

}