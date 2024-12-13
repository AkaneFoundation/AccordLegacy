package org.akanework.gramophone.ui.components

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
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.AudioManager
import android.os.Bundle
import android.util.AttributeSet
import android.util.Size
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.Insets
import androidx.core.graphics.TypefaceCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.load
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.error
import coil3.request.placeholder
import coil3.size.Scale
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.animateText
import org.akanework.gramophone.logic.checkIfNegativeOrNullOrMaxedOut
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.fadInAnimation
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.getLyrics
import org.akanework.gramophone.logic.getTextViews
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.hasImagePermission
import org.akanework.gramophone.logic.hasScopedStorageV1
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.hasTimer
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.resetShader
import org.akanework.gramophone.logic.scaleText
import org.akanework.gramophone.logic.setTextAnimation
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.logic.ui.CustomSmoothScroller
import org.akanework.gramophone.logic.ui.coolCrossfade
import org.akanework.gramophone.logic.updateMargin
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.logic.utils.DatabaseUtils
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.MainActivity
import java.util.LinkedList
import kotlin.math.absoluteValue

@SuppressLint("SetTextI18n")
class FullBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes),
    Player.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

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
        const val LYRIC_REMOVE_BLUR = 2
        const val LYRIC_SET_BLUR = 3
        const val LYRIC_UPDATE = 4
        const val LYRIC_SCROLL_DURATION = 600L
        const val LYRIC_UPDATE_DURATION = 10L
        const val SHRINK_VALUE_DEFAULT = 0.93F
        const val ALBUM_SHRINK_DURATION_ANIMATION = 300L
        const val SHRINK_TRIGGER_DURATION = 300L
        const val SHRINK_VALUE_PAUSE = 0.85F
        const val BOTTOM_TRANSIT_DURATION = 100L
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val LYRIC_DEFAULT_SIZE = .98f
    }

    private fun buildShrinkAnimator(
        isShrink: Boolean = true,
        shrinkValue: Float = SHRINK_VALUE_DEFAULT,
        completion: (() -> Unit)? = null
    ) {
        if (bottomSheetFullCoverFrame.isVisible) {
            val scaleX = PropertyValuesHolder.ofFloat(
                SCALE_X,
                if (isShrink) 1f else shrinkValue,
                if (isShrink) shrinkValue else 1f
            )
            val scaleY = PropertyValuesHolder.ofFloat(
                SCALE_Y,
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
                updateLyric()
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
    private val bottomSheetFullLyricButtonUnder: MaterialButton
    private val bottomSheetPlaylistButtonUnder: MaterialButton
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
    private var startY = 0f
    private var isScrollingDown = false
    private var animationBroadcastLock = false
    private var queryFavouriteJob: CoroutineScope? = null
    private var favouriteLock = false
    private val audioManager: AudioManager
    private var volumeChangeReceiver: VolumeChangeReceiver
    private val volumeChangeReceiverIntentFilter: IntentFilter
    var fingerReleaseJob: Job? = null
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
    var isFingerOnScreen: Boolean = false
    var blurLock: Boolean = false

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
        bottomSheetFullLyricButtonUnder = findViewById(R.id.lyric_btn_under)
        bottomSheetPlaylistButtonUnder = findViewById(R.id.playlist_under)

        bottomSheetFullPlaylistAdapter = PlaylistCardAdapter(activity)
        val callback: ItemTouchHelper.Callback =
            PlaylistCardMoveCallback(bottomSheetFullPlaylistAdapter::onRowMoved)
        val touchHelper = ItemTouchHelper(callback)
        bottomSheetFullPlaylistRecyclerView.layoutManager = LinearLayoutManager(context)
        bottomSheetFullPlaylistRecyclerView.adapter = bottomSheetFullPlaylistAdapter
        touchHelper.attachToRecyclerView(bottomSheetFullPlaylistRecyclerView)

        bottomSheetFullPlaylistSubtitleUnder.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetFullSubtitleUnder.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)

        bottomSheetFullDurationBack.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetFullPositionBack.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetQualityOverlay.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetQualityFrame.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)

        bottomSheetVolumeStartOverlayImageView.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetVolumeEndOverlayImageView.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetStarButtonBackground.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetStarButtonPlaylistBackground.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetMoreButtonBackground.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetMoreButtonPlaylistBackground.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetFullLyricButtonUnder.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)
        bottomSheetPlaylistButtonUnder.setLayerType(LAYER_TYPE_HARDWARE, overlayPaint)

        prefs.registerOnSharedPreferenceChangeListener(this)

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
                val mediaId = instance?.currentMediaItem?.mediaId?.toLong()
                    ?: return@addOnCheckedChangeListener
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
                        DatabaseUtils.removeFavouriteSong(
                            mediaId, activity.libraryViewModel, context
                        )
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
                val mediaId = instance?.currentMediaItem?.mediaId?.toLong()
                    ?: return@addOnCheckedChangeListener
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
                        DatabaseUtils.removeFavouriteSong(
                            mediaId, activity.libraryViewModel, context
                        )
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
            bottomSheetPlaylistButtonUnder.isChecked = isChecked
            if (triggerLock) {
                triggerLock = false
                return@addOnCheckedChangeListener
            }
            if (isChecked && !bottomSheetFullLyricButton.isChecked) {
                changeMovableFrame(false)
                bottomSheetFullPlaylistRecyclerView.scrollToPosition(
                    bottomSheetFullPlaylistAdapter.playlist.first.indexOfFirst { i ->
                        i == (instance?.currentMediaItemIndex ?: 0)
                    }
                )
                isPlaylistEnabled = true
                bottomSheetFullHeaderFrame.fadInAnimation(interpolator, VIEW_TRANSIT_DURATION) {
                    manipulateTopOverlayVisibility(VISIBLE)
                    playlistCoverCoordinateX =
                        bottomSheetFullPlaylistCoverFrame.left + bottomSheetFullHeaderFrame.left
                    playlistCoverCoordinateY =
                        bottomSheetFullPlaylistCoverFrame.top + bottomSheetFullHeaderFrame.top
                    playlistCoverScale =
                        bottomSheetFullPlaylistCoverFrame.height / 48.dpToPx(context).toFloat() - 1f
                }
                bottomSheetFullPlaylistFrame.fadInAnimation(interpolator, VIEW_TRANSIT_DURATION)
                bottomSheetFullBlendView?.animateBlurRadius(false, VIEW_TRANSIT_DURATION)
            } else if (bottomSheetFullLyricButton.isChecked) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                triggerLock = true
                bottomSheetFullLyricButton.isChecked = false
                bottomSheetFadingVerticalEdgeLayout.changeOverlayVisibility(false)
                bottomSheetFadingVerticalEdgeLayout.fadOutAnimation(
                    interpolator,
                    VIEW_TRANSIT_DURATION
                )
                bottomSheetFullPlaylistFrame.fadInAnimation(interpolator, VIEW_TRANSIT_DURATION)
                hideJob?.cancel()
                if (bottomSheetFullControllerButton.visibility == GONE || bottomSheetFullControllerButton.visibility == INVISIBLE) {
                    showEveryController()
                }
            } else {
                changeMovableFrame(true)
                isPlaylistEnabled = false
                bottomSheetFullHeaderFrame.fadOutAnimation(
                    interpolator, VIEW_TRANSIT_DURATION, GONE
                )
                bottomSheetFullPlaylistFrame.fadOutAnimation(
                    interpolator, VIEW_TRANSIT_DURATION, GONE
                )
                bottomSheetFullBlendView?.animateBlurRadius(true, VIEW_TRANSIT_DURATION)
            }
        }

        bottomSheetFullLyricButton.addOnCheckedChangeListener { _, isChecked ->
            bottomSheetFullLyricButtonUnder.isChecked = isChecked
            if (triggerLock) {
                triggerLock = false
                return@addOnCheckedChangeListener
            }
            if (isChecked && !bottomSheetPlaylistButton.isChecked) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                changeMovableFrame(false)
                isPlaylistEnabled = true
                bottomSheetFullHeaderFrame.fadInAnimation(interpolator, VIEW_TRANSIT_DURATION) {
                    manipulateTopOverlayVisibility(VISIBLE)
                    playlistCoverCoordinateX =
                        bottomSheetFullPlaylistCoverFrame.left + bottomSheetFullHeaderFrame.left
                    playlistCoverCoordinateY =
                        bottomSheetFullPlaylistCoverFrame.top + bottomSheetFullHeaderFrame.top
                    playlistCoverScale =
                        bottomSheetFullPlaylistCoverFrame.height / 48.dpToPx(context).toFloat() - 1f
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
                bottomSheetFadingVerticalEdgeLayout.fadInAnimation(
                    interpolator, VIEW_TRANSIT_DURATION
                ) {
                    bottomSheetFadingVerticalEdgeLayout.changeOverlayVisibility(true)
                }
                hideControllerJob()
                bottomSheetFullBlendView?.animateBlurRadius(false, VIEW_TRANSIT_DURATION)
            } else if (bottomSheetPlaylistButton.isChecked) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                triggerLock = true
                bottomSheetPlaylistButton.isChecked = false
                bottomSheetFullPlaylistFrame.fadOutAnimation(interpolator, VIEW_TRANSIT_DURATION)
                bottomSheetFadingVerticalEdgeLayout.setPadding(
                    bottomSheetFadingVerticalEdgeLayout.paddingLeft,
                    bottomSheetFadingVerticalEdgeLayout.paddingTop,
                    bottomSheetFadingVerticalEdgeLayout.paddingRight,
                    if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                        getDistanceToBottom(bottomSheetFullSlider)
                    else
                        0
                )
                bottomSheetFadingVerticalEdgeLayout.fadInAnimation(
                    interpolator, VIEW_TRANSIT_DURATION
                ) {
                    bottomSheetFadingVerticalEdgeLayout.changeOverlayVisibility(true)
                }
                hideControllerJob()
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                changeMovableFrame(true)
                isPlaylistEnabled = false
                bottomSheetFullHeaderFrame.fadOutAnimation(
                    interpolator, VIEW_TRANSIT_DURATION, GONE
                )
                bottomSheetFadingVerticalEdgeLayout.changeOverlayVisibility(false)
                bottomSheetFadingVerticalEdgeLayout.fadOutAnimation(
                    interpolator, VIEW_TRANSIT_DURATION, GONE
                )

                hideJob?.cancel()
                if (bottomSheetFullControllerButton.visibility == GONE || bottomSheetFullControllerButton.visibility == INVISIBLE) {
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

        bottomSheetStarButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        bottomSheetStarButtonPlaylist.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        bottomSheetFullLyricButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        bottomSheetPlaylistButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        bottomSheetFullLyricRecyclerView.layoutManager = bottomSheetFullLyricLinearLayoutManager
        bottomSheetFullLyricRecyclerView.adapter = bottomSheetFullLyricAdapter
        bottomSheetFullLyricRecyclerView.addItemDecoration(LyricPaddingDecoration(context))
        bottomSheetFullLyricRecyclerView.addOnItemTouchListener(object :
            RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = e.y
                        if (!animationBroadcastLock && e.y >= rv.measuredHeight / 4 * 3 &&
                            bottomSheetFullControllerButton.visibility != VISIBLE
                        ) {
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
                            animator.duration = BOTTOM_TRANSIT_DURATION
                            animator.start()
                            hideControllerJob()
                            return true
                        } else {
                            isFingerOnScreen = true
                            blurLock = true
                            clearBlur()
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        fingerReleaseJob?.cancel()
                        fingerReleaseJob = CoroutineScope(Dispatchers.Default).launch {
                            isFingerOnScreen = false
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val currentY = e.y
                        isScrollingDown = currentY < startY
                        if (!animationBroadcastLock && !isScrollingDown && bottomSheetFullControllerButton.visibility != VISIBLE) {
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
                            animator.duration = BOTTOM_TRANSIT_DURATION
                            animator.start()
                            hideControllerJob()
                        } else if (!animationBroadcastLock && isScrollingDown) {
                            animationBroadcastLock = true
                            hideJob?.cancel()
                            // Up
                            hideEveryController()
                            val animator = ValueAnimator.ofInt(
                                bottomSheetFadingVerticalEdgeLayout.paddingBottom, 0
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
                            animator.duration = BOTTOM_TRANSIT_DURATION
                            animator.start()
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
        val windowMetrics = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
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
                val animator = ValueAnimator.ofInt(
                    bottomSheetFadingVerticalEdgeLayout.paddingBottom, 0
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
                    if (hasScheduledShowJob) {
                        showEveryController()
                        hasScheduledShowJob = false
                    }
                }
                animator.duration = BOTTOM_TRANSIT_DURATION
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
        manipulateBottomOverlayVisibility(INVISIBLE)
        bottomSheetFullControllerFrame.fadOutAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
        bottomSheetFullControllerButton.fadOutAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
        bottomSheetVolumeSliderFrame.fadOutAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
        bottomSheetFullNextButton.fadOutAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
        bottomSheetFullPreviousButton.fadOutAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
        bottomSheetActionBar.fadOutAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
    }

    private fun showEveryController() {
        bottomSheetFullControllerFrame.fadInAnimation(interpolator, BOTTOM_TRANSIT_DURATION) {
            manipulateBottomOverlayVisibility(VISIBLE)
        }
        bottomSheetFullControllerButton.fadInAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
        bottomSheetVolumeSliderFrame.fadInAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
        bottomSheetFullNextButton.fadInAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
        bottomSheetFullPreviousButton.fadInAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
        bottomSheetActionBar.fadInAnimation(interpolator, BOTTOM_TRANSIT_DURATION)
    }

    private fun isHires(boolean: Boolean) {
        if (!bottomSheetQualityCard.isVisible && boolean) {
            bottomSheetQualityCard.fadInAnimation(interpolator, VIEW_TRANSIT_DURATION)
        } else if (bottomSheetQualityCard.isVisible && !boolean) {
            bottomSheetQualityCard.fadOutAnimation(interpolator, VIEW_TRANSIT_DURATION)
        }
    }

    fun isCoverFrameElevated(): Boolean =
        bottomSheetFullCoverFrame.elevation == resources.getDimensionPixelSize(
            R.dimen.full_cover_elevation
        ).toFloat()

    fun applyElevation(remove: Boolean) {
        val animator = ValueAnimator.ofFloat(
            if (remove) resources.getDimensionPixelSize(R.dimen.full_cover_elevation).toFloat()
            else 0f,
            if (remove) 0f
            else resources.getDimensionPixelSize(R.dimen.full_cover_elevation).toFloat()
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
            manipulateTopOverlayVisibility(INVISIBLE)
            bottomSheetFullTextLayout.fadInAnimation(interpolator, VIEW_TRANSIT_DURATION) {
                manipulateTopOverlayVisibility(VISIBLE)
            }
            bottomSheetFullDragHandle.fadInAnimation(interpolator, VIEW_TRANSIT_DURATION)
            TransitionManager.beginDelayedTransition(this, transformOut)
            bottomSheetFullPlaylistCoverFrame.visibility = INVISIBLE
            bottomSheetFullCoverFrame.visibility = VISIBLE
        } else {
            if (bottomSheetFullCoverFrame.scaleX == 1.0f) {
                manipulateTopOverlayVisibility(INVISIBLE)
                bottomSheetFullTextLayout.fadOutAnimation(interpolator, VIEW_TRANSIT_DURATION)
                bottomSheetFullDragHandle.fadOutAnimation(interpolator, VIEW_TRANSIT_DURATION)
                TransitionManager.beginDelayedTransition(this, transformIn)
                bottomSheetFullPlaylistCoverFrame.visibility = VISIBLE
                bottomSheetFullCoverFrame.visibility = INVISIBLE
            } else {
                manipulateTopOverlayVisibility(INVISIBLE)
                bottomSheetFullTextLayout.fadOutAnimation(interpolator, VIEW_TRANSIT_DURATION)
                bottomSheetFullDragHandle.fadOutAnimation(interpolator, VIEW_TRANSIT_DURATION)
                buildShrinkAnimator(false, bottomSheetFullCoverFrame.scaleX) {
                    TransitionManager.beginDelayedTransition(this, transformIn)
                    bottomSheetFullPlaylistCoverFrame.visibility = VISIBLE
                    bottomSheetFullCoverFrame.visibility = INVISIBLE
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

                GramophonePlaybackService.SERVICE_GET_LYRICS -> {
                    val parsedLyrics = instance?.getLyrics()
                    if (bottomSheetFullLyricList != parsedLyrics) {
                        bottomSheetFullLyricList.clear()
                        if (!parsedLyrics.isNullOrEmpty()) {
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
            onShuffleModeEnabledChanged(instance?.shuffleModeEnabled == true)
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
                interpolator = interpolator,
                skipAnimation = firstTime,
            )
            bottomSheetFullPlaylistTitle.setTextAnimation(
                mediaItem?.mediaMetadata?.title,
                interpolator = interpolator,
                skipAnimation = firstTime
            )
            bottomSheetFullSubtitle.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                interpolator = interpolator,
                skipAnimation = firstTime
            )
            bottomSheetFullSubtitleUnder.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                interpolator = interpolator,
                skipAnimation = firstTime
            )
            bottomSheetFullPlaylistSubtitle.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                interpolator = interpolator,
                skipAnimation = firstTime
            )
            bottomSheetFullPlaylistSubtitleUnder.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                interpolator = interpolator,
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
            suddenUpdate()
            isFingerOnScreen = false
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
            bottomSheetFullSlider.value = instance?.currentPosition?.toFloat().checkIfNegativeOrNullOrMaxedOut(bottomSheetFullSlider.valueTo)
            bottomSheetFullPosition.text = position
            bottomSheetFullPositionBack.text = bottomSheetFullPosition.text
            bottomSheetFullDuration.text = '-' + CalculationUtils.convertDurationToTimeStamp(
                instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")?.minus((currentPosition ?: 0)) ?: 0
            )
            bottomSheetFullDurationBack.text = bottomSheetFullDuration.text
        }
        if (duration != null) {
            updateLyric()
        }
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
        bottomSheetFullPlaylistAdapter.isShuffleEvent = true
        if (!shuffleModeEnabled) {
            bottomSheetFullPlaylistAdapter.updatePlaylist(
                dumpPlaylist()
            )
        }
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

    private fun dumpPlaylist(): Pair<MutableList<Int>, MutableList<MediaItem>> {
        val items = LinkedList<MediaItem>()
        for (i in 0 until instance!!.mediaItemCount) {
            items.add(instance!!.getMediaItemAt(i))
        }
        val indexes = LinkedList<Int>()
        val s = instance!!.shuffleModeEnabled
        var i = instance!!.currentTimeline.getFirstWindowIndex(s)
        while (i != C.INDEX_UNSET) {
            indexes.add(i)
            i = instance!!.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, s)
        }
        return Pair(indexes, items)
    }

    private inner class LyricAdapter(
        private val lyricList: MutableList<MediaStoreUtils.Lyric>
    ) : RecyclerView.Adapter<LyricAdapter.ViewHolder>() {

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return lyricList[position].hashCode().toLong()
        }

        private var defaultTextColor = ResourcesCompat.getColor(
            resources, R.color.contrast_lyric_defaultColor, null
        )

        private var highlightTranslationTextColor = ResourcesCompat.getColor(
            resources, R.color.contrast_lyric_highlightTranslationColor, null
        )

        private var highlightTextColor = ResourcesCompat.getColor(
            resources, R.color.contrast_lyric_highlightColor, null
        )

        private var disabledTextColor = ResourcesCompat.getColor(
            resources, R.color.contrast_lyric_disabledColor, null
        )

        private val sizeFactor = 1f

        private val defaultTypeface = TypefaceCompat.create(context, null, 700, false)

        private val disabledTextTypeface = TypefaceCompat.create(context, null, 500, false)

        private val extraLineHeight = resources.getDimensionPixelSize(
            R.dimen.lyric_extra_line_height
        )

        var currentHighlightLyricPositions: MutableList<Int> = mutableListOf()
        var currentFocusLyricPosition = -1
        var ignoredPositionAtMost = -1

        val isExtendedLRC: Boolean get() = lyricList.any { it.wordTimestamps.isNotEmpty() }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.lyrics, parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            onBindViewHolder(holder, position, mutableListOf())
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            val hasUpdateLyricPayload = payloads.contains(LYRIC_UPDATE)
            val hasSetHighlightPayload = payloads.contains(LYRIC_SET_HIGHLIGHT)
            val hasRemoveHighlightPayload = payloads.contains(LYRIC_REMOVE_HIGHLIGHT)
            val hasHighlightPayload = hasSetHighlightPayload || hasRemoveHighlightPayload

            if (hasUpdateLyricPayload) {
                holder.lyricFlexboxLayout.children.getTextViews {
                    if (it is CustomTextView) {
                        val currentPosition: Long = instance?.currentPosition ?: 0
                        val percent: Float = ((currentPosition.toFloat() - it.durationStart.toFloat()) / (it.durationEnd.toFloat() - it.durationStart.toFloat()))
//                        Log.d("Percent", "$percent, $currentPosition")
                        it.setProgress(percent)
                    }
                }
                if (payloads.size == 1) return
            }

            val hasSetBlurPayload = payloads.contains(LYRIC_SET_BLUR)
            val hasRemoveBlurPayload = payloads.contains(LYRIC_REMOVE_BLUR)
            val hasBlurPayload = hasSetBlurPayload || hasRemoveBlurPayload

            if (
                hasBlurPayload &&
                lyricList.isNotEmpty() &&
                !blurLock
            ) {
                val value = if (hasSetBlurPayload) getBlurRadius(position) else 0f
                with(ValueAnimator.ofFloat(holder.blurRadius, value)) {
                    duration = LYRIC_SCROLL_DURATION
                    interpolator = interpolator
                    addUpdateListener {
                        val value = animatedValue as Float
                        holder.blurRadius = value
                        holder.lyricCard.setRenderEffect(
                            if (holder.blurRadius != 0F) {
                                RenderEffect.createBlurEffect(
                                    holder.blurRadius,
                                    holder.blurRadius,
                                    Shader.TileMode.MIRROR
                                )
                            } else {
                                null
                            }
                        )
                    }
                    start()
                }
                if (!hasHighlightPayload) return
            }

            val lyric = lyricList[position]
            val hasMultiSpeaker = lyricList.any {
                it.label == LrcUtils.SpeakerLabel.Voice2 || it.label == LrcUtils.SpeakerLabel.Female
            }
            val currentLyricIsAnotherSpeaker = lyric.label == LrcUtils.SpeakerLabel.Voice2 || lyric.label == LrcUtils.SpeakerLabel.Female
            val lastLyricIsAnotherSpeaker = lyricList.getOrNull(position - 1)?.label == LrcUtils.SpeakerLabel.Voice2 || lyricList.getOrNull(position - 1)?.label == LrcUtils.SpeakerLabel.Female
            val currentLyricIsBgSpeaker = lyric.label == LrcUtils.SpeakerLabel.Background

            with(holder.lyricCard) {
                if (lyric.startTimestamp != null) {
                    isFocusable = true
                    isClickable = true
                } else {
                    isFocusable = false
                    isClickable = false
                }
                lyric.startTimestamp?.let { timestamp ->
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        activity.getPlayer()?.apply {
                            animationLock = true
                            ignoredPositionAtMost = if (currentLyricIsBgSpeaker) {
                                lyricList.indexOf(lyric) - 1
                            } else {
                                lyricList.indexOf(lyric)
                            }
                            seekTo(timestamp)
                            if (!isPlaying) play()
                        }
                    }
                }
                if (lyric.startTimestamp != null &&
                    lyric.absolutePosition != null &&
                    payloads.isEmpty() &&
                    !blurLock
                ) {
                    holder.blurRadius = getBlurRadius(position)
                    setRenderEffect(
                        if (holder.blurRadius != 0F) {
                            RenderEffect.createBlurEffect(
                                holder.blurRadius,
                                holder.blurRadius,
                                Shader.TileMode.MIRROR
                            )
                        } else {
                            null
                        }
                    )
                } else if (blurLock) {
                    holder.blurRadius = 0F
                    setRenderEffect(null)
                }
            }

            if (lyric.translationContent.isNotEmpty()) {
                with(holder.transitionFrame) {
                    visibility = VISIBLE
                    translationY = 0f
                    pivotX = if (currentLyricIsAnotherSpeaker || (currentLyricIsBgSpeaker && lastLyricIsAnotherSpeaker)) width / 1f else 0f
                    pivotY = height / 2f
                    val paddingStart = 12.5f
                    val paddingEnd = if (hasMultiSpeaker) 66.5f else 12.5f
                    updatePaddingRelative(
                        start = paddingStart.dpToPx(context).toInt(),
                        end = paddingEnd.dpToPx(context).toInt()
                    )
                }
                with(holder.transitionTextView) {
                    text = lyric.translationContent
                    typeface =
                        if (lyric.startTimestamp != null) defaultTypeface else disabledTextTypeface
                    setLineSpacing(
                        if (lyric.startTimestamp != null) 0f else extraLineHeight.toFloat(),
                        1f
                    )
                }
            } else {
                with(holder.transitionFrame) {
                    visibility = GONE
                }
                with(holder.transitionTextView) {
                    text = null
                }
            }

            with(holder.lyricFlexboxLayout) {
                visibility = if (lyric.content.isNotEmpty()) VISIBLE else GONE
                justifyContent =
                    if (currentLyricIsAnotherSpeaker || (currentLyricIsBgSpeaker && lastLyricIsAnotherSpeaker))
                        JustifyContent.FLEX_END
                    else
                        JustifyContent.FLEX_START

                translationY = 0f
                pivotX = if (currentLyricIsAnotherSpeaker || (currentLyricIsBgSpeaker && lastLyricIsAnotherSpeaker)) width / 1f else 0f
                pivotY = height / 2f

                val paddingTop =
                    if (lyric.startTimestamp != null) 18
                    else 0
                val paddingBottom =
                    if (lyric.translationContent.isNotEmpty()) 2
                    else if (lyric.startTimestamp != null) 18
                    else 0
                val paddingStart = 12.5f
                val paddingEnd = if (hasMultiSpeaker) 66.5f else 12.5f
                updatePaddingRelative(
                    start = paddingStart.dpToPx(context).toInt(),
                    top = paddingTop.dpToPx(context),
                    end = paddingEnd.dpToPx(context).toInt(),
                    bottom = paddingBottom.dpToPx(context)
                )

                // Add TextViews
                if (lyric.wordTimestamps.isNotEmpty()) {
                    // Remove old views
                    if (lyric.wordTimestamps.size != childCount) removeAllViews()
                    if (childCount > 0) {
                        with(children.first()) {
                            if (this !is CustomTextView) {
                                removeAllViews()
                            } else if (contentHash != lyric.hashCode()) {
                                removeAllViews()
                            }
                        }
                    }

                    // Add new views after check
                    var wordIndex = 0
                    lyric.wordTimestamps.forEach {
                        val lyricContent = lyric.content.substring(wordIndex, it.first)
                        val lyricShaderColor =
                            if (currentLyricIsBgSpeaker)
                                intArrayOf(
                                    highlightTranslationTextColor,
                                    highlightTranslationTextColor,
                                    highlightTranslationTextColor,
                                    highlightTranslationTextColor,
                                    defaultTextColor
                                )
                            else
                                intArrayOf(
                                    highlightTextColor,
                                    highlightTextColor,
                                    highlightTextColor,
                                    highlightTextColor,
                                    defaultTextColor
                                )
                        val lyricTextView = CustomTextView(
                            context = context,
                            colors = lyricShaderColor,
                            durationStart = it.second,
                            durationEnd = it.third,
                            contentHash = lyric.hashCode()
                        ).apply {
                            text = lyricContent

                            val textSize = if (currentLyricIsBgSpeaker) 23f else if (lyric.startTimestamp != null) 34f else 18f
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                            typeface =
                                if (lyric.startTimestamp != null) defaultTypeface else disabledTextTypeface
                            setLineSpacing(
                                if (lyric.startTimestamp != null) 0f else extraLineHeight.toFloat(),
                                1f
                            )
                        }
                        if (lyric.wordTimestamps.size != childCount) {
                            addView(lyricTextView)
                        }
                        wordIndex = it.first
                    }
                } else {
                    // Remove old views
                    if (childCount > 0) {
                        children.getTextViews {
                            if (it.text != lyric.content) removeAllViews()
                        }
                    }

                    // Add view if no view found
                    if (childCount < 1) addView(
                        AppCompatTextView(context).apply {
                            text = lyric.content
                            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                            val textSize = if (currentLyricIsBgSpeaker) 23f else if (lyric.startTimestamp != null) 34f else 18f
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                            typeface =
                                if (lyric.startTimestamp != null) defaultTypeface else disabledTextTypeface
                            setLineSpacing(
                                if (lyric.startTimestamp != null) 0f else extraLineHeight.toFloat(),
                                1f
                            )
                        }
                    )
                }

                children.getTextViews {
                    with(it) {
                        visibility = if (lyric.content.isNotEmpty()) VISIBLE else GONE

                        if (lyric.startTimestamp == null) {
                            if (it !is CustomTextView) {
                                setTextColor(disabledTextColor)
                            }
                            return@with
                        }
                    }
                }

                // Highlight Stuffs for lyrics
                when {
                    hasHighlightPayload -> {
                        scaleText(
                            if (hasSetHighlightPayload) sizeFactor else LYRIC_DEFAULT_SIZE,
                            interpolator
                        )
                        children.getTextViews {
                            if (it is CustomTextView) {
                                if (hasRemoveHighlightPayload) it.resetShader(interpolator)
                            } else {
                                val targetColor =
                                    if (hasSetHighlightPayload)
                                        highlightTextColor
                                    else
                                        defaultTextColor
                                it.animateText(targetColor, interpolator)
                            }
                        }
                    }

                    currentHighlightLyricPositions.contains(position) -> {
                        scaleText(sizeFactor, interpolator)
                        children.getTextViews { view ->
                            if (view !is CustomTextView) view.setTextColor(highlightTextColor)
                        }
                    }

                    else -> {
                        if (scaleX != LYRIC_DEFAULT_SIZE && scaleY != LYRIC_DEFAULT_SIZE)
                            scaleText(LYRIC_DEFAULT_SIZE, interpolator)
                        children.getTextViews {
                            if (it !is CustomTextView) it.setTextColor(defaultTextColor)
                        }
                    }
                }
            }

            // Highlight Stuffs for translations
            if (lyric.translationContent.isNotEmpty()) {
                when {
                    hasHighlightPayload -> {
                        holder.transitionFrame.scaleText(
                            if (hasSetHighlightPayload) sizeFactor else LYRIC_DEFAULT_SIZE,
                            interpolator
                        )
                        holder.transitionTextView.animateText(
                            if (hasSetHighlightPayload) highlightTranslationTextColor else defaultTextColor,
                            interpolator
                        )
                    }

                    currentHighlightLyricPositions.contains(position) -> {
                        holder.transitionFrame.scaleText(sizeFactor, interpolator)
                        holder.transitionTextView.setTextColor(highlightTranslationTextColor)
                    }

                    else -> {
                        holder.transitionFrame.scaleText(LYRIC_DEFAULT_SIZE, interpolator)
                        holder.transitionTextView.setTextColor(defaultTextColor)
                    }
                }
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            with(holder.lyricFlexboxLayout) {
                translationY = 0f
                scaleText(LYRIC_DEFAULT_SIZE)
            }
            holder.lyricFlexboxLayout.children.getTextViews {
                if (it !is CustomTextView) {
                    it.setTextColor(defaultTextColor)
                }
            }
            with(holder.transitionFrame) {
                translationY = 0f
                scaleText(LYRIC_DEFAULT_SIZE)
            }
            with(holder.transitionTextView) {
                setTextColor(defaultTextColor)
            }
            holder.blurRadius = 0F
            holder.lyricCard.setRenderEffect(null)
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = lyricList.size

        inner class ViewHolder(
            view: View
        ) : RecyclerView.ViewHolder(view) {
            val lyricFlexboxLayout: FlexboxLayout = view.findViewById(R.id.lyric_flexbox)
            val transitionTextView: TextView = view.findViewById(R.id.transition_text)
            val transitionFrame: LinearLayout = view.findViewById(R.id.translation_frame)
            val lyricCard: MaterialCardView = view.findViewById(R.id.lyric_cardview)
            var blurRadius: Float = 0F
        }

        fun updateHighlight(
            position: Int,
            remove: Boolean
        ) {
            if (remove && !currentHighlightLyricPositions.contains(position)) return
            if (!remove && currentHighlightLyricPositions.contains(position)) return
            if (position >= 0) {
                if (remove) {
                    currentHighlightLyricPositions.remove(position)
                } else {
                    currentHighlightLyricPositions.add(position)
                }
                val payloads = if (remove) LYRIC_REMOVE_HIGHLIGHT else LYRIC_SET_HIGHLIGHT
                notifyItemChanged(position, payloads)
            } else {
                currentHighlightLyricPositions.clear()
            }
        }

        fun getBlurRadius(position: Int): Float = 0f
        // TODO bring blur back
        /*
        {
            runCatching {
                val currentHighlightLyricPosition =
                    if (currentHighlightLyricPositions.isEmpty())
                        Pair(-1, -1)
                    else
                        Pair(currentHighlightLyricPositions.max(), currentHighlightLyricPositions.min())
                val radius = if (
                    currentHighlightLyricPosition == Pair(-1, -1) ||
                    currentHighlightLyricPositions.contains(position) ||
                    lyricList[position].absolutePosition == lyricList.getOrNull(currentHighlightLyricPosition.first)?.absolutePosition ||
                    lyricList[position].absolutePosition == null || lyricList.size < currentHighlightLyricPosition.first) {
                    0f
                } else if (position > currentHighlightLyricPosition.first) {
                    (lyricList[position].absolutePosition!! - lyricList[currentHighlightLyricPosition.first].absolutePosition!!)
                        .absoluteValue.toFloat().pow(2F).coerceAtMost(36F)
                } else if (position < currentHighlightLyricPosition.second) {
                    (lyricList[position].absolutePosition!! - lyricList[currentHighlightLyricPosition.second].absolutePosition!!)
                        .absoluteValue.toFloat().pow(2F).coerceAtMost(36F)
                } else {
                    throw IllegalArgumentException()
                }
                return radius
            }.onFailure { exception ->
                Log.d("getBlurRadius", Log.getStackTraceString(exception))
            }
            return 0F
        }
         */

            /*
        if (lyricList[position].absolutePosition == lyricList[currentFocusPos.first()].absolutePosition || lyricList[position].absolutePosition == null)
            0f
        else if (currentFocusPos.first() == 0)
            (lyricList[position].absolutePosition!! - 0).absoluteValue.toFloat().pow(2F).coerceAtMost(36F)
        else
            (lyricList[position].absolutePosition!! - lyricList[currentFocusPos.first()].absolutePosition!!).absoluteValue.toFloat().pow(2F).coerceAtMost(36F)
             */
    }

    private inner class PlaylistCardAdapter(
        private val activity: MainActivity
    ) : RecyclerView.Adapter<PlaylistCardAdapter.ViewHolder>() {

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return playlist.second[position].hashCode().toLong()
        }

        var playlist = Pair(mutableListOf<Int>(), mutableListOf<MediaItem>())
        var ignoreCount = 0
        var isShuffleEvent = false
        private lateinit var mRecyclerView: RecyclerView

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            mRecyclerView = recyclerView
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updatePlaylist(content: Pair<MutableList<Int>, MutableList<MediaItem>>) {
            if (ignoreCount > 0) {
                ignoreCount--
                return
            }
            if (isShuffleEvent) handleShuffleEvent()
            if (content == playlist) return
            playlist = content
            notifyDataSetChanged()
        }

        private fun handleShuffleEvent() {
            mRecyclerView.scrollToPosition(
                playlist.first.indexOf(activity.getPlayer()?.currentMediaItemIndex ?: 0)
            )
            isShuffleEvent = false
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder = ViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.adapter_list_card_playlist, parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = playlist.second[playlist.first[holder.bindingAdapterPosition]]
            holder.songName.text = item.mediaMetadata.title
            holder.songArtist.text = item.mediaMetadata.artist
            holder.songCover.load(item.mediaMetadata.artworkUri) {
                coolCrossfade(true)
                placeholder(R.drawable.ic_default_cover)
                error(R.drawable.ic_default_cover)
            }
            holder.closeButton.setOnClickListener { v ->
                holder.closeButton.setOnClickListener(null)
                holder.itemView.setOnClickListener(null)
                ViewCompat.performHapticFeedback(v, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
                val instance = activity.getPlayer()
                val pos = holder.bindingAdapterPosition
                val idx = playlist.first.removeAt(pos)
                playlist.first.replaceAll { if (it > idx) it - 1 else it }
                instance?.removeMediaItem(idx)
                notifyItemRemoved(pos)
            }
            holder.itemView.setOnClickListener {
                ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
                val instance = activity.getPlayer()
                instance?.seekToDefaultPosition(playlist.first[holder.absoluteAdapterPosition])
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            holder.songCover.dispose()
        }

        override fun getItemCount(): Int =
            if (playlist.first.size != playlist.second.size) throw IllegalStateException("${playlist.first.size}, ${playlist.second.size}")
            else playlist.first.size

        inner class ViewHolder(
            view: View,
        ) : RecyclerView.ViewHolder(view) {
            val songName: TextView = view.findViewById(R.id.title)
            val songArtist: TextView = view.findViewById(R.id.artist)
            val songCover: ImageView = view.findViewById(R.id.cover)
            val closeButton: MaterialButton = view.findViewById(R.id.close)
        }

        fun onRowMoved(from: Int, to: Int) {
            val mediaController = activity.getPlayer()
            val from1 = playlist.first.removeAt(from)
            playlist.first.replaceAll { if (it > from1) it - 1 else it }
            val movedItem = playlist.second.removeAt(from1)
            val to1 = if (to > 0) playlist.first[to - 1] + 1 else 0
            playlist.first.replaceAll { if (it >= to1) it + 1 else it }
            playlist.first.add(to, to1)
            playlist.second.add(to1, movedItem)
            ignoreCount++
            mediaController?.moveMediaItem(from1, to1)
            notifyItemMoved(from, to)
        }
    }

    private class PlaylistCardMoveCallback(
        private val touchHelperContract: (Int, Int) -> Unit
    ) : ItemTouchHelper.Callback() {

        override fun isLongPressDragEnabled(): Boolean {
            return true
        }

        override fun isItemViewSwipeEnabled(): Boolean {
            return false
        }

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val dragFlag = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlag, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            touchHelperContract(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            throw IllegalStateException()
        }
    }

    private fun getNewIndex(): List<Int> {
        val currentPosition = instance?.currentPosition ?: 0
        val filteredList = bottomSheetFullLyricList.filter { lyric ->
            (lyric.startTimestamp ?: 0) <= currentPosition && (lyric.endTimestamp ?: 0) >= currentPosition
        }.map { lyric ->
            bottomSheetFullLyricList.indexOf(lyric)
        }
//        Log.d("Index", filteredList.toString())

        return filteredList
    }

    fun updateLyric() {
        if (bottomSheetFullLyricList.isNotEmpty() && alpha > 0f) {

            val newIndex = getNewIndex()
            val fullList = (newIndex + bottomSheetFullLyricAdapter.currentHighlightLyricPositions).sorted().distinct()

            fullList.forEach {
                // Update extended lyric
                if (bottomSheetFullLyricAdapter.isExtendedLRC) {

                    /** Fix abnormal shader state when switch lyric line by click
                     * @see LyricAdapter.ignoredPositionAtMost
                     */
                    if (newIndex.contains(it) && it >= bottomSheetFullLyricAdapter.ignoredPositionAtMost) {
                        bottomSheetFullLyricAdapter.notifyItemChanged(it, LYRIC_UPDATE)
                    }
                }

                // Update highlight
                if (bottomSheetFullLyricAdapter.currentHighlightLyricPositions != newIndex) {

                    // Maybe we needn't update highlight for ignored lines
                    if (it >= bottomSheetFullLyricAdapter.ignoredPositionAtMost || !newIndex.contains(it)) {
                        bottomSheetFullLyricAdapter.updateHighlight(it, !newIndex.contains(it))
                    }
                }
            }

            // Get new target lyric position
            var targetFocusLyricPosition = newIndex.minOrNull()

            /** Fix abnormal smooth scroll when switch lyric line by click
             *  @see LyricAdapter.ignoredPositionAtMost
             */
            if (newIndex.size > 1) {
                targetFocusLyricPosition = newIndex.filter { i ->
                    i >= bottomSheetFullLyricAdapter.ignoredPositionAtMost
                }.minOrNull()
            } else {
                bottomSheetFullLyricAdapter.ignoredPositionAtMost = -1
            }

            // Smooth scroll & Update focus lyric position
            if (targetFocusLyricPosition != null) {
                if (bottomSheetFullLyricAdapter.currentHighlightLyricPositions.contains(targetFocusLyricPosition) &&
                    bottomSheetFullLyricAdapter.currentFocusLyricPosition != targetFocusLyricPosition &&
                    (bottomSheetFullLyricList.getOrNull(bottomSheetFullLyricAdapter.currentFocusLyricPosition)?.absolutePosition ?: 10721) != bottomSheetFullLyricList.getOrNull(targetFocusLyricPosition)?.absolutePosition &&
                    bottomSheetFullLyricList.getOrNull(targetFocusLyricPosition)?.label != LrcUtils.SpeakerLabel.Background
                ) {
                    if (bottomSheetFullLyricList[targetFocusLyricPosition].content.isNotEmpty() &&
                        !isFingerOnScreen
                    ) {
                        blurLock = false
                        val smoothScroller =
                            createSmoothScroller(animationLock || targetFocusLyricPosition == 0).apply {
                                targetPosition = targetFocusLyricPosition
                            }
                        bottomSheetFullLyricLinearLayoutManager.startSmoothScroll(smoothScroller)
                        bottomSheetFullLyricAdapter.currentFocusLyricPosition = targetFocusLyricPosition
                        if (animationLock) animationLock = false
                    }
                }
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

            override fun calculateTimeForScrolling(dx: Int): Int {
                return LYRIC_SCROLL_DURATION.toInt()
            }

            override fun afterTargetFound() {
                if (targetPosition > 1 && alpha > 0f) {
                    val firstVisibleItemPosition: Int =
                        bottomSheetFullLyricLinearLayoutManager.findFirstVisibleItemPosition() - 3
                    val lastVisibleItemPosition: Int =
                        bottomSheetFullLyricLinearLayoutManager.findLastVisibleItemPosition() + 3
                    for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
                        if (i > targetPosition) {
                            val view: View? =
                                bottomSheetFullLyricLinearLayoutManager.findViewByPosition(i)
                            if (view != null) {
                                if (!noAnimation &&
                                    bottomSheetFullLyricList[targetPosition].absolutePosition != null &&
                                    bottomSheetFullLyricList[i].absolutePosition != null
                                ) {
                                    val ii = (bottomSheetFullLyricList[i].absolutePosition!! - bottomSheetFullLyricList[targetPosition].absolutePosition!!).absoluteValue
                                    applyAnimation(view, ii)
                                }
                            }
                        }
                        bottomSheetFullLyricAdapter.notifyItemChanged(i, LYRIC_SET_BLUR)
                    }
                }
            }
        }
    }

    private fun suddenUpdate() {
        val firstVisibleItemPosition: Int =
            bottomSheetFullLyricLinearLayoutManager.findFirstVisibleItemPosition() - 3
        val lastVisibleItemPosition: Int =
            bottomSheetFullLyricLinearLayoutManager.findLastVisibleItemPosition() + 3
        for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
            bottomSheetFullLyricAdapter.notifyItemChanged(i, LYRIC_SET_BLUR)
        }
    }

    fun clearBlur() {
        val firstVisibleItemPosition: Int =
            bottomSheetFullLyricLinearLayoutManager.findFirstVisibleItemPosition() - 3
        val lastVisibleItemPosition: Int =
            bottomSheetFullLyricLinearLayoutManager.findLastVisibleItemPosition() + 3
        for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
            bottomSheetFullLyricAdapter.notifyItemChanged(i, LYRIC_REMOVE_BLUR)
        }
    }

    private val inComingInterpolator = PathInterpolator(0.96f, 0.43f, 0.72f, 1f)
    private val liftInterpolator = PathInterpolator(0.17f, 0f, -0.15f, 1f)

    private fun applyAnimation(view: View, ii: Int) {
        val depth = 15.dpToPx(context).toFloat()
        val duration = (LYRIC_SCROLL_DURATION * 0.278).toLong()
        val durationReturn = (LYRIC_SCROLL_DURATION * 0.722).toLong()
        val durationStep = (LYRIC_SCROLL_DURATION * 0.1).toLong()
        val lyricFlexboxLayout = view.findViewById<FlexboxLayout>(R.id.lyric_flexbox)
        val translationFrame = view.findViewById<LinearLayout>(R.id.translation_frame)
        val animator = ValueAnimator.ofFloat(0f, depth)
        animator.setDuration(duration)
        animator.interpolator = inComingInterpolator
        animator.addUpdateListener {
            val value = it.animatedValue as Float
            lyricFlexboxLayout.translationY = value
            translationFrame.translationY = value
        }
        animator.doOnEnd {
            lyricFlexboxLayout.translationY = depth
            translationFrame.translationY = depth

            val animator1 = ObjectAnimator.ofFloat(depth, 0f)
            animator1.setDuration(durationReturn + ii * durationStep)
            animator1.interpolator = liftInterpolator
            animator1.addUpdateListener {
                val value = it.animatedValue as Float
                lyricFlexboxLayout.translationY = value
                translationFrame.translationY = value
            }
            animator1.doOnEnd {
                with(lyricFlexboxLayout) {
                    translationY = 0f
                }
                with(translationFrame) {
                    translationY = 0f
                }
            }
            animator1.start()
        }
        animator.start()
    }


    private val positionRunnable = object : Runnable {
        @SuppressLint("SetTextI18n")
        override fun run() {
            if (!runnableRunning) return
            val currentPosition = instance?.currentPosition
            val position = CalculationUtils.convertDurationToTimeStamp(currentPosition ?: 0)
            val duration = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
            if (duration != null && duration != 0L && !isUserTracking) {
                bottomSheetFullSlider.valueTo = duration.toFloat()
                bottomSheetFullSlider.value = instance?.currentPosition?.toFloat().checkIfNegativeOrNullOrMaxedOut(bottomSheetFullSlider.valueTo)
                bottomSheetFullPosition.text = position
                bottomSheetFullPositionBack.text = bottomSheetFullPosition.text
                bottomSheetFullDuration.text = '-' + CalculationUtils.convertDurationToTimeStamp(
                    instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")?.minus((currentPosition ?: 0)) ?: 0
                )
                bottomSheetFullDurationBack.text = bottomSheetFullDuration.text
            }
            if (duration != null) {
                updateLyric()
            }
            if (instance?.isPlaying == true) {
                handler.postDelayed(
                    this,
                    if (bottomSheetFullLyricAdapter.isExtendedLRC) LYRIC_UPDATE_DURATION else SLIDER_UPDATE_INTERVAL
                )
            } else {
                runnableRunning = false
            }
        }
    }

    private fun resetToDefaultLyricPosition() {
        val smoothScroller = createSmoothScroller(true).apply {
            targetPosition = 0
        }
        bottomSheetFullLyricLinearLayoutManager.startSmoothScroll(
            smoothScroller
        )
        // TODO
        /*
        bottomSheetFullLyricAdapter.updateHighlight(0, true)
        bottomSheetFullLyricAdapter.notifyItemChanged(0)
         */
    }

    inner class VolumeChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ((intent.action == VOLUME_CHANGED_ACTION ||
                        intent.action == Intent.ACTION_HEADSET_PLUG ||
                        intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                && !volumeLock
            ) {
                val targetProgress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                val valueAnimator = ValueAnimator.ofFloat(bottomSheetVolumeSlider.value, targetProgress)
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
        val targetColorPrimary =
            if (visibility == VISIBLE)
                ContextCompat.getColor(
                    context,
                    R.color.contrast_primaryOverlayColor
                )
            else
                Color.TRANSPARENT
        // Note to self: Don't use visibility to textview because of sync
        bottomSheetFullSubtitleUnder.setTextColor(targetColorPrimary)
        bottomSheetStarButtonBackground.visibility = visibility
        bottomSheetMoreButtonBackground.visibility = visibility
        bottomSheetFullPlaylistSubtitleUnder.setTextColor(targetColorPrimary)
        bottomSheetStarButtonPlaylistBackground.visibility = visibility
        bottomSheetMoreButtonPlaylistBackground.visibility = visibility
    }

    private fun manipulateBottomOverlayVisibility(visibility: Int) {
        val targetColorPrimary =
            if (visibility == VISIBLE)
                ContextCompat.getColor(context, R.color.contrast_primaryOverlayColor)
            else
                Color.TRANSPARENT
        val targetColorSecondary =
            if (visibility == VISIBLE)
                ContextCompat.getColor(context, R.color.contrast_secondaryOverlayColor)
            else
                Color.TRANSPARENT
        bottomSheetVolumeSlider.setTrackColorActiveOverlay(targetColorPrimary)
        bottomSheetVolumeSlider.setTrackColorInactiveOverlay(targetColorSecondary)
        bottomSheetFullSlider.setTrackColorActiveOverlay(targetColorPrimary)
        bottomSheetFullSlider.setTrackColorInactiveOverlay(targetColorSecondary)
        // Note to self: Don't use visibility to textview because of sync
        bottomSheetFullDurationBack.setTextColor(targetColorSecondary)
        bottomSheetFullPositionBack.setTextColor(targetColorSecondary)
        bottomSheetVolumeStartOverlayImageView.visibility = visibility
        bottomSheetVolumeEndOverlayImageView.visibility = visibility
        bottomSheetQualityOverlay.visibility = visibility
        bottomSheetQualityFrame.visibility = visibility
        bottomSheetPlaylistButtonUnder.visibility = visibility
        bottomSheetFullLyricButtonUnder.visibility = visibility
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Do nothing for now
    }
}