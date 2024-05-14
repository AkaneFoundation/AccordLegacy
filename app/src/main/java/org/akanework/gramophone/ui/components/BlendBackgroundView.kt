package org.akanework.gramophone.ui.components

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
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageSwitcher
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.math.ceil

class BlendBackgroundView(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int
) :
    ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private val imageViewTS: ImageSwitcher
    private val imageViewBE: ImageSwitcher
    private val imageViewBG: ImageSwitcher
    private val rotateFrame: ConstraintLayout

    private var isAnimationOngoing: Boolean = true

    private val handler = Handler(Looper.getMainLooper())

    private val overlayColor = ContextCompat.getColor(context, R.color.contrast_blendOverlayColor)

    private var previousBitmap: Bitmap? = null

    companion object {
        const val VIEW_TRANSIT_DURATION: Long = 500
        const val FULL_BLUR_RADIUS: Float = 80F
        const val SHALLOW_BLUR_RADIUS: Float = 60F
        const val UPDATE_RUNNABLE_INTERVAL: Long = 34
        const val CYCLE: Int = 360
        const val SATURATION_FACTOR: Float = 4F
        const val PICTURE_SIZE: Int = 100
    }

    init {
        inflate(context, R.layout.blend_background, this)
        imageViewTS = findViewById(R.id.type1)
        imageViewBE = findViewById(R.id.type3)
        imageViewBG = findViewById(R.id.bg)
        rotateFrame = findViewById(R.id.rotate_frame)

        imageViewTS.rotation = 0f
        imageViewBE.rotation = 120f
        rotateFrame.rotation = 360f

        val animationIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
        animationIn.duration = VIEW_TRANSIT_DURATION
        val animationOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
        animationOut.duration = VIEW_TRANSIT_DURATION

        val factoryList =
            listOf(imageViewTS, imageViewBE, imageViewBG)
        factoryList.forEach {
            it.setFactory {
                val imageView = ImageView(context)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                imageView.setLayerType(LAYER_TYPE_HARDWARE, null)
                imageView
            }
            it.inAnimation = animationIn
            it.outAnimation = animationOut
        }
        this.setRenderEffect(
            RenderEffect.createBlurEffect(FULL_BLUR_RADIUS, FULL_BLUR_RADIUS, Shader.TileMode.MIRROR)
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        canvas.drawColor(overlayColor)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        doOnLayout {
            val windowMetrics =
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
            val screenHeight = windowMetrics.bounds.height()
            val screenWidth = windowMetrics.bounds.width()

            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()

            val scaleX = screenWidth / viewWidth
            val scaleY = screenHeight / viewHeight

            val finalScale = ceil(scaleX.coerceAtLeast(scaleY))

            this.scaleX = finalScale
            this.scaleY = finalScale
        }
    }

    fun setImageUri(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val originalBitmap = getBitmapFromUri(context.contentResolver, uri)
            if (originalBitmap != null &&
                (previousBitmap == null || !areBitmapsSame(originalBitmap, previousBitmap!!))
            ) {
                enhanceSaturation(originalBitmap).let {
                    withContext(Dispatchers.Main) {
                        imageViewTS.setImageDrawable(cropTopLeftQuarter(it).toDrawable(resources))
                        imageViewBE.setImageDrawable(cropBottomRightQuarter(it).toDrawable(resources))
                        imageViewBG.setImageDrawable(it.toDrawable(resources))
                    }
                }
                previousBitmap = originalBitmap
            } else if (originalBitmap == null) {
                withContext(Dispatchers.Main) {
                    imageViewTS.setImageDrawable(null)
                    imageViewBE.setImageDrawable(null)
                    imageViewBG.setImageDrawable(null)
                }
                previousBitmap = null
            }
        }
    }

    fun animateBlurRadius(enlarge: Boolean, duration: Long) {
        val fromVal = if (enlarge) SHALLOW_BLUR_RADIUS else FULL_BLUR_RADIUS
        val toVal = if (enlarge) FULL_BLUR_RADIUS else SHALLOW_BLUR_RADIUS
        val animator = ValueAnimator.ofFloat(fromVal, toVal).apply {
            this.duration = duration
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                // Reuse RenderEffect and set it directly
                val renderEffect = RenderEffect.createBlurEffect(
                    radius,
                    radius,
                    Shader.TileMode.MIRROR
                )
                // Ensure this runs on the UI thread
                post {
                    this@BlendBackgroundView.setRenderEffect(renderEffect)
                }
            }
        }
        animator.start()
    }

    fun startRotationAnimation() {
        if (this.alpha > 0) {
            handler.removeCallbacks(runnable)
            isAnimationOngoing = true
            handler.postDelayed(runnable, UPDATE_RUNNABLE_INTERVAL)
        }
    }

    fun stopRotationAnimation() {
        handler.removeCallbacks(runnable)
        isAnimationOngoing = false
    }

    private fun getBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        try {
            inputStream = contentResolver.openInputStream(uri)

            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            inputStream = contentResolver.openInputStream(uri)
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

    private fun calculateInSampleSize(
        options: BitmapFactory.Options
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > PICTURE_SIZE || width > PICTURE_SIZE) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= PICTURE_SIZE && (halfWidth / inSampleSize) >= PICTURE_SIZE) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }


    private fun enhanceSaturation(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        enhancedBitmap.density = bitmap.density

        val paint = Paint()

        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(SATURATION_FACTOR)

        paint.colorFilter = ColorMatrixColorFilter(saturationMatrix)

        val canvas = Canvas(enhancedBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return enhancedBitmap
    }

    private fun cropTopLeftQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        val croppedBitmap =
            Bitmap.createBitmap(quarterWidth, quarterHeight, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(croppedBitmap)
        val srcRect = Rect(0, 0, quarterWidth, quarterHeight)
        val destRect = Rect(0, 0, quarterWidth, quarterHeight)
        canvas.drawBitmap(bitmap, srcRect, destRect, null)

        return croppedBitmap
    }

    private fun cropBottomRightQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        val croppedBitmap =
            Bitmap.createBitmap(quarterWidth, quarterHeight, Bitmap.Config.ARGB_8888)
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

    /*
     * 360 / 20 / fps
     * 360 / 25 / fps
     * 360 / 30 / fps
     * 360 / 35 / fps
     * 360 / 60 / fps
     */
    private val runnable = object : Runnable {
        override fun run() {
            imageViewTS.rotation = (imageViewTS.rotation + .6f) % CYCLE
            imageViewBE.rotation = (imageViewBE.rotation + .4f) % CYCLE
            rotateFrame.rotation = (rotateFrame.rotation - .2f) % CYCLE

            if (isAnimationOngoing) {
                handler.postDelayed(this, UPDATE_RUNNABLE_INTERVAL)
            }
        }
    }

}