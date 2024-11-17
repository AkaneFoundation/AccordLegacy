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

class BlendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val imageViewTS: ImageSwitcher
    private val imageViewBE: ImageSwitcher
    private val imageViewBG: ImageSwitcher
    private val rotateFrame: ConstraintLayout

    private var isAnimationOngoing: Boolean = true
    private val handler = Handler(Looper.getMainLooper())
    private val overlayColor = ContextCompat.getColor(context, R.color.contrast_blendOverlayColor)
    private var previousBitmap: Bitmap? = null

    companion object {
        const val VIEW_TRANSIT_DURATION: Long = 400
        const val FULL_BLUR_RADIUS: Float = 80F
        const val SHALLOW_BLUR_RADIUS: Float = 60F
        const val UPDATE_RUNNABLE_INTERVAL: Long = 34
        const val CYCLE: Int = 360
        const val SATURATION_FACTOR: Float = 2.5F
        const val PICTURE_SIZE: Int = 60
    }

    init {
        inflate(context, R.layout.blend_background, this)
        imageViewTS = findViewById(R.id.type1)
        imageViewBE = findViewById(R.id.type3)
        imageViewBG = findViewById(R.id.bg)
        rotateFrame = findViewById(R.id.rotate_frame)

        initializeImageSwitchers()

        this.setRenderEffect(
            RenderEffect.createBlurEffect(FULL_BLUR_RADIUS, FULL_BLUR_RADIUS, Shader.TileMode.MIRROR)
        )
    }

    private fun initializeImageSwitchers() {
        val animationIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in).apply {
            duration = VIEW_TRANSIT_DURATION
        }
        val animationOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out).apply {
            duration = VIEW_TRANSIT_DURATION
        }
        val factoryList = listOf(imageViewTS, imageViewBE, imageViewBG)

        factoryList.forEach { switcher ->
            switcher.setFactory {
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setLayerType(LAYER_TYPE_SOFTWARE, null)
                }
            }
            switcher.inAnimation = animationIn
            switcher.outAnimation = animationOut
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        canvas.drawColor(overlayColor)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        adjustViewScale()
    }

    private fun adjustViewScale() {
        doOnLayout {
            val windowMetrics = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
            val screenHeight = windowMetrics.bounds.height()
            val screenWidth = windowMetrics.bounds.width()

            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()

            val finalScale = ceil((screenWidth / viewWidth).coerceAtLeast(screenHeight / viewHeight))

            this.scaleX = finalScale
            this.scaleY = finalScale
        }
    }

    fun setImageUri(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val originalBitmap = getBitmapFromUri(context.contentResolver, uri)
            if (originalBitmap != null && !areBitmapsSame(originalBitmap, previousBitmap)) {
                enhanceBitmap(originalBitmap).let { enhancedBitmap ->
                    withContext(Dispatchers.Main) {
                        updateImageViews(enhancedBitmap)
                    }
                }
                previousBitmap = originalBitmap
            } else if (originalBitmap == null) {
                withContext(Dispatchers.Main) {
                    clearImageViews()
                }
                previousBitmap = null
            }
        }
    }

    private fun updateImageViews(bitmap: Bitmap) {
        imageViewTS.setImageDrawable(cropTopLeftQuarter(bitmap).toDrawable(resources))
        imageViewBE.setImageDrawable(cropBottomRightQuarter(bitmap).toDrawable(resources))
        imageViewBG.setImageDrawable(bitmap.toDrawable(resources))
    }

    private fun clearImageViews() {
        imageViewTS.setImageDrawable(null)
        imageViewBE.setImageDrawable(null)
        imageViewBG.setImageDrawable(null)
    }

    fun animateBlurRadius(enlarge: Boolean, duration: Long) {
        val fromVal = if (enlarge) SHALLOW_BLUR_RADIUS else FULL_BLUR_RADIUS
        val toVal = if (enlarge) FULL_BLUR_RADIUS else SHALLOW_BLUR_RADIUS
        ValueAnimator.ofFloat(fromVal, toVal).apply {
            this.duration = duration
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                val renderEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR)
                post { this@BlendView.setRenderEffect(renderEffect) }
            }
            start()
        }
    }

    fun startRotationAnimation() {
        if (this.alpha > 0) {
            handler.removeCallbacks(rotationRunnable)
            isAnimationOngoing = true
            handler.postDelayed(rotationRunnable, UPDATE_RUNNABLE_INTERVAL)
        }
    }

    fun stopRotationAnimation() {
        handler.removeCallbacks(rotationRunnable)
        isAnimationOngoing = false
    }


    private val rotationRunnable = object : Runnable {
        override fun run() {
            imageViewTS.rotation = (imageViewTS.rotation + 1.2f) % CYCLE
            imageViewBE.rotation = (imageViewBE.rotation + .67f) % CYCLE
            rotateFrame.rotation = (rotateFrame.rotation - .6f) % CYCLE
            if (isAnimationOngoing) {
                handler.postDelayed(this, UPDATE_RUNNABLE_INTERVAL)
            }
        }
    }

    private fun getBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            options.inSampleSize = calculateInSampleSize(options)
            options.inJustDecodeBounds = false
            inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            null
        } finally {
            inputStream?.close()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > PICTURE_SIZE || width > PICTURE_SIZE) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= PICTURE_SIZE && (halfWidth / inSampleSize) >= PICTURE_SIZE) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun enhanceBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        enhancedBitmap.density = bitmap.density

        val enhancePaint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(SATURATION_FACTOR) }
        enhancePaint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        val canvas = Canvas(enhancedBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, enhancePaint)

        return enhancedBitmap
    }


    private fun cropTopLeftQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        return Bitmap.createBitmap(bitmap, 0, 0, quarterWidth, quarterHeight)
    }

    private fun cropBottomRightQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        return Bitmap.createBitmap(bitmap, quarterWidth, quarterHeight, quarterWidth, quarterHeight)
    }

    private fun areBitmapsSame(b1: Bitmap?, b2: Bitmap?): Boolean {
        return b1 != null && b2 != null && b1.width == b2.width && b1.height == b2.height && b1.sameAs(b2)
    }
}
