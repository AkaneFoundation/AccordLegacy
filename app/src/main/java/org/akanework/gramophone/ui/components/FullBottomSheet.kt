package org.akanework.gramophone.ui.components

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fluidrecyclerview.widget.LinearLayoutManager
import androidx.fluidrecyclerview.widget.RecyclerView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.transition.TransitionManager
import coil3.annotation.ExperimentalCoilApi
import coil3.dispose
import coil3.imageLoader
import coil3.load
import coil3.request.ImageRequest
import coil3.request.error
import coil3.request.placeholder
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
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.fadInAnimation
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.getLyrics
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.hasTimer
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.setTextAnimation
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.logic.ui.coolCrossfade
import org.akanework.gramophone.logic.updateMargin
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.MainActivity
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

@SuppressLint("SetTextI18n")
class FullBottomSheet(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
	ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Player.Listener {
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

	val interpolator = DecelerateInterpolator()

	companion object {
		const val SLIDER_UPDATE_INTERVAL: Long = 100
		const val VIEW_TRANSIT_DURATION: Long = 350
		const val LYRIC_REMOVE_HIGHLIGHT: Int = 0
		const val LYRIC_SET_HIGHLIGHT: Int = 1
		const val LYRIC_TRANSIT_DURATION: Long = 500                     // Lyric size animate duration
		const val LYRIC_SMOOTH_SCROLL_MAX_DIST: Int = 250                // Lyric scroll 1st phase distance
		const val LYRIC_SMOOTH_SCROLL_MINIMUM_SCROLL_TIME: Int = 600     // Lyric scroll accelerate limit
		const val LYRIC_SMOOTH_SCROLL_SPEED_KEY: Float = 300f            // Lyric scroll general speed
		const val LYRIC_SMOOTH_SCROLL_SQUARE_FACTOR: Double = 1.06
		const val SHRINK_VALUE = 0.93f
		const val ALBUM_SHRINK_DURATION_ANIMATION = 300L

	}

	private fun buildShrinkAnimator(isShrink: Boolean = true) {
		val scaleX = PropertyValuesHolder.ofFloat(
			View.SCALE_X,
			if (isShrink) 1f else SHRINK_VALUE,
			if (isShrink) SHRINK_VALUE else 1f
		)
		val scaleY = PropertyValuesHolder.ofFloat(
			View.SCALE_Y,
			if (isShrink) 1f else SHRINK_VALUE,
			if (isShrink) SHRINK_VALUE else 1f
		)
		bottomSheetFullCoverFrame.apply {
			val animator = ObjectAnimator.ofPropertyValuesHolder(this, scaleX, scaleY)
			animator.duration = ALBUM_SHRINK_DURATION_ANIMATION
			animator.interpolator = DecelerateInterpolator()
			animator.start()
		}
	}

	private val touchListener = object : OverlaySlider.OnSliderTouchListener {
		override fun onStartTrackingTouch(slider: OverlaySlider) {
			isUserTracking = true
			buildShrinkAnimator()
		}

		override fun onStopTrackingTouch(slider: OverlaySlider) {
			val mediaId = instance?.currentMediaItem?.mediaId
			if (mediaId != null) {
				instance?.seekTo((slider.value.toLong()))
				updateLyric(slider.value.toLong())
			}
			isUserTracking = false
			buildShrinkAnimator(false)
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
	private val bottomSheetFullLyricLinearLayoutManager = LinearLayoutManager(context)
	private val bottomSheetFullDragHandle: BottomSheetDragHandleView
	private val bottomSheetFullTextLayout: LinearLayout
	private val bottomSheetFullHeaderFrame: ConstraintLayout
	private val bottomSheetFullPlaylistFrame: ConstraintLayout
	private val bottomSheetFullPlaylistCover: ImageView
	private val bottomSheetFullPlaylistTitle: TextView
	private val bottomSheetFullPlaylistSubtitle: TextView
	private val bottomSheetFullPlaylistSubtitleUnder: TextView
	private val bottomSheetFullPlaylistRecyclerView: RecyclerView
	private val bottomSheetFullPlaylistAdapter: PlaylistCardAdapter
	private val bottomSheetFullPlaylistCoverFrame: MaterialCardView
	private val bottomSheetActionBar: LinearLayout
	private var playlistNowPlaying: TextView? = null
	private var playlistNowPlayingCover: ImageView? = null
	private var triggerLock: Boolean = false
	var bottomSheetFullBlendBackgroundView: BlendBackgroundView? = null

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
		bottomSheetInfinityButton = findViewById(R.id.sheet_infinity)
		bottomSheetActionBar = findViewById(R.id.actionBar)

		bottomSheetFullPlaylistAdapter = PlaylistCardAdapter(activity)
		bottomSheetFullPlaylistRecyclerView.layoutManager = LinearLayoutManager(context)
		bottomSheetFullPlaylistRecyclerView.adapter = bottomSheetFullPlaylistAdapter

		bottomSheetFullSubtitle.paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
		bottomSheetFullPlaylistSubtitle.paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
		bottomSheetFullDurationBack.paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
		bottomSheetFullPositionBack.paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)

		ViewCompat.setOnApplyWindowInsetsListener(bottomSheetFullLyricRecyclerView) { v, insets ->
			val myInsets = insets.getInsets(
				WindowInsetsCompat.Type.systemBars()
					or WindowInsetsCompat.Type.displayCutout())
			v.updateMargin {
				left = -myInsets.left
				top = -myInsets.top
				right = -myInsets.right
				bottom = -myInsets.bottom
			}
			v.setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
			return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(insets)
				.setInsets(WindowInsetsCompat.Type.systemBars()
						or WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
				.setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
						or WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
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
				bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#33FFFFFF"))
				bottomSheetFullHeaderFrame.fadInAnimation(VIEW_TRANSIT_DURATION) {
					bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#CCFFFFFF"))
				}
				bottomSheetFullPlaylistFrame.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullBlendBackgroundView?.animateBlurRadius(false, VIEW_TRANSIT_DURATION)
			} else if (bottomSheetFullLyricButton.isChecked) {
				triggerLock = true
				bottomSheetFullLyricButton.isChecked = false
				bottomSheetFullLyricRecyclerView.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullControllerFrame.fadInAnimation(VIEW_TRANSIT_DURATION) {
					showSliderOverlay()
				}
				bottomSheetFullControllerButton.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullNextButton.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullPreviousButton.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullPlaylistFrame.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetActionBar.fadInAnimation(VIEW_TRANSIT_DURATION)
			} else {
				changeMovableFrame(true)
				bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#33FFFFFF"))
				bottomSheetFullHeaderFrame.fadOutAnimation(VIEW_TRANSIT_DURATION, View.GONE) {
					bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#CCFFFFFF"))
				}
				bottomSheetFullPlaylistFrame.fadOutAnimation(VIEW_TRANSIT_DURATION, View.GONE)
				bottomSheetFullBlendBackgroundView?.animateBlurRadius(true, VIEW_TRANSIT_DURATION)
			}
		}

		bottomSheetFullLyricButton.addOnCheckedChangeListener { _, isChecked ->
			if (triggerLock) {
				triggerLock = false
				return@addOnCheckedChangeListener
			}
			if (isChecked && !bottomSheetPlaylistButton.isChecked) {
				changeMovableFrame(false)
				bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#33FFFFFF"))
				bottomSheetFullHeaderFrame.fadInAnimation(VIEW_TRANSIT_DURATION) {
					bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#CCFFFFFF"))
				}
				bottomSheetFullLyricRecyclerView.fadInAnimation(VIEW_TRANSIT_DURATION)
				hideSliderOverlay()
				bottomSheetFullControllerFrame.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullControllerButton.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullNextButton.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullPreviousButton.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetActionBar.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullBlendBackgroundView?.animateBlurRadius(false, VIEW_TRANSIT_DURATION)
			} else if (bottomSheetPlaylistButton.isChecked) {
				triggerLock = true
				bottomSheetPlaylistButton.isChecked = false
				bottomSheetFullPlaylistFrame.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullControllerFrame.fadOutAnimation(VIEW_TRANSIT_DURATION) {
					hideSliderOverlay()
				}
				bottomSheetFullControllerButton.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullNextButton.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullPreviousButton.fadOutAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullLyricRecyclerView.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetActionBar.fadOutAnimation(VIEW_TRANSIT_DURATION)
			} else {
				changeMovableFrame(true)
				bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#33FFFFFF"))
				bottomSheetFullHeaderFrame.fadOutAnimation(VIEW_TRANSIT_DURATION, View.GONE) {
					bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#CCFFFFFF"))
				}
				bottomSheetFullLyricRecyclerView.fadOutAnimation(VIEW_TRANSIT_DURATION, View.GONE)
				bottomSheetFullControllerFrame.fadInAnimation(VIEW_TRANSIT_DURATION) {
					showSliderOverlay()
				}
				bottomSheetFullControllerButton.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullNextButton.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullPreviousButton.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetActionBar.fadInAnimation(VIEW_TRANSIT_DURATION)
				bottomSheetFullBlendBackgroundView?.animateBlurRadius(true, VIEW_TRANSIT_DURATION)
			}
		}

		bottomSheetFullControllerButton.setOnClickListener {
			it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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

	}

	fun hideSliderOverlay() {
		bottomSheetFullSlider.updateBottomTrackColor(Color.parseColor("#00FFFFFF"))
		bottomSheetFullSlider.trackInactiveTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
		bottomSheetFullDurationBack.setTextColor(Color.parseColor("#00FFFFFF"))
		bottomSheetFullDuration.setTextColor(Color.parseColor("#33FFFFFF"))
		bottomSheetFullPositionBack.setTextColor(Color.parseColor("#00FFFFFF"))
		bottomSheetFullPosition.setTextColor(Color.parseColor("#33FFFFFF"))
	}

	fun showSliderOverlay() {
		bottomSheetFullSlider.updateBottomTrackColor(Color.parseColor("#80FFFFFF"))
		bottomSheetFullSlider.trackInactiveTintList = ColorStateList.valueOf(Color.parseColor("#0AFFFFFF"))
		bottomSheetFullDurationBack.setTextColor(Color.parseColor("#80FFFFFF"))
		bottomSheetFullDuration.setTextColor(Color.parseColor("#0AFFFFFF"))
		bottomSheetFullPositionBack.setTextColor(Color.parseColor("#80FFFFFF"))
		bottomSheetFullPosition.setTextColor(Color.parseColor("#0AFFFFFF"))
	}

	fun hideSubtitleOverlay() {
		bottomSheetFullSubtitle.setTextColor(Color.parseColor("#33FFFFFF"))
		bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#33FFFFFF"))
	}

	fun showSubtitleOverlay() {
		bottomSheetFullSubtitle.setTextColor(Color.parseColor("#CCFFFFFF"))
		bottomSheetFullPlaylistSubtitle.setTextColor(Color.parseColor("#CCFFFFFF"))
	}

	fun isCoverFrameElevated(): Boolean = bottomSheetFullCoverFrame.elevation == 8.dpToPx(context).toFloat()

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
			bottomSheetFullTextLayout.fadInAnimation(VIEW_TRANSIT_DURATION) {
				bottomSheetFullSubtitle.setTextColor(Color.parseColor("#CCFFFFFF"))
			}
			bottomSheetFullDragHandle.fadInAnimation(VIEW_TRANSIT_DURATION) {}
			TransitionManager.beginDelayedTransition(this, transformOut)
			bottomSheetFullPlaylistCoverFrame.visibility = View.INVISIBLE
			bottomSheetFullCoverFrame.visibility = View.VISIBLE
		} else {
			bottomSheetFullSubtitle.setTextColor(Color.parseColor("#33FFFFFF"))
			bottomSheetFullTextLayout.fadOutAnimation(VIEW_TRANSIT_DURATION) {}
			bottomSheetFullDragHandle.fadOutAnimation(VIEW_TRANSIT_DURATION) {}
			TransitionManager.beginDelayedTransition(this, transformIn)
			bottomSheetFullPlaylistCoverFrame.visibility = View.VISIBLE
			bottomSheetFullCoverFrame.visibility = View.INVISIBLE
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
		val myInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars()
				or WindowInsetsCompat.Type.displayCutout())
		setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
		return WindowInsetsCompat.Builder(insets)
			.setInsets(WindowInsetsCompat.Type.systemBars()
					or WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
			.setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
					or WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
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
			context.imageLoader.enqueue(ImageRequest.Builder(context).apply {
				target(onSuccess = {
					bottomSheetFullCover.setImageDrawable(it.asDrawable(context.resources))
					bottomSheetFullPlaylistCover.setImageDrawable(it.asDrawable(context.resources))
				}, onError = {
					bottomSheetFullCover.setImageDrawable(it?.asDrawable(context.resources))
					bottomSheetFullPlaylistCover.setImageDrawable(it?.asDrawable(context.resources))
				}) // do not react to onStart() which sets placeholder
				data(mediaItem?.mediaMetadata?.artworkUri)
				error(R.drawable.ic_default_cover)
				coolCrossfade(true)
			}.build())
			bottomSheetFullTitle.setTextAnimation(mediaItem?.mediaMetadata?.title, skipAnimation = firstTime)
			bottomSheetFullPlaylistTitle.setTextAnimation(mediaItem?.mediaMetadata?.title, skipAnimation = firstTime)
			bottomSheetFullSubtitle.setTextAnimation(
				mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist), skipAnimation = firstTime,
				startWhenAnimating = {
					hideSubtitleOverlay()
				},
				completionWhenAnimating = {
					showSubtitleOverlay()
				}
			)
			bottomSheetFullSubtitleUnder.setTextAnimation(
				mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist), skipAnimation = firstTime
			)
			bottomSheetFullPlaylistSubtitle.setTextAnimation(
				mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist), skipAnimation = firstTime
			)
			bottomSheetFullPlaylistSubtitleUnder.setTextAnimation(
				mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist), skipAnimation = firstTime
			)
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
			bottomSheetFullCover.dispose()
			playlistNowPlayingCover?.dispose()
		}
		val currentPosition = instance?.currentPosition
		val position = CalculationUtils.convertDurationToTimeStamp(currentPosition ?: 0)
		val duration = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
		if (duration != null && !isUserTracking) {
			bottomSheetFullSlider.valueTo = duration.toFloat()
			bottomSheetFullSlider.value =
				min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
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
			Color.parseColor("#33FFFFFF")

		private var highlightTextColor =
			Color.parseColor("#FFFFFF")

		private val sizeFactor = 1.03f

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
		}

		override fun onBindViewHolder(
			holder: LyricAdapter.ViewHolder,
			position: Int,
			payloads: MutableList<Any>
		) {
			val lyric = lyricList[position]
			val isHighlightPayload = payloads.isNotEmpty() && (payloads[0] == LYRIC_SET_HIGHLIGHT || payloads[0] == LYRIC_REMOVE_HIGHLIGHT)

			with(holder.lyricCard) {
				setOnClickListener {
					performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
					activity.getPlayer()?.apply {
						if (!isPlaying) play()
						seekTo(lyric.timeStamp)
					}
				}
			}

			with(holder.lyricTextView) {
				visibility = if (lyric.content.isNotEmpty()) View.VISIBLE else View.GONE
				text = lyric.content
				gravity = if (isLyricCentered) Gravity.CENTER else Gravity.START

				val textSize = if (lyric.isTranslation) 20f else 32f
				val paddingTop = if (lyric.isTranslation) 2 else 18
				val paddingBottom = if (position + 1 < lyricList.size && lyricList[position + 1].isTranslation) 2 else 18

				setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
				setPadding(10.dpToPx(context), paddingTop.dpToPx(context), 10.dpToPx(context), paddingBottom.dpToPx(context))
				pivotX = 0f
				pivotY = height.toFloat() / 2

				when {
					isHighlightPayload -> {
						val targetScale = if (payloads[0] == LYRIC_SET_HIGHLIGHT) sizeFactor else 1f
						val targetColor = if (payloads[0] == LYRIC_SET_HIGHLIGHT) highlightTextColor else defaultTextColor
						animateText(targetScale, targetColor)
					}
					position == currentFocusPos || position == currentTranslationPos -> {
						scaleText(sizeFactor)
						setTextColor(highlightTextColor)
					}
					else -> {
						scaleText(1f)
						setTextColor(defaultTextColor)
					}
				}
			}
		}

		private fun TextView.animateText(targetScale: Float, targetColor: Int) {
			val animator = ValueAnimator.ofFloat(scaleX, targetScale)
			animator.addUpdateListener { animation ->
				val animatedValue = animation.animatedValue as Float
				scaleX = animatedValue
				scaleY = animatedValue
			}
			animator.duration = LYRIC_TRANSIT_DURATION
			animator.interpolator = interpolator
			animator.start()

			val colorAnimator = ValueAnimator.ofArgb(textColors.defaultColor, targetColor)
			colorAnimator.addUpdateListener { animation ->
				val animatedValue = animation.animatedValue as Int
				setTextColor(animatedValue)
			}
			colorAnimator.duration = LYRIC_TRANSIT_DURATION
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
				currentFocusPos.let {
					notifyItemChanged(it, LYRIC_REMOVE_HIGHLIGHT)
					currentFocusPos = position
					notifyItemChanged(currentFocusPos, LYRIC_SET_HIGHLIGHT)
				}

				if (position + 1 < lyricList.size &&
					lyricList[position + 1].isTranslation
				) {
					currentTranslationPos.let {
						notifyItemChanged(it, LYRIC_REMOVE_HIGHLIGHT)
						currentTranslationPos = position + 1
						notifyItemChanged(currentTranslationPos, LYRIC_SET_HIGHLIGHT)
					}
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

		fun getPlaylistSize() = playlist.size

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
			holder.songName.text = playlist[holder.bindingAdapterPosition].second.mediaMetadata.title
			holder.songArtist.text = playlist[holder.bindingAdapterPosition].second.mediaMetadata.artist
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

	fun updateLyric(duration: Long?) {
		if (bottomSheetFullLyricList.isNotEmpty()) {
			val newIndex: Int

			val filteredList = bottomSheetFullLyricList.filterIndexed { _, lyric ->
				lyric.timeStamp <= (instance?.currentPosition ?: 0)
			}

			newIndex = if (filteredList.isNotEmpty()) {
				filteredList.indices.maxBy {
					filteredList[it].timeStamp
				}
			} else {
				-1
			}

			if (newIndex != -1 &&
				duration != null &&
				newIndex != bottomSheetFullLyricAdapter.currentFocusPos
			) {
				val smoothScroller = createSmoothScroller()
				smoothScroller.targetPosition = newIndex
				bottomSheetFullLyricLinearLayoutManager.startSmoothScroll(
					smoothScroller
				)

				bottomSheetFullLyricAdapter.updateHighlight(newIndex)
			}
		}
	}

	private fun createSmoothScroller(): RecyclerView.SmoothScroller {
		val smoothScrollLimit = LYRIC_SMOOTH_SCROLL_MAX_DIST.dpToPx(context)
		val speedPerPixel = LYRIC_SMOOTH_SCROLL_SPEED_KEY / context.resources.displayMetrics.densityDpi

		return object : CustomLinearSmoothScroller(context) {

			override fun calculateDtToFit(
				viewStart: Int,
				viewEnd: Int,
				boxStart: Int,
				boxEnd: Int,
				snapPreference: Int
			): Int {
				return super.calculateDtToFit(viewStart, viewEnd, boxStart, boxEnd, snapPreference) + 36.dpToPx(context)
			}

			override fun getVerticalSnapPreference(): Int {
				return SNAP_TO_START
			}

			override fun calculateTimeForDeceleration(dx: Int): Int {
				val timeForScrolling = calculateTimeForScrolling(dx)
				val calculatedTime = ceil(timeForScrolling / .3356).toInt()
				return if (dx <= smoothScrollLimit) calculatedTime else {
					val maximumTime = ceil(calculateTimeForScrolling(smoothScrollLimit) / .3356).toInt()
					val finalFactor = maximumTime * (smoothScrollLimit / dx).toDouble().pow(LYRIC_SMOOTH_SCROLL_SQUARE_FACTOR).toInt()
					if (finalFactor > LYRIC_SMOOTH_SCROLL_MINIMUM_SCROLL_TIME) finalFactor else LYRIC_SMOOTH_SCROLL_MINIMUM_SCROLL_TIME
				}
			}

			override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
				return speedPerPixel
			}
		}
	}


	private val positionRunnable = object : Runnable {
		@SuppressLint("SetTextI18n")
		override fun run() {
			if (!runnableRunning) return
			val currentPosition = instance?.currentPosition
			val position =
				CalculationUtils.convertDurationToTimeStamp(currentPosition ?: 0)
			val duration = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
			if (duration != null && !isUserTracking) {
				bottomSheetFullSlider.valueTo = duration.toFloat()
				bottomSheetFullSlider.value =
					min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
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
			if (duration != null && duration >= LYRIC_TRANSIT_DURATION) {
				updateLyric(duration - LYRIC_TRANSIT_DURATION)
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

}