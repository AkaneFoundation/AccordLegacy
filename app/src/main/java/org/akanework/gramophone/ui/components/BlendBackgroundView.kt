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
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageSwitcher
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toDrawable
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

    private val imageViewTS: ImageSwitcher
    private val imageViewTE: ImageSwitcher
    private val imageViewBS: ImageSwitcher
    private val imageViewBE: ImageSwitcher
    private val imageViewC: ImageSwitcher
    private val imageViewBG: ImageSwitcher
    private val rotateFrame: ConstraintLayout
    private val blurView: BlurView

    private val objectAnimatorList: MutableList<ObjectAnimator> = mutableListOf()

    private var previousBitmap: Bitmap? = null

    companion object {
        const val VIEW_TRANSIT_DURATION: Long = 500
    }

    init {
        inflate(context, R.layout.blend_background, this)
        imageViewTS = findViewById(R.id.type1)
        imageViewTE = findViewById(R.id.type2)
        imageViewBE = findViewById(R.id.type3)
        imageViewBS = findViewById(R.id.type4)
        imageViewC = findViewById(R.id.type5)
        imageViewBG = findViewById(R.id.bg)

        val animationIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
        animationIn.duration = VIEW_TRANSIT_DURATION
        val animationOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
        animationOut.duration = VIEW_TRANSIT_DURATION

        val factoryList = listOf(imageViewTS, imageViewTE, imageViewBE, imageViewBS, imageViewC, imageViewBG)
        factoryList.forEach {
            it.setFactory {
                val imageView = ImageView(context)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                imageView
            }
            it.inAnimation = animationIn
            it.outAnimation = animationOut
        }

        rotateFrame = findViewById(R.id.rotate_frame)
        blurView = findViewById(R.id.blur_view)
        initializeRotationAnimation(imageViewTS, 0f, 360f, 20000)
        initializeRotationAnimation(imageViewTE, 40f, 400f, 25000)
        initializeRotationAnimation(imageViewBS, 120f, 480f, 30000)
        initializeRotationAnimation(imageViewBE, 80f, 440f, 35000)
        initializeRotationAnimation(imageViewC, 360f, 0f, 60000)
        initializeRotationAnimation(rotateFrame, 360f, 0f, 60000)
        setUpBlurView(blurView, this, 160f)
    }

    fun setImageUri(uri: Uri) {
        val originalBitmap = getBitmapFromUri(context.contentResolver, uri)
        if (originalBitmap != null &&
            (previousBitmap == null || !areBitmapsSame(originalBitmap, previousBitmap!!))
            ) {
            enhanceSaturation(originalBitmap, 3f).let {
                imageViewTS.setImageDrawable(cropTopLeftQuarter(it).toDrawable(resources))
                imageViewTE.setImageDrawable(cropTopRightQuarter(it).toDrawable(resources))
                imageViewBS.setImageDrawable(cropBottomLeftQuarter(it).toDrawable(resources))
                imageViewBE.setImageDrawable(cropBottomRightQuarter(it).toDrawable(resources))
                imageViewC.setImageDrawable(cropCenterHalf(it).toDrawable(resources))
                imageViewBG.setImageDrawable(it.toDrawable(resources))
            }
            previousBitmap = originalBitmap
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
                inSampleSize = 4
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
                RenderScriptBlur(context),
            null
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

    private fun areBitmapsSame(bitmap1: Bitmap, bitmap2: Bitmap): Boolean {
        if (bitmap1.config != bitmap2.config) {
            return false
        }

        val cornerPixels1 = intArrayOf(
            bitmap1.getPixel(0, 0),
            bitmap1.getPixel(bitmap1.width - 1, 0),
            bitmap1.getPixel(0, bitmap1.height - 1),
            bitmap1.getPixel(bitmap1.width - 1, bitmap1.height - 1)
        )

        val cornerPixels2 = intArrayOf(
            bitmap2.getPixel(0, 0),
            bitmap2.getPixel(bitmap2.width - 1, 0),
            bitmap2.getPixel(0, bitmap2.height - 1),
            bitmap2.getPixel(bitmap2.width - 1, bitmap2.height - 1)
        )

        val centerPixel1 = bitmap1.getPixel(bitmap1.width / 2, bitmap1.height / 2)
        val centerPixel2 = bitmap2.getPixel(bitmap2.width / 2, bitmap2.height / 2)

        return cornerPixels1 contentEquals cornerPixels2 && centerPixel1 == centerPixel2
    }

}