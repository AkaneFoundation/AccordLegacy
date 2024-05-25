package org.akanework.gramophone.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore.Audio.Media
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.addListener
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fluidrecyclerview.widget.LinearLayoutManager
import androidx.fluidrecyclerview.widget.RecyclerView
import androidx.fragment.app.activityViewModels
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import androidx.transition.TransitionManager
import coil3.annotation.ExperimentalCoilApi
import coil3.dispose
import coil3.imageLoader
import coil3.load
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.error
import coil3.request.placeholder
import coil3.size.Scale
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.OverlaySlider
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.android.material.transition.MaterialContainerTransform
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.checkIfNegativeOrNullOrMaxedOut
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.fadInAnimation
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.getLyrics
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.hasImagePermission
import org.akanework.gramophone.logic.hasScopedStorageV1
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.hasTimer
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.setTextAnimation
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.logic.ui.CustomLinearLayoutManager
import org.akanework.gramophone.logic.ui.CustomSmoothScroller
import org.akanework.gramophone.logic.ui.coolCrossfade
import org.akanework.gramophone.logic.updateMargin
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.logic.utils.DatabaseUtils
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.MainActivity
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.pow

@SuppressLint("SetTextI18n")
class FullBottomSheet(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Player.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context) : this(context, null)

    private val activity
        get() = context as MainActivity
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val instance: MediaController?
        get() = if (controllerFuture?.isDone == false || controllerFuture?.isCancelled == true)
            null else controllerFuture?.get()
    var minimize: (() -> Unit)? = null

    private var wrappedContext: Context? = null
    private var isUserTracking = false
    private var runnableRunning = false
    private var firstTime = false
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val interpolator = PathInterpolator(0.4f, 0.2f, 0f, 1f)




    companion object {
        const val SLIDER_UPDATE_INTERVAL = 100L
        const val VIEW_TRANSIT_DURATION = 350L
        const val LYRIC_REMOVE_HIGHLIGHT = 0
        const val LYRIC_SET_HIGHLIGHT = 1
        const val LYRIC_SCROLL_DURATION = 700L
        const val SHRINK_VALUE_DEFAULT = 0.93F
        const val ALBUM_SHRINK_DURATION_ANIMATION = 300L
        const val SHRINK_TRIGGER_DURATION = 300L
        const val SHRINK_VALUE_PAUSE = 0.85F
        const val BOTTOM_TRANSIT_DURATION = 250L
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val LYRIC_UPDATE_BLUR = 2
        const val LYRIC_CLEAR_ALL = 3
    }


    private fun buildShrinkAnimator(
        isShrink: Boolean = true,
        shrinkValue: Float = SHRINK_VALUE_DEFAULT,
        completion: (() -> Unit)? = null
    ) {
        if (bottomSheetFullCoverFrame.isVisible) {
            val scaleX = PropertyValuesHolder.ofFloat(
                View.SCALE_X,
                if (isShrink) 1f else shrinkValue,
                if (isShrink) shrinkValue else 1f
            )
            val scaleY = PropertyValuesHolder.ofFloat(
                View.SCALE_Y,
                if (isShrink) 1f else shrinkValue,
                if (isShrink) shrinkValue else 1f
            )
            bottomSheetFullCoverFrame.apply {
                val animator = ObjectAnimator.ofPropertyValuesHolder(this, scaleX, scaleY)
                animator.duration = ALBUM_SHRINK_DURATION_ANIMATION
                animator.interpolator = DecelerateInterpolator()
                animator.doOnEnd {
                    fullCoverFrameCoordinateX = bottomSheetFullCoverFrame.left +
                            if (isShrink) (bottomSheetFullCoverFrame.height * ((1f - shrinkValue) / 2)).toInt() else 0
                    fullCoverFrameCoordinateY = bottomSheetFullCoverFrame.top +
                            if (isShrink) (bottomSheetFullCoverFrame.height * ((1f - shrinkValue) / 2)).toInt() else 0
                    fullCoverFrameScale = bottomSheetFullCoverFrame.height *
                            (if (isShrink) shrinkValue else 1f) / 48.dpToPx(context).toFloat() - 1f
                    completion?.let {
                        it()
                    }
                }
                animator.start()
            }
        }
    }


    private val touchListener = object : OverlaySlider.OnSliderTouchListener {
        override fun onStartTrackingTouch(slider: OverlaySlider) {
            isUserTracking = true
            handler.postDelayed({
                if (isUserTracking && bottomSheetFullCoverFrame.scaleX == 1.0F) {
                    buildShrinkAnimator()
                }
            }, SHRINK_TRIGGER_DURATION)
        }

        override fun onStopTrackingTouch(slider: OverlaySlider) {
            val mediaId = instance?.currentMediaItem?.mediaId
            if (mediaId != null) {
                instance?.seekTo((slider.value.toLong()))
                updateLyric(slider.value.toLong())
            }
            isUserTracking = false
            if (bottomSheetFullCoverFrame.scaleX >= 0.93F &&
                bottomSheetFullCoverFrame.scaleX < 1.0F
            ) {
                buildShrinkAnimator(false)
            }
        }
    }

    private val volumeChangeListener = OverlaySlider.OnChangeListener { _, value, fromUser ->
        if (fromUser) {
            if ((currentVolume - value.toInt()).absoluteValue >= 1) {
                Log.d("TAG", "KNOCK KNOCK, CURRENT: ${currentVolume}, $value")
                CoroutineScope(Dispatchers.Default).launch {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value.toInt(), 0)
                    currentVolume = value.toInt()
                }
            }
        }
    }

    private val volumeTouchListener = object : OverlaySlider.OnSliderTouchListener {
        override fun onStartTrackingTouch(slider: OverlaySlider) {
            volumeLock = true
        }

        override fun onStopTrackingTouch(slider: OverlaySlider) {
            volumeLock = false
        }

    }

    private val bottomSheetFullCover: ImageView
    private val bottomSheetFullTitle: TextView
    private val bottomSheetFullSubtitle: TextView
    private val bottomSheetFullSubtitleUnder: TextView
    private val bottomSheetFullControllerButton: MaterialButton
    private val bottomSheetFullNextButton: MaterialButton
    private val bottomSheetFullPreviousButton: MaterialButton
    private val bottomSheetFullDuration: TextView
    private val bottomSheetFullDurationBack: TextView
    private val bottomSheetFullPosition: TextView
    private val bottomSheetFullPositionBack: TextView
    private val bottomSheetShuffleButton: MaterialButton
    private val bottomSheetLoopButton: MaterialButton
    private val bottomSheetPlaylistButton: MaterialButton
    private val bottomSheetTimerButton: MaterialButton
    private val bottomSheetInfinityButton: MaterialButton
    private val bottomSheetFullLyricButton: MaterialButton
    private val bottomSheetFullSlider: OverlaySlider
    private val bottomSheetFullCoverFrame: MaterialCardView
    private val bottomSheetFullControllerFrame: ConstraintLayout
    private val bottomSheetFullLyricRecyclerView: RecyclerView
    private val bottomSheetFullLyricList: MutableList<MediaStoreUtils.Lyric> = mutableListOf()
    private val bottomSheetFullLyricAdapter: LyricAdapter = LyricAdapter(bottomSheetFullLyricList)
    private val bottomSheetFullLyricLinearLayoutManager = CustomLinearLayoutManager(context)
    private val bottomSheetFullDragHandle: BottomSheetDragHandleView
    private val bottomSheetFullTextLayout: View
    private val bottomSheetFullHeaderFrame: ConstraintLayout
    private val bottomSheetFullPlaylistFrame: ConstraintLayout
    private val bottomSheetFullPlaylistCover: ImageView
    private val bottomSheetFullPlaylistTitle: TextView
    private val bottomSheetFullPlaylistSubtitle: TextView
    private val bottomSheetFullPlaylistSubtitleUnder: TextView
    private val bottomSheetFullPlaylistRecyclerView: RecyclerView
    private val bottomSheetFullPlaylistAdapter: PlaylistCardAdapter
    private val bottomSheetFullPlaylistCoverFrame: MaterialCardView
    private val bottomSheetQualityOverlay: View
    private val bottomSheetQualityFrame: View
    private val bottomSheetQualityCard: View
    private val bottomSheetStarButton: MaterialButton
    private val bottomSheetVolumeStartOverlayImageView: ImageView
    private val bottomSheetVolumeEndOverlayImageView: ImageView
    private val bottomSheetVolumeSlider: OverlaySlider
    private val bottomSheetVolumeSliderFrame: View
    private val bottomSheetStarButtonBackground: ImageView
    private val bottomSheetStarButtonPlaylist: MaterialButton
    private val bottomSheetStarButtonPlaylistBackground: ImageView
    private val bottomSheetMoreButtonBackground: ImageView
    private val bottomSheetMoreButtonPlaylistBackground: ImageView
    private val bottomSheetActionBar: LinearLayout
    private val bottomSheetFadingVerticalEdgeLayout: FadingVerticalEdgeLayout
    private var playlistNowPlaying: TextView? = null
    private var playlistNowPlayingCover: ImageView? = null
    private var triggerLock: Boolean = false
    var bottomSheetFullBlendView: BlendView? = null
    private var lastDisposable: Disposable? = null
    private var animationLock: Boolean = false
    private var hideJob: CoroutineScope? = null
    private var blurTransitionJob: CoroutineScope? = null
    private var startY = 0f
    private var isScrollingDown = false
    private var animationBroadcastLock = false
    private var queryFavouriteJob: CoroutineScope? = null
    private var favouriteLock = false
    private val audioManager: AudioManager
    private var volumeChangeReceiver: VolumeChangeReceiver
    private val volumeChangeReceiverIntentFilter: IntentFilter
    private var volumeLock = false
    private var currentVolume: Int = 0
    private var hasScheduledShowJob = false
    var fullCoverFrameCoordinateX: Int = 0
    var fullCoverFrameCoordinateY: Int = 0
    var playlistCoverCoordinateX: Int = 0
    var playlistCoverCoordinateY: Int = 0
    var fullCoverFrameScale: Float = 0f
    var playlistCoverScale: Float = 0f
    var isPlaylistEnabled: Boolean = false
    var blurLock: Boolean = false
    var isBlurEnabled: Boolean = false

    private val overlayPaint = Paint().apply {
        blendMode = BlendMode.OVERLAY
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }

    init {
        inflate(context, R.layout.full_player, this)
        bottomSheetFullCoverFrame = findViewById(R.id.album_cover_frame)
        bottomSheetFullCover = findViewById(R.id.full_sheet_cover)
        bottomSheetFullTitle = findViewById(R.id.full_song_name)
        bottomSheetFullSubtitle = findViewById(R.id.full_song_artist)
        bottomSheetFullSubtitleUnder = findViewById(R.id.full_song_artist_under)
        bottomSheetFullPreviousButton = findViewById(R.id.sheet_previous_song)
        bottomSheetFullControllerButton = findViewById(R.id.sheet_mid_button)
        bottomSheetFullNextButton = findViewById(R.id.sheet_next_song)
        bottomSheetFullPosition = findViewById(R.id.position)
        bottomSheetFullPositionBack = findViewById(R.id.position_back)
        bottomSheetFullDuration = findViewById(R.id.duration)
        bottomSheetFullDurationBack = findViewById(R.id.duration_back)
        bottomSheetFullSlider = findViewById(R.id.slider_vert)
        bottomSheetFullLyricButton = findViewById(R.id.lyric_btn)
        bottomSheetShuffleButton = findViewById(R.id.sheet_random)
        bottomSheetLoopButton = findViewById(R.id.sheet_loop)
        bottomSheetTimerButton = findViewById(R.id.timer)
        bottomSheetPlaylistButton = findViewById(R.id.playlist)
        bottomSheetFullLyricRecyclerView = findViewById(R.id.lyric_frame)
        bottomSheetFullDragHandle = findViewById(R.id.drag)
        bottomSheetFullTextLayout = findViewById(R.id.textLayout)
        bottomSheetFullHeaderFrame = findViewById(R.id.playlist_frame)
        bottomSheetFullPlaylistFrame = findViewById(R.id.playlist_content)
        bottomSheetFullPlaylistCover = findViewById(R.id.playlist_demo_cover)
        bottomSheetFullPlaylistCoverFrame = findViewById(R.id.playlist_cover_frame)
        bottomSheetFullControllerFrame = findViewById(R.id.controls)
        bottomSheetFullPlaylistTitle = findViewById(R.id.playlist_song_name)
        bottomSheetFullPlaylistSubtitle = findViewById(R.id.playlist_song_artist)
        bottomSheetFullPlaylistSubtitleUnder = findViewById(R.id.playlist_song_artist_under)
        bottomSheetFullPlaylistRecyclerView = findViewById(R.id.playlist_recyclerview)
        bottomSheetStarButton = findViewById(R.id.star_btn)
        bottomSheetVolumeStartOverlayImageView = findViewById(R.id.volume_icon_start_bottom)
        bottomSheetVolumeEndOverlayImageView = findViewById(R.id.volume_icon_end_bottom)
        bottomSheetVolumeSlider = findViewById(R.id.slider_volume)
        bottomSheetVolumeSliderFrame = findViewById(R.id.volume_frame)
        bottomSheetStarButtonBackground = findViewById(R.id.star_bg)
        bottomSheetStarButtonPlaylist = findViewById(R.id.star_btn_playlist)
        bottomSheetStarButtonPlaylistBackground = findViewById(R.id.star_btn_playlist_bg)
        bottomSheetMoreButtonBackground = findViewById(R.id.more_bg)
        bottomSheetMoreButtonPlaylistBackground = findViewById(R.id.more_btn_playlist_bg)
        bottomSheetInfinityButton = findViewById(R.id.sheet_infinity)
        bottomSheetActionBar = findViewById(R.id.actionBar)
        bottomSheetQualityOverlay = findViewById(R.id.quality_overlay)
        bottomSheetQualityFrame = findViewById(R.id.quality_frame)
        bottomSheetQualityCard = findViewById(R.id.quality_card)
        bottomSheetFadingVerticalEdgeLayout = findViewById(R.id.fadingEdgeLayout)

        bottomSheetFullPlaylistAdapter = PlaylistCardAdapter(activity)
        bottomSheetFullPlaylistRecyclerView.layoutManager = LinearLayoutManager(context)
        bottomSheetFullPlaylistRecyclerView.adapter = bottomSheetFullPlaylistAdapter

        bottomSheetFullPlaylistSubtitleUnder.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetFullSubtitleUnder.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetFullPlaylistSubtitle.setLayerType(LAYER_TYPE_HARDWARE,overlayPaint)

        bottomSheetFullDurationBack.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetFullPositionBack.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetQualityOverlay.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetQualityFrame.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)

        bottomSheetFadingVerticalEdgeLayout.setLayerType(LAYER_TYPE_HARDWARE,null)


        prefs.registerOnSharedPreferenceChangeListener(this)
        isBlurEnabled = prefs.getBooleanStrict("lyric_blur", false)

        doOnLayout {
            fullCoverFrameCoordinateX = bottomSheetFullCoverFrame.left
            fullCoverFrameCoordinateY = bottomSheetFullCoverFrame.top
            fullCoverFrameScale = bottomSheetFullCoverFrame.height / 48.dpToPx(context).toFloat() - 1f
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheetFullLyricRecyclerView) { v, insets ->
            val myInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updateMargin {
                left = -myInsets.left
                top = -myInsets.top
                right = -myInsets.right
                bottom = -myInsets.bottom
            }
            v.setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
                )
                .setInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
                )
                .build()
        }

        bottomSheetTimerButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val picker =
                MaterialTimePicker
                    .Builder()
                    .setHour((instance?.getTimer() ?: 0) / 3600 / 1000)
                    .setMinute(((instance?.getTimer() ?: 0) % (3600 * 1000)) / (60 * 1000))
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                    .build()
            picker.addOnPositiveButtonClickListener {
                val destinationTime: Int = picker.hour * 1000 * 3600 + picker.minute * 1000 * 60
                instance?.setTimer(destinationTime)
            }
            picker.addOnDismissListener {
                bottomSheetTimerButton.isChecked = instance?.hasTimer() == true
            }
            picker.show(activity.supportFragmentManager, "timer")
        }

        bottomSheetLoopButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            when (instance?.repeatMode) {
                Player.REPEAT_MODE_OFF -> {
                    bottomSheetInfinityButton.isChecked = false
                    bottomSheetInfinityButton.isEnabled = false
                    instance?.repeatMode = Player.REPEAT_MODE_ALL
                }

                Player.REPEAT_MODE_ALL -> {
                    instance?.repeatMode = Player.REPEAT_MODE_ONE
                }

                Player.REPEAT_MODE_ONE -> {
                    bottomSheetInfinityButton.isEnabled = true
                    instance?.repeatMode = Player.REPEAT_MODE_OFF
                }

                else -> throw IllegalStateException()
            }
        }

        bottomSheetStarButton.addOnCheckedChangeListener { _, b ->
            if (!favouriteLock) {
                val mediaId = instance?.currentMediaItem?.mediaId?.toLong() ?: return@addOnCheckedChangeListener
                CoroutineScope(Dispatchers.Main).launch {
                    if (b) {
                        favouriteLock = true
                        bottomSheetStarButtonPlaylist.isChecked = true
                        favouriteLock = false
                        DatabaseUtils.favouriteSong(mediaId, activity.libraryViewModel, context)
                    } else {
                        favouriteLock = true
                        bottomSheetStarButtonPlaylist.isChecked = false
                        favouriteLock = false
                        DatabaseUtils.removeFavouriteSong(mediaId, activity.libraryViewModel, context)
                    }
                    val targetRes = if (b)
                        R.drawable.ic_nowplaying_favorited
                    else
                        R.drawable.ic_nowplaying_favorite
                    bottomSheetStarButtonBackground.setImageResource(
                        targetRes
                    )
                    bottomSheetStarButtonPlaylistBackground.setImageResource(
                        targetRes
                    )
                }
            }
        }

        bottomSheetStarButtonPlaylist.addOnCheckedChangeListener { _, b ->
            if (!favouriteLock) {
                val mediaId = instance?.currentMediaItem?.mediaId?.toLong() ?: return@addOnCheckedChangeListener
                CoroutineScope(Dispatchers.Main).launch {
                    if (b) {
                        favouriteLock = true
                        bottomSheetStarButton.isChecked = true
                        favouriteLock = false
                        DatabaseUtils.favouriteSong(mediaId, activity.libraryViewModel, context)
                    } else {
                        favouriteLock = true
                        bottomSheetStarButton.isChecked = false
                        favouriteLock = false
                        DatabaseUtils.removeFavouriteSong(mediaId, activity.libraryViewModel, context)
                    }
                    val targetRes = if (b)
                        R.drawable.ic_nowplaying_favorited
                    else
                        R.drawable.ic_nowplaying_favorite
                    bottomSheetStarButtonBackground.setImageResource(
                        targetRes
                    )
                    bottomSheetStarButtonPlaylistBackground.setImageResource(
                        targetRes
                    )
                }
            }
        }

        bottomSheetInfinityButton.addOnCheckedChangeListener { it, isChecked ->
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (isChecked) {
                bottomSheetLoopButton.isChecked = false
                bottomSheetLoopButton.isEnabled = false
                instance?.repeatMode = Player.REPEAT_MODE_ONE
            } else {
                bottomSheetLoopButton.isEnabled = true
                instance?.repeatMode = Player.REPEAT_MODE_OFF
            }
        }

        bottomSheetFullPlaylistCoverFrame.setOnClickListener {
            if (bottomSheetFullLyricButton.isChecked) {
                bottomSheetFullLyricButton.isChecked = false
            }
            if (bottomSheetPlaylistButton.isChecked) {
                bottomSheetPlaylistButton.isChecked = false
            }
        }

        bottomSheetPlaylistButton.addOnCheckedChangeListener { _, isChecked ->
            if (triggerLock) {
                triggerLock = false
                return@addOnCheckedChangeListener
            }
            if (isChecked && !bottomSheetFullLyricButton.isChecked) {
                changeMovableFrame(false)
                bottomSheetFullPlaylistRecyclerView.scrollToPosition(dumpPlaylist().indexOfFirst { item ->
                    item.first == (instance?.currentMediaItemIndex ?: 0)
                })
                isPlaylistEnabled = true
                bottomSheetFullHeaderFrame.fadInAnimation(VIEW_TRANSIT_DURATION) {
                    manipulateTopOverlayVisibility(View.VISIBLE)
                    playlistCoverCoordinateX = bottomSheetFullPlaylistCoverFrame.left + bottomSheetFullHeaderFrame.left
                    playlistCoverCoordinateY = bottomSheetFullPlaylistCoverFrame.top + bottomSheetFullHeaderFrame.top
                    playlistCoverScale = bottomSheetFullPlaylistCoverFrame.height / 48.dpToPx(context).toFloat() - 1f
                }
                bottomSheetFullPlaylistFrame.fadInAnimation(VIEW_TRANSIT_DURATION)
                bottomSheetFullBlendView?.animateBlurRadius(false, VIEW_TRANSIT_DURATION)
            } else if (bottomSheetFullLyricButton.isChecked) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                triggerLock = true
                bottomSheetFullLyricButton.isChecked = false
                bottomSheetFadingVerticalEdgeLayout.changeOverlayVisibility(false)
                bottomSheetFadingVerticalEdgeLayout.fadOutAnimation(VIEW_TRANSIT_DURATION)
                bottomSheetFullPlaylistFrame.fadInAnimation(VIEW_TRANSIT_DURATION)
                hideJob?.cancel()
                if (bottomSheetFullControllerButton.visibility == View.GONE || bottomSheetFullControllerButton.visibility == View.INVISIBLE) {
                    showEveryController()
                }
            } else {
                changeMovableFrame(true)
                isPlaylistEnabled = false
                bottomSheetFullHeaderFrame.fadOutAnimation(VIEW_TRANSIT_DURATION, View.GONE)
                bottomSheetFullPlaylistFrame.fadOutAnimation(VIEW_TRANSIT_DURATION, View.GONE)
                bottomSheetFullBlendView?.animateBlurRadius(true, VIEW_TRANSIT_DURATION)
            }
        }

        bottomSheetFullLyricButton.addOnCheckedChangeListener { _, isChecked ->
            if (triggerLock) {
                triggerLock = false
                return@addOnCheckedChangeListener
            }
            if (isChecked && !bottomSheetPlaylistButton.isChecked) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                changeMovableFrame(false)
                isPlaylistEnabled = true
                bottomSheetFullHeaderFrame.fadInAnimation(VIEW_TRANSIT_DURATION) {
                    manipulateTopOverlayVisibility(View.VISIBLE)
                    playlistCoverCoordinateX = bottomSheetFullPlaylistCoverFrame.left + bottomSheetFullHeaderFrame.left
                    playlistCoverCoordinateY = bottomSheetFullPlaylistCoverFrame.top + bottomSheetFullHeaderFrame.top
                    playlistCoverScale = bottomSheetFullPlaylistCoverFrame.height / 48.dpToPx(context).toFloat() - 1f
                }
                bottomSheetFadingVerticalEdgeLayout.setPadding(
                    bottomSheetFadingVerticalEdgeLayout.paddingLeft,
                    bottomSheetFadingVerticalEdgeLayout.paddingTop,
                    bottomSheetFadingVerticalEdgeLayout.paddingRight,
                    if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                        getDistanceToBottom(bottomSheetFullSlider)
                    else
                        0
                )
                bottomSheetFadingVerticalEdgeLayout.fadInAnimation(VIEW_TRANSIT_DURATION) {
                    bottomSheetFadingVerticalEdgeLayout.changeOverlayVisibility(true)
                }
                hideControllerJob()
                bottomSheetFullBlendView?.animateBlurRadius(false, VIEW_TRANSIT_DURATION)
            } else if (bottomSheetPlaylistButton.isChecked) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                triggerLock = true
                bottomSheetPlaylistButton.isChecked = false
                bottomSheetFullPlaylistFrame.fadOutAnimation(VIEW_TRANSIT_DURATION)
                bottomSheetFadingVerticalEdgeLayout.setPadding(
                    bottomSheetFadingVerticalEdgeLayout.paddingLeft,
                    bottomSheetFadingVerticalEdgeLayout.paddingTop,
                    bottomSheetFadingVerticalEdgeLayout.paddingRight,
                    if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                        getDistanceToBottom(bottomSheetFullSlider)
                    else
                        0
                )
                bottomSheetFadingVerticalEdgeLayout.fadInAnimation(VIEW_TRANSIT_DURATION) {
                    bottomSheetFadingVerticalEdgeLayout.changeOverlayVisibility(true)
                }
                hideControllerJob()
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                changeMovableFrame(true)
                isPlaylistEnabled = false
                bottomSheetFullHeaderFrame.fadOutAnimation(VIEW_TRANSIT_DURATION, View.GONE)
                bottomSheetFadingVerticalEdgeLayout.changeOverlayVisibility(false)
                bottomSheetFadingVerticalEdgeLayout.fadOutAnimation(VIEW_TRANSIT_DURATION, View.GONE)

                hideJob?.cancel()
                if (bottomSheetFullControllerButton.visibility == View.GONE || bottomSheetFullControllerButton.visibility == View.INVISIBLE) {
                    showEveryController()
                }
                bottomSheetFullBlendView?.animateBlurRadius(true, VIEW_TRANSIT_DURATION)
            }
        }

        bottomSheetFullControllerButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (instance?.isPlaying == true && bottomSheetFullCoverFrame.scaleX == 1.0F) {
                buildShrinkAnimator(shrinkValue = SHRINK_VALUE_PAUSE)
            } else if (bottomSheetFullCoverFrame.scaleX == SHRINK_VALUE_PAUSE) {
                buildShrinkAnimator(false, SHRINK_VALUE_PAUSE)
            }
            instance?.playOrPause()
        }
        bottomSheetFullPreviousButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            instance?.seekToPreviousMediaItem()
        }
        bottomSheetFullNextButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            instance?.seekToNextMediaItem()
        }
        bottomSheetShuffleButton.addOnCheckedChangeListener { _, isChecked ->
            instance?.shuffleModeEnabled = isChecked
        }

        bottomSheetFullSlider.addOnChangeListener { _, value, isUser ->
            if (isUser) {
                val dest = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
                if (dest != null) {
                    bottomSheetFullPosition.text =
                        CalculationUtils.convertDurationToTimeStamp((value).toLong())
                    bottomSheetFullPositionBack.text =
                        bottomSheetFullPosition.text
                    bottomSheetFullDuration.text =
                        '-' + CalculationUtils.convertDurationToTimeStamp(dest - (value).toLong())
                    bottomSheetFullDurationBack.text =
                        bottomSheetFullDuration.text
                }
            }
        }

        bottomSheetFullSlider.addOnSliderTouchListener(touchListener)

        bottomSheetShuffleButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        bottomSheetFullLyricRecyclerView.layoutManager =
            bottomSheetFullLyricLinearLayoutManager
        bottomSheetFullLyricRecyclerView.adapter =
            bottomSheetFullLyricAdapter
        bottomSheetFullLyricRecyclerView.addItemDecoration(LyricPaddingDecoration(context))
        bottomSheetFullLyricRecyclerView.addOnItemTouchListener(object :
            RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = e.y
                        if (!blurLock && isBlurEnabled) {
                            blurLock = true
                            clearAllBlur()
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val currentY = e.y
                        isScrollingDown = currentY < startY
                        if (!animationBroadcastLock && !isScrollingDown && bottomSheetFullControllerButton.visibility != View.VISIBLE) {
                            // Down
                            animationBroadcastLock = true
                            showEveryController()
                            val animator = ValueAnimator.ofInt(
                                0,
                                if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                                    getDistanceToBottom(bottomSheetFullSlider)
                                else
                                    0
                            )
                            animator.addUpdateListener {
                                val value = it.animatedValue as Int
                                bottomSheetFadingVerticalEdgeLayout.setPadding(
                                    bottomSheetFadingVerticalEdgeLayout.paddingLeft,
                                    bottomSheetFadingVerticalEdgeLayout.paddingTop,
                                    bottomSheetFadingVerticalEdgeLayout.paddingRight,
                                    value
                                )
                            }
                            animator.doOnEnd {
                                animationBroadcastLock = false
                            }
                            animator.duration = BOTTOM_TRANSIT_DURATION / 3 * 2
                            animator.start()
                            hideControllerJob()
                        } else if (!animationBroadcastLock && isScrollingDown) {
                            animationBroadcastLock = true
                            hideJob?.cancel()
                            // Up
                            hideEveryController()
                            val animator =
                                ValueAnimator.ofInt(bottomSheetFadingVerticalEdgeLayout.paddingBottom, 0)
                            animator.addUpdateListener {
                                val value = it.animatedValue as Int
                                bottomSheetFadingVerticalEdgeLayout.setPadding(
                                    bottomSheetFadingVerticalEdgeLayout.paddingLeft,
                                    bottomSheetFadingVerticalEdgeLayout.paddingTop,
                                    bottomSheetFadingVerticalEdgeLayout.paddingRight,
                                    value
                                )
                            }
                            animator.doOnEnd {
                                animationBroadcastLock = false
                            }
                            animator.duration = BOTTOM_TRANSIT_DURATION / 3 * 2
                            animator.start()
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (isBlurEnabled) {
                            setDelayedTransition()
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            }
        })

        audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        bottomSheetVolumeSlider.valueTo = maxVolume.toFloat()
        bottomSheetVolumeSlider.value = currentVolume.toFloat()
        bottomSheetVolumeSlider.addOnChangeListener(volumeChangeListener)
        bottomSheetVolumeSlider.addOnSliderTouchListener(volumeTouchListener)

        volumeChangeReceiver = VolumeChangeReceiver()
        volumeChangeReceiverIntentFilter = IntentFilter()
        volumeChangeReceiverIntentFilter.addAction(VOLUME_CHANGED_ACTION)
        volumeChangeReceiverIntentFilter.addAction(Intent.ACTION_HEADSET_PLUG)
        volumeChangeReceiverIntentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(volumeChangeReceiver)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(volumeChangeReceiver, volumeChangeReceiverIntentFilter)
    }

    private fun getDistanceToBottom(view: View): Int {
        val windowMetrics =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
        val screenHeight = windowMetrics.bounds.height()

        val location = IntArray(2)
        view.getLocationOnScreen(location)

        return screenHeight - (location[1] + view.height)
    }

    private fun hideControllerJob() {
        hideJob?.cancel()
        hideJob = CoroutineScope(Dispatchers.Default)
        hideJob!!.launch {
            delay(5000)
            hideEveryController()
            withContext(Dispatchers.Main) {
                val animator = ValueAnimator.ofInt(bottomSheetFadingVerticalEdgeLayout.paddingBottom, 0)
                animator.addUpdateListener {
                    val value = it.animatedValue as Int
                    bottomSheetFadingVerticalEdgeLayout.setPadding(
                        bottomSheetFadingVerticalEdgeLayout.paddingLeft,
                        bottomSheetFadingVerticalEdgeLayout.paddingTop,
                        bottomSheetFadingVerticalEdgeLayout.paddingRight,
                        value
                    )
                }
                animator.doOnEnd {
                    if (hasScheduledShowJob) {
                        showEveryController()
                        hasScheduledShowJob = false
                    }
                }
                animator.duration = BOTTOM_TRANSIT_DURATION / 3 * 2
                animator.start()
            }
        }
    }

    fun changeBottomCoverVisibility(visibility: Int) {
        if (isPlaylistEnabled) {
            bottomSheetFullPlaylistCoverFrame.visibility = visibility
        } else {
            bottomSheetFullCoverFrame.visibility = visibility
        }
    }

    private fun hideEveryController() {
        manipulateBottomOverlayVisibility(View.INVISIBLE)
        bottomSheetFullControllerFrame.fadOutAnimation(BOTTOM_TRANSIT_DURATION)
        bottomSheetFullControllerButton.fadOutAnimation(BOTTOM_TRANSIT_DURATION)
        bottomSheetVolumeSliderFrame.fadOutAnimation(BOTTOM_TRANSIT_DURATION)
        bottomSheetFullNextButton.fadOutAnimation(BOTTOM_TRANSIT_DURATION)
        bottomSheetFullPreviousButton.fadOutAnimation(BOTTOM_TRANSIT_DURATION)
        bottomSheetActionBar.fadOutAnimation(BOTTOM_TRANSIT_DURATION)
    }

    private fun showEveryController() {
        bottomSheetFullControllerFrame.fadInAnimation(BOTTOM_TRANSIT_DURATION) { manipulateBottomOverlayVisibility(View.VISIBLE) }
        bottomSheetFullControllerButton.fadInAnimation(BOTTOM_TRANSIT_DURATION)
        bottomSheetVolumeSliderFrame.fadInAnimation(BOTTOM_TRANSIT_DURATION)
        bottomSheetFullNextButton.fadInAnimation(BOTTOM_TRANSIT_DURATION)
        bottomSheetFullPreviousButton.fadInAnimation(BOTTOM_TRANSIT_DURATION)
        bottomSheetActionBar.fadInAnimation(BOTTOM_TRANSIT_DURATION)
    }

    private fun isHires(boolean: Boolean) {
        if (!bottomSheetQualityCard.isVisible && boolean) {
            bottomSheetQualityCard.fadInAnimation(VIEW_TRANSIT_DURATION)
        } else if (bottomSheetQualityCard.isVisible && !boolean) {
            bottomSheetQualityCard.fadOutAnimation(VIEW_TRANSIT_DURATION)
        }
    }

    fun isCoverFrameElevated(): Boolean =
        bottomSheetFullCoverFrame.elevation == 8.dpToPx(context).toFloat()

    fun applyElevation(remove: Boolean) {
        val animator = ValueAnimator.ofFloat(
            if (remove) 8.dpToPx(context).toFloat() else 0f,
            if (remove) 0f else 8.dpToPx(context).toFloat()
        )
        animator.apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                bottomSheetFullCoverFrame.elevation = value
            }
            duration = 200
        }
        animator.start()
    }

    private val transformIn = MaterialContainerTransform().apply {
        startView = bottomSheetFullCoverFrame
        endView = bottomSheetFullPlaylistCoverFrame
        addTarget(bottomSheetFullPlaylistCoverFrame)
        scrimColor = Color.TRANSPARENT
        duration = VIEW_TRANSIT_DURATION
    }

    private val transformOut = MaterialContainerTransform().apply {
        startView = bottomSheetFullPlaylistCoverFrame
        endView = bottomSheetFullCoverFrame
        addTarget(bottomSheetFullCoverFrame)
        scrimColor = Color.TRANSPARENT
        duration = VIEW_TRANSIT_DURATION
    }

    private fun changeMovableFrame(isVisible: Boolean) {
        if (isVisible) {
            manipulateTopOverlayVisibility(View.INVISIBLE)
            bottomSheetFullTextLayout.fadInAnimation(VIEW_TRANSIT_DURATION) { manipulateTopOverlayVisibility(View.VISIBLE) }
            bottomSheetFullDragHandle.fadInAnimation(VIEW_TRANSIT_DURATION)
            TransitionManager.beginDelayedTransition(this, transformOut)
            bottomSheetFullPlaylistCoverFrame.visibility = View.INVISIBLE
            bottomSheetFullCoverFrame.visibility = View.VISIBLE
        } else {
            if (bottomSheetFullCoverFrame.scaleX == 1.0f) {
                manipulateTopOverlayVisibility(View.INVISIBLE)
                bottomSheetFullTextLayout.fadOutAnimation(VIEW_TRANSIT_DURATION)
                bottomSheetFullDragHandle.fadOutAnimation(VIEW_TRANSIT_DURATION)
                TransitionManager.beginDelayedTransition(this, transformIn)
                bottomSheetFullPlaylistCoverFrame.visibility = View.VISIBLE
                bottomSheetFullCoverFrame.visibility = View.INVISIBLE
            } else {
                manipulateTopOverlayVisibility(View.INVISIBLE)
                bottomSheetFullTextLayout.fadOutAnimation(VIEW_TRANSIT_DURATION)
                bottomSheetFullDragHandle.fadOutAnimation(VIEW_TRANSIT_DURATION)
                buildShrinkAnimator(false, bottomSheetFullCoverFrame.scaleX) {
                    TransitionManager.beginDelayedTransition(this, transformIn)
                    bottomSheetFullPlaylistCoverFrame.visibility = View.VISIBLE
                    bottomSheetFullCoverFrame.visibility = View.INVISIBLE
                }
            }
        }
    }

    val sessionListener: MediaController.Listener = object : MediaController.Listener {
        @SuppressLint("NotifyDataSetChanged")
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (command.customAction) {
                GramophonePlaybackService.SERVICE_TIMER_CHANGED -> {
                    bottomSheetTimerButton.isChecked = controller.hasTimer()
                }

                GramophonePlaybackService.SERVICE_SHUFFLE_CHANGED -> {
                    bottomSheetFullPlaylistAdapter.updatePlaylistWhenShuffle(
                        dumpPlaylist()
                    )
                }

                GramophonePlaybackService.SERVICE_GET_LYRICS -> {
                    val parsedLyrics = instance?.getLyrics()
                    if (bottomSheetFullLyricList != parsedLyrics) {
                        bottomSheetFullLyricList.clear()
                        if (parsedLyrics?.isEmpty() != false) {
                            bottomSheetFullLyricList.add(
                                MediaStoreUtils.Lyric(
                                    0,
                                    context.getString(R.string.no_lyric_found)
                                )
                            )
                        } else {
                            bottomSheetFullLyricList.addAll(parsedLyrics)
                        }
                        bottomSheetFullLyricAdapter.notifyDataSetChanged()
                        resetToDefaultLyricPosition()
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    fun onStart(cf: ListenableFuture<MediaController>) {
        controllerFuture = cf
        controllerFuture!!.addListener({
            firstTime = true
            instance?.addListener(this)
            bottomSheetTimerButton.isChecked = instance?.hasTimer() == true
            onRepeatModeChanged(instance?.repeatMode ?: Player.REPEAT_MODE_OFF)
            onShuffleModeEnabledChanged(instance?.shuffleModeEnabled ?: false)
            onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            firstTime = false
        }, MoreExecutors.directExecutor())
    }

    fun onStop() {
        runnableRunning = false
        instance?.removeListener(this)
        controllerFuture = null
    }

    override fun dispatchApplyWindowInsets(platformInsets: WindowInsets): WindowInsets {
        val insets = WindowInsetsCompat.toWindowInsetsCompat(platformInsets)
        val myInsets = insets.getInsets(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )
        setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
        return WindowInsetsCompat.Builder(insets)
            .setInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
            )
            .setInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
            )
            .build()
            .toWindowInsets()!!
    }

    @OptIn(ExperimentalCoilApi::class)
    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        if (instance?.mediaItemCount != 0) {
            val req = { data: Any?, block: ImageRequest.Builder.() -> Unit ->
                lastDisposable?.dispose()
                lastDisposable = context.imageLoader.enqueue(ImageRequest.Builder(context).apply {
                    data(data)
                    scale(Scale.FILL)
                    block()
                    error(R.drawable.ic_default_cover)
                    allowHardware(false)
                }.build())
            }
            val load = { data: Any? ->
                req(data) {
                    target(onSuccess = {
                        bottomSheetFullCover.setImageDrawable(it.asDrawable(context.resources))
                        bottomSheetFullPlaylistCover.setImageDrawable(it.asDrawable(context.resources))
                    }, onError = {
                        bottomSheetFullCover.setImageDrawable(it?.asDrawable(context.resources))
                        bottomSheetFullPlaylistCover.setImageDrawable(it?.asDrawable(context.resources))
                    }) // do not react to onStart() which sets placeholder
                }
            }
            val file = mediaItem?.getFile()
            if (hasScopedStorageV1() && (!hasScopedStorageWithMediaTypes()
                        || context.hasImagePermission()) && file != null
            ) {
                req(Pair(file, Size(bottomSheetFullCover.width, bottomSheetFullCover.height))) {
                    target(onSuccess = {
                        bottomSheetFullCover.setImageDrawable(it.asDrawable(context.resources))
                        bottomSheetFullPlaylistCover.setImageDrawable(it.asDrawable(context.resources))
                    }, onError = {
                        load(mediaItem.mediaMetadata.artworkUri)
                    })
                }
            } else {
                load(mediaItem?.mediaMetadata?.artworkUri)
            }
            bottomSheetFullTitle.setTextAnimation(
                mediaItem?.mediaMetadata?.title,
                skipAnimation = firstTime
            )
            bottomSheetFullPlaylistTitle.setTextAnimation(
                mediaItem?.mediaMetadata?.title,
                skipAnimation = firstTime
            )
            bottomSheetFullSubtitle.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                skipAnimation = firstTime
            )
            bottomSheetFullSubtitleUnder.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                skipAnimation = firstTime
            )
            bottomSheetFullPlaylistSubtitle.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                skipAnimation = firstTime
            )
            bottomSheetFullPlaylistSubtitleUnder.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                skipAnimation = firstTime
            )
            isHires(mediaItem?.localConfiguration?.mimeType?.contains("flac") == true)
            startQueryFavourite()
            if (playlistNowPlaying != null) {
                playlistNowPlaying!!.text = mediaItem?.mediaMetadata?.title
                playlistNowPlayingCover!!.load(mediaItem?.mediaMetadata?.artworkUri) {
                    coolCrossfade(true)
                    placeholder(R.drawable.ic_default_cover)
                    error(R.drawable.ic_default_cover)
                }
            }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                bottomSheetFullPlaylistAdapter.updatePlaylist(
                    dumpPlaylist()
                )
            }
        } else {
            lastDisposable?.dispose()
            lastDisposable = null
            bottomSheetFullCover.dispose()
            playlistNowPlayingCover?.dispose()
        }
        val currentPosition = instance?.currentPosition
        val position = CalculationUtils.convertDurationToTimeStamp(currentPosition ?: 0)
        val duration = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
        if (duration != null && duration != 0L && !isUserTracking) {
            bottomSheetFullSlider.valueTo = duration.toFloat()
            bottomSheetFullSlider.value =
                instance?.currentPosition?.toFloat()
                    .checkIfNegativeOrNullOrMaxedOut(bottomSheetFullSlider.valueTo)
            bottomSheetFullPosition.text = position
            bottomSheetFullPositionBack.text = bottomSheetFullPosition.text
            bottomSheetFullDuration.text =
                '-' +
                        CalculationUtils.convertDurationToTimeStamp(
                            instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
                                ?.minus((currentPosition ?: 0)) ?: 0
                        )
            bottomSheetFullDurationBack.text =
                bottomSheetFullDuration.text
        }
        updateLyric(duration)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            bottomSheetFullPlaylistAdapter.updatePlaylist(
                dumpPlaylist()
            )
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        bottomSheetShuffleButton.isChecked = shuffleModeEnabled
        bottomSheetFullPlaylistAdapter.updatePlaylistWhenShuffle(
            dumpPlaylist()
        )
    }

    private fun startQueryFavourite() {
        queryFavouriteJob?.cancel()
        queryFavouriteJob = CoroutineScope(Dispatchers.Main)
        queryFavouriteJob!!.launch {
            val mediaId = instance?.currentMediaItem?.mediaId?.toLong() ?: return@launch
            favouriteLock = true
            val isFav = DatabaseUtils.isFavourite(mediaId, activity.libraryViewModel)
            val targetRes = if (isFav)
                    R.drawable.ic_nowplaying_favorited
                else
                    R.drawable.ic_nowplaying_favorite
            bottomSheetStarButton.isChecked = isFav
            bottomSheetStarButtonPlaylist.isChecked = isFav
            bottomSheetStarButtonBackground.setImageResource(
                targetRes
            )
            bottomSheetStarButtonPlaylistBackground.setImageResource(
                targetRes
            )
            favouriteLock = false
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        when (repeatMode) {
            Player.REPEAT_MODE_ALL -> {
                bottomSheetLoopButton.isChecked = true
                bottomSheetLoopButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_repeat)
                bottomSheetInfinityButton.isChecked = false
                bottomSheetInfinityButton.isEnabled = false
            }

            Player.REPEAT_MODE_ONE -> {
                if (!bottomSheetInfinityButton.isChecked) {
                    bottomSheetLoopButton.isChecked = true
                    bottomSheetLoopButton.icon =
                        AppCompatResources.getDrawable(context, R.drawable.ic_repeat_one)
                }
            }

            Player.REPEAT_MODE_OFF -> {
                bottomSheetLoopButton.isChecked = false
                bottomSheetLoopButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_repeat)
                bottomSheetInfinityButton.isChecked = false
                bottomSheetInfinityButton.isEnabled = true
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
    }

    fun updateFavStatus() {
        startQueryFavourite()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (instance?.isPlaying == true) {
            if (bottomSheetFullControllerButton.getTag(R.id.play_next) as Int? != 1) {
                bottomSheetFullControllerButton.icon =
                    AppCompatResources.getDrawable(
                        if (wrappedContext != null) wrappedContext!! else context,
                        R.drawable.ic_apple_pause
                    )
                bottomSheetFullControllerButton.setTag(R.id.play_next, 1)
            }
            if (!runnableRunning) {
                handler.postDelayed(positionRunnable, SLIDER_UPDATE_INTERVAL)
                runnableRunning = true
            }
        } else if (playbackState != Player.STATE_BUFFERING) {
            if (bottomSheetFullControllerButton.getTag(R.id.play_next) as Int? != 2) {
                bottomSheetFullControllerButton.icon =
                    AppCompatResources.getDrawable(
                        if (wrappedContext != null) wrappedContext!! else context,
                        R.drawable.ic_apple_play
                    )
                bottomSheetFullControllerButton.setTag(R.id.play_next, 2)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                instance?.playOrPause(); true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                instance?.seekToPreviousMediaItem(); true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                instance?.seekToNextMediaItem(); true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun dumpPlaylist(): MutableList<Pair<Int, MediaItem>> {
        val items = mutableListOf<Pair<Int, MediaItem>>()
        if (instance != null && instance!!.shuffleModeEnabled) {
            var i = instance!!.currentTimeline.getFirstWindowIndex(true)
            while (i != C.INDEX_UNSET) {
                items.add(Pair(i, instance!!.getMediaItemAt(i)))
                i = instance!!.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
            }
        } else if (instance != null) {
            for (i in 0 until instance!!.mediaItemCount) {
                items.add(Pair(i, instance!!.getMediaItemAt(i)))
            }
        }
        return items
    }

    private inner class LyricAdapter(
        private val lyricList: MutableList<MediaStoreUtils.Lyric>
    ) : RecyclerView.Adapter<LyricAdapter.ViewHolder>() {

        private var defaultTextColor =
            ResourcesCompat.getColor(resources, R.color.contrast_lyric_defaultColor, null)

        private var highlightTranslationTextColor =
            ResourcesCompat.getColor(
                resources,
                R.color.contrast_lyric_highlightTranslationColor,
                null
            )

        private var highlightTextColor =
            ResourcesCompat.getColor(resources, R.color.contrast_lyric_highlightColor, null)

        private val sizeFactor = 1f
        private val defaultSizeFactor = .97f

        var currentFocusPos = -1
        private var currentTranslationPos = -1
        private var isLyricCentered = false

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): LyricAdapter.ViewHolder =
            ViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.lyrics, parent, false),
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            onBindViewHolder(holder, position, mutableListOf())
        }

        override fun onBindViewHolder(
            holder: LyricAdapter.ViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {

            val lyric = lyricList[position]
            val isHighlightPayload =
                payloads.isNotEmpty() && (payloads[0] == LYRIC_SET_HIGHLIGHT || payloads[0] == LYRIC_REMOVE_HIGHLIGHT)

            with(holder.lyricCard) {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    activity.getPlayer()?.apply {
                        animationLock = true
                        seekTo(lyric.timeStamp)
                        if (!isPlaying) play()
                    }
                }
            }

            with(holder.lyricTextView) {
                fun applyBlurEffect(textPaint: Paint, blurRadius: Float, shouldApplyBlur: Boolean) {
                    val blurMaskFilter = if (shouldApplyBlur) BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL) else null
                    textPaint.maskFilter = blurMaskFilter
                }

                if (isBlurEnabled) {
                    this.setLayerType(LAYER_TYPE_HARDWARE, null)
                    visibility = if (lyric.content.isNotEmpty()) View.VISIBLE else View.GONE
                    text = lyric.content
                    gravity = if (isLyricCentered) Gravity.CENTER else Gravity.START
                    translationY = 0f

                    val blurRadius = 8f // Adjust blur radius as needed
                    val isHighlight = position == currentFocusPos || position == currentTranslationPos
                    val shouldApplyBlur = (!lyric.isTranslation || (lyric.isTranslation && !isHighlightPayload)) && !isHighlight

                    val textPaint = paint
                    applyBlurEffect(textPaint, blurRadius, shouldApplyBlur)

                    val textSize = if (lyric.isTranslation) 20f else 34.25f
                    val paddingTop = if (lyric.isTranslation) 2 else 18
                    val paddingBottom =
                        if (position + 1 < lyricList.size && lyricList[position + 1].isTranslation) 2 else 18

                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                    setPadding(
                        (12.5f).dpToPx(context).toInt(),
                        paddingTop.dpToPx(context),
                        (12.5f).dpToPx(context).toInt(),
                        paddingBottom.dpToPx(context)
                    )
                    pivotX = 0f
                    pivotY = height.toFloat() / 2

                    when {
                        isHighlightPayload -> {
                            val targetScale =
                                if (payloads[0] == LYRIC_SET_HIGHLIGHT) sizeFactor else defaultSizeFactor
                            val targetColor =
                                if (payloads[0] == LYRIC_SET_HIGHLIGHT && lyric.isTranslation)
                                    highlightTranslationTextColor
                                else if (payloads[0] == LYRIC_SET_HIGHLIGHT)
                                    highlightTextColor
                                else
                                    defaultTextColor
                            animateText(targetScale, targetColor)
                        }

                        position == currentFocusPos -> {
                            scaleText(sizeFactor)
                            setTextColor(highlightTextColor)
                        }

                        position == currentTranslationPos -> {
                            scaleText(sizeFactor)
                            setTextColor(highlightTranslationTextColor)
                        }

                        else -> {
                            scaleText(defaultSizeFactor)
                            setTextColor(defaultTextColor)
                        }
                    }


            } else {
                    // If blur is not enabled, perform other operations without applying blur
                    setLayerType(LAYER_TYPE_NONE, null)
                    visibility = if (lyric.content.isNotEmpty()) View.VISIBLE else View.GONE
                    text = lyric.content
                    gravity = if (isLyricCentered) Gravity.CENTER else Gravity.START
                    translationY = 0f

                    val textSize = if (lyric.isTranslation) 20f else 34.25f
                    val paddingTop = if (lyric.isTranslation) 2 else 18
                    val paddingBottom =
                        if (position + 1 < lyricList.size && lyricList[position + 1].isTranslation) 2 else 18

                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                    setPadding(
                        (12.5f).dpToPx(context).toInt(),
                        paddingTop.dpToPx(context),
                        (12.5f).dpToPx(context).toInt(),
                        paddingBottom.dpToPx(context)
                    )
                    pivotX = 0f
                    pivotY = height.toFloat() / 2

                    when {
                        isHighlightPayload -> {
                            val targetScale =
                                if (payloads[0] == LYRIC_SET_HIGHLIGHT) sizeFactor else defaultSizeFactor
                            val targetColor =
                                if (payloads[0] == LYRIC_SET_HIGHLIGHT && lyric.isTranslation)
                                    highlightTranslationTextColor
                                else if (payloads[0] == LYRIC_SET_HIGHLIGHT)
                                    highlightTextColor
                                else
                                    defaultTextColor
                            animateText(targetScale, targetColor)
                        }

                        position == currentFocusPos -> {
                            scaleText(sizeFactor)
                            setTextColor(highlightTextColor)
                        }

                        position == currentTranslationPos -> {
                            scaleText(sizeFactor)
                            setTextColor(highlightTranslationTextColor)
                        }

                        else -> {
                            scaleText(defaultSizeFactor)
                            setTextColor(defaultTextColor)
                        }
                    }
                }
            }

        }

        private fun TextView.animateText(targetScale: Float, targetColor: Int) {
            
            val animator = ValueAnimator.ofFloat(scaleX, targetScale)
            animator.addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                if (scaleX != animatedValue) {
                    scaleX = animatedValue
                    scaleY = animatedValue
                }
            }
            animator.duration = LYRIC_SCROLL_DURATION
            animator.interpolator = interpolator
            animator.start()

            val colorAnimator = ValueAnimator.ofArgb(textColors.defaultColor, targetColor)
            colorAnimator.addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Int
                if (currentTextColor != animatedValue) {
                    setTextColor(animatedValue)
                }
            }
            colorAnimator.duration = LYRIC_SCROLL_DURATION
            colorAnimator.interpolator = interpolator
            colorAnimator.start()
        }


        private fun TextView.scaleText(scale: Float) {
            scaleX = scale
            scaleY = scale
        }

        override fun getItemCount(): Int = lyricList.size

        inner class ViewHolder(
            view: View
        ) : RecyclerView.ViewHolder(view) {
            val lyricTextView: TextView = view.findViewById(R.id.lyric)
            val lyricCard: MaterialCardView = view.findViewById(R.id.cardview)
        }

        fun updateHighlight(position: Int) {
            if (currentFocusPos == position) return

            if (position >= 0) {
                notifyItemChanged(currentFocusPos, LYRIC_REMOVE_HIGHLIGHT)
                currentFocusPos = position
                notifyItemChanged(currentFocusPos, LYRIC_SET_HIGHLIGHT)

                if (position + 1 < lyricList.size && lyricList[position + 1].isTranslation) {
                    notifyItemChanged(currentTranslationPos, LYRIC_REMOVE_HIGHLIGHT)
                    currentTranslationPos = position + 1
                    notifyItemChanged(currentTranslationPos, LYRIC_SET_HIGHLIGHT)
                } else if (currentTranslationPos != -1) {
                    notifyItemChanged(currentTranslationPos, LYRIC_REMOVE_HIGHLIGHT)
                    currentTranslationPos = -1
                }
            } else {
                currentFocusPos = -1
                currentTranslationPos = -1
            }
        }

    }


    private class PlaylistCardAdapter(
        private val activity: MainActivity
    ) : RecyclerView.Adapter<PlaylistCardAdapter.ViewHolder>() {

        private val playlist: MutableList<Pair<Int, MediaItem>> = mutableListOf()
        private lateinit var mRecyclerView: RecyclerView

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            mRecyclerView = recyclerView
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updatePlaylist(content: MutableList<Pair<Int, MediaItem>>) {
            playlist.clear()
            playlist.addAll(content)
            notifyDataSetChanged()
        }

        fun updatePlaylistWhenShuffle(content: MutableList<Pair<Int, MediaItem>>) {
            playlist.clear()
            playlist.addAll(content)
            notifyItemRangeRemoved(0, itemCount)
            notifyItemRangeInserted(0, itemCount)
            mRecyclerView.scrollToPosition(playlist.indexOfFirst { item ->
                item.first == (activity.getPlayer()?.currentMediaItemIndex ?: 0)
            })
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): PlaylistCardAdapter.ViewHolder =
            ViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.adapter_list_card_playlist, parent, false),
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.songName.text =
                playlist[holder.bindingAdapterPosition].second.mediaMetadata.title
            holder.songArtist.text =
                playlist[holder.bindingAdapterPosition].second.mediaMetadata.artist
            holder.songCover.load(playlist[position].second.mediaMetadata.artworkUri) {
                coolCrossfade(true)
                placeholder(R.drawable.ic_default_cover)
                error(R.drawable.ic_default_cover)
            }
            holder.closeButton.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                val instance = activity.getPlayer()
                val pos = playlist[holder.absoluteAdapterPosition].first
                playlist.removeAt(pos)
                notifyItemRemoved(pos)
                instance?.removeMediaItem(pos)
            }
            holder.itemView.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                val instance = activity.getPlayer()
                instance?.seekToDefaultPosition(playlist[holder.absoluteAdapterPosition].first)
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            holder.songCover.dispose()
        }

        override fun getItemCount(): Int = playlist.size

        inner class ViewHolder(
            view: View,
        ) : RecyclerView.ViewHolder(view) {
            val songName: TextView = view.findViewById(R.id.title)
            val songArtist: TextView = view.findViewById(R.id.artist)
            val songCover: ImageView = view.findViewById(R.id.cover)
            val closeButton: MaterialButton = view.findViewById(R.id.close)
        }

    }

    private fun updateNewIndex(): Int {
        val filteredList = bottomSheetFullLyricList.filterIndexed { _, lyric ->
            lyric.timeStamp <= (instance?.currentPosition ?: 0)
        }

        return if (filteredList.isNotEmpty()) {
            filteredList.indices.maxBy {
                filteredList[it].timeStamp
            }
        } else {
            -1
        }
    }

    fun setDelayedTransition() {
        blurTransitionJob?.cancel()
        blurTransitionJob = null
        blurTransitionJob = CoroutineScope(Dispatchers.Default)
        blurTransitionJob!!.launch {
            delay(5000)
            blurLock = false
        }
    }

    fun updateLyric(duration: Long?) {
        if (bottomSheetFullLyricList.isNotEmpty()) {
            val newIndex = updateNewIndex()

            if (newIndex != -1 &&
                duration != null &&
                newIndex != bottomSheetFullLyricAdapter.currentFocusPos
            ) {
                if (bottomSheetFullLyricList[newIndex].content.isNotEmpty()) {
                    val smoothScroller = createSmoothScroller(animationLock)
                    smoothScroller.targetPosition = newIndex
                    bottomSheetFullLyricLinearLayoutManager.startSmoothScroll(
                        smoothScroller
                    )
                    if (animationLock) animationLock = false
                }

                bottomSheetFullLyricAdapter.updateHighlight(newIndex)
            }
        }
    }

    private fun createSmoothScroller(noAnimation: Boolean = false): RecyclerView.SmoothScroller {
        return object : CustomSmoothScroller(context) {

            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int
            ): Int {
                return super.calculateDtToFit(
                    viewStart,
                    viewEnd,
                    boxStart,
                    boxEnd,
                    snapPreference
                ) + 72.dpToPx(context)
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }

            override fun calculateTimeForDeceleration(dx: Int): Int {
                return LYRIC_SCROLL_DURATION.toInt()
            }

            override fun afterTargetFound() {
                if (targetPosition > 1) {
                    val firstVisibleItemPosition: Int = targetPosition + 1
                    val lastVisibleItemPosition: Int =
                        bottomSheetFullLyricLinearLayoutManager.findLastVisibleItemPosition() + 1
                    for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
                        val view: View? =
                            bottomSheetFullLyricLinearLayoutManager.findViewByPosition(i)
                        if (view != null) {
                            if (!blurLock && isBlurEnabled) {
                                bottomSheetFullLyricAdapter.notifyItemChanged(i, LYRIC_UPDATE_BLUR)
                            }
                            if (i == targetPosition + 1 && bottomSheetFullLyricList[i].isTranslation) {
                                continue
                            }
                            if (!noAnimation) {
                                val ii = i - firstVisibleItemPosition -
                                        if (bottomSheetFullLyricList[i].isTranslation) 1 else 0
                                applyAnimation(view, ii)
                            }
                        }
                    }
                }
            }
        }
    }

    fun clearAllBlur() {
        val firstVisibleItemPosition: Int = bottomSheetFullLyricLinearLayoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition: Int =
            bottomSheetFullLyricLinearLayoutManager.findLastVisibleItemPosition() + 1
        for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
            val view: View? =
                bottomSheetFullLyricLinearLayoutManager.findViewByPosition(i)
            if (view != null) {
                bottomSheetFullLyricAdapter.notifyItemChanged(i, LYRIC_CLEAR_ALL)
            }
        }
    }
    

    private fun applyAnimation(view: View, ii: Int) {
        val depth = 15.dpToPx(context).toFloat()
        val duration = (LYRIC_SCROLL_DURATION * 0.278).toLong()
        val durationReturn = (LYRIC_SCROLL_DURATION * 0.722).toLong()
        val durationStep = (LYRIC_SCROLL_DURATION * 0.1).toLong()
        val animator = ObjectAnimator.ofFloat(
            view,
            "translationY",
            0f,
            depth,
        )
        animator.setDuration(duration)
        animator.interpolator = PathInterpolator(0.96f, 0.43f, 0.72f, 1f)
        animator.doOnEnd {
            val animator1 = ObjectAnimator.ofFloat(
                view,
                "translationY",
                depth,
                0f
            )
            animator1.setDuration(durationReturn + ii * durationStep)
            animator1.interpolator = PathInterpolator(0.17f, 0f, -0.15f, 1f)
            animator1.start()
        }
        animator.start()
    }


    private val positionRunnable = object : Runnable {
        @SuppressLint("SetTextI18n")
        override fun run() {
            if (!runnableRunning) return
            val currentPosition = instance?.currentPosition
            val position =
                CalculationUtils.convertDurationToTimeStamp(currentPosition ?: 0)
            val duration = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
            if (duration != null && duration != 0L && !isUserTracking) {
                bottomSheetFullSlider.valueTo = duration.toFloat()
                bottomSheetFullSlider.value =
                    instance?.currentPosition?.toFloat()
                        .checkIfNegativeOrNullOrMaxedOut(bottomSheetFullSlider.valueTo)
                bottomSheetFullPosition.text = position
                bottomSheetFullPositionBack.text = bottomSheetFullPosition.text
                bottomSheetFullDuration.text =
                    '-' +
                            CalculationUtils.convertDurationToTimeStamp(
                                instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
                                    ?.minus((currentPosition ?: 0)) ?: 0
                            )
                bottomSheetFullDurationBack.text = bottomSheetFullDuration.text
            }
            if (duration != null && duration >= LYRIC_SCROLL_DURATION) {
                updateLyric(duration - LYRIC_SCROLL_DURATION)
            }
            if (instance?.isPlaying == true) {
                handler.postDelayed(this, SLIDER_UPDATE_INTERVAL)
            } else {
                runnableRunning = false
            }
        }
    }

    private fun resetToDefaultLyricPosition() {
        val smoothScroller = createSmoothScroller()
        smoothScroller.targetPosition = 0
        bottomSheetFullLyricLinearLayoutManager.startSmoothScroll(
            smoothScroller
        )
        bottomSheetFullLyricAdapter.updateHighlight(0)
    }

    inner class VolumeChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ((intent.action == VOLUME_CHANGED_ACTION ||
                    intent.action == Intent.ACTION_HEADSET_PLUG ||
                    intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                && !volumeLock) {
                val targetProgress =
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                val valueAnimator =
                    ValueAnimator.ofFloat(bottomSheetVolumeSlider.value, targetProgress)
                valueAnimator.apply {
                    addUpdateListener {
                        val value = animatedValue as Float
                        bottomSheetVolumeSlider.value = value
                        currentVolume = value.toInt()
                    }
                    duration = 100
                    interpolator = this@FullBottomSheet.interpolator
                    start()
                }
            }
        }
    }

    private fun manipulateTopOverlayVisibility(visibility: Int) {
        Log.d("TAG", "TOP_!")
        val targetColorPrimary =
            if (visibility == View.VISIBLE)
                ContextCompat.getColor(
                    context,
                    R.color.contrast_primaryOverlayColor
                )
            else
                Color.TRANSPARENT
        Log.d("TAG", "VISIBILITY: $visibility")
        bottomSheetFullSubtitleUnder.setTextColor(targetColorPrimary)
        bottomSheetStarButtonBackground.visibility = visibility
        bottomSheetMoreButtonBackground.visibility = visibility
        bottomSheetFullPlaylistSubtitleUnder.setTextColor(targetColorPrimary)
        bottomSheetStarButtonPlaylistBackground.visibility = visibility
        bottomSheetMoreButtonPlaylistBackground.visibility = visibility
    }

    private fun manipulateBottomOverlayVisibility(visibility: Int) {
        Log.d("TAG", "TOP_=")
        val targetColorPrimary =
            if (visibility == View.VISIBLE)
                ContextCompat.getColor(
                    context,
                    R.color.contrast_primaryOverlayColor
                )
            else
                Color.TRANSPARENT
        val targetColorSecondary =
            if (visibility == View.VISIBLE)
                ContextCompat.getColor(
                    context,
                    R.color.contrast_secondaryOverlayColor
                )
            else
                Color.TRANSPARENT
        bottomSheetVolumeSlider.setTrackColorActiveOverlay(targetColorPrimary)
        bottomSheetVolumeSlider.setTrackColorInactiveOverlay(targetColorSecondary)
        bottomSheetFullSlider.setTrackColorActiveOverlay(targetColorPrimary)
        bottomSheetFullSlider.setTrackColorInactiveOverlay(targetColorSecondary)
        bottomSheetFullDurationBack.setTextColor(targetColorSecondary)
        bottomSheetFullPositionBack.setTextColor(targetColorSecondary)
        bottomSheetVolumeStartOverlayImageView.visibility = visibility
        bottomSheetVolumeEndOverlayImageView.visibility = visibility
        bottomSheetQualityOverlay.visibility = visibility
        bottomSheetQualityFrame.visibility = visibility
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "lyric_blur") {
            isBlurEnabled = prefs.getBooleanStrict("lyric_blur", false)
            if (!isBlurEnabled) {
                clearAllBlur()
            } else {
                updateLyric(instance?.duration)
            }
        }
    }
}