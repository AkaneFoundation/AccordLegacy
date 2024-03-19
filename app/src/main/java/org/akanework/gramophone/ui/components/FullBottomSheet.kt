package org.akanework.gramophone.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
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
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getLyrics
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.hasTimer
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.setTextAnimation
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.MainActivity
import kotlin.math.min

class FullBottomSheet(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
	ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Player.Listener,
	SharedPreferences.OnSharedPreferenceChangeListener {
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
	private var lastArtworkUri: Uri? = null

	private val prefs = context.gramophoneApplication.prefs

	companion object {
		const val SLIDER_UPDATE_INTERVAL: Long = 100
		const val LYRIC_FADE_TRANSITION_SEC: Long = 125
	}

	private val touchListener = object : Slider.OnSliderTouchListener {
		override fun onStartTrackingTouch(slider: Slider) {
			isUserTracking = true
		}

		override fun onStopTrackingTouch(slider: Slider) {
			// This value is multiplied by 1000 is because
			// when the number is too big (like when toValue
			// used the duration directly) we might encounter
			// some performance problem.
			val mediaId = instance?.currentMediaItem?.mediaId
			if (mediaId != null) {
				instance?.seekTo((slider.value.toLong()))
				updateLyric(slider.value.toLong())
			}
			isUserTracking = false
		}
	}
	private val bottomSheetFullCover: ImageView
	private val bottomSheetFullTitle: TextView
	private val bottomSheetFullSubtitle: TextView
	private val bottomSheetFullControllerButton: MaterialButton
	private val bottomSheetFullNextButton: MaterialButton
	private val bottomSheetFullPreviousButton: MaterialButton
	private val bottomSheetFullDuration: TextView
	private val bottomSheetFullPosition: TextView
	private val bottomSheetShuffleButton: MaterialButton
	private val bottomSheetLoopButton: MaterialButton
	private val bottomSheetPlaylistButton: MaterialButton
	private val bottomSheetTimerButton: MaterialButton
	private val bottomSheetFavoriteButton: MaterialButton
	private val bottomSheetInfinityButton: MaterialButton
	private val bottomSheetFullSlider: Slider
	private val bottomSheetFullCoverFrame: MaterialCardView
	val bottomSheetFullLyricRecyclerView: RecyclerView
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
	private val bottomSheetFullPlaylistRecyclerView: RecyclerView
	private val bottomSheetFullPlaylistAdapter: PlaylistCardAdapter
	private val bottomSheetFullPlaylistCoverFrame: MaterialCardView
	private var playlistNowPlaying: TextView? = null
	private var playlistNowPlayingCover: ImageView? = null

	init {
		inflate(context, R.layout.full_player, this)
		bottomSheetFullCoverFrame = findViewById(R.id.album_cover_frame)
		bottomSheetFullCover = findViewById(R.id.full_sheet_cover)
		bottomSheetFullTitle = findViewById(R.id.full_song_name)
		bottomSheetFullSubtitle = findViewById(R.id.full_song_artist)
		bottomSheetFullPreviousButton = findViewById(R.id.sheet_previous_song)
		bottomSheetFullControllerButton = findViewById(R.id.sheet_mid_button)
		bottomSheetFullNextButton = findViewById(R.id.sheet_next_song)
		bottomSheetFullPosition = findViewById(R.id.position)
		bottomSheetFullDuration = findViewById(R.id.duration)
		bottomSheetFullSlider = findViewById(R.id.slider_vert)
		bottomSheetShuffleButton = findViewById(R.id.sheet_random)
		bottomSheetLoopButton = findViewById(R.id.sheet_loop)
		bottomSheetTimerButton = findViewById(R.id.timer)
		bottomSheetFavoriteButton = findViewById(R.id.favor)
		bottomSheetPlaylistButton = findViewById(R.id.playlist)
		bottomSheetFullLyricRecyclerView = findViewById(R.id.lyric_frame)
		bottomSheetFullDragHandle = findViewById(R.id.drag)
		bottomSheetFullTextLayout = findViewById(R.id.textLayout)
		bottomSheetFullHeaderFrame = findViewById(R.id.playlist_frame)
		bottomSheetFullPlaylistFrame = findViewById(R.id.playlist_content)
		bottomSheetFullPlaylistCover = findViewById(R.id.playlist_demo_cover)
		bottomSheetFullPlaylistCoverFrame = findViewById(R.id.playlist_cover_frame)
		bottomSheetFullPlaylistTitle = findViewById(R.id.playlist_song_name)
		bottomSheetFullPlaylistSubtitle = findViewById(R.id.playlist_song_artist)
		bottomSheetFullPlaylistRecyclerView = findViewById(R.id.playlist_recyclerview)
		bottomSheetInfinityButton = findViewById(R.id.sheet_infinity)

		bottomSheetFullPlaylistAdapter = PlaylistCardAdapter(activity)
		bottomSheetFullPlaylistRecyclerView.layoutManager = LinearLayoutManager(context)
		bottomSheetFullPlaylistRecyclerView.adapter = bottomSheetFullPlaylistAdapter

		refreshSettings(null)
		prefs.registerOnSharedPreferenceChangeListener(this)

		bottomSheetTimerButton.setOnClickListener {
			if (Build.VERSION.SDK_INT >= 23) {
				it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
			}
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
			if (Build.VERSION.SDK_INT >= 23) {
				it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
			}
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

		bottomSheetInfinityButton.addOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				bottomSheetLoopButton.isChecked = false
				bottomSheetLoopButton.isEnabled = false
				instance?.repeatMode = Player.REPEAT_MODE_ONE
			} else {
				bottomSheetLoopButton.isEnabled = true
				instance?.repeatMode = Player.REPEAT_MODE_OFF
			}
		}

		/*
		bottomSheetFavoriteButton.addOnCheckedChangeListener { _, isChecked ->
			/*
			if (isChecked) {
				instance.currentMediaItem?.let { insertIntoPlaylist(it) }
			} else {
				instance.currentMediaItem?.let { removeFromPlaylist(it) }
			}
			 */
		}

		 */

		bottomSheetPlaylistButton.addOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				changeMovableFrame(false)
				bottomSheetFullPlaylistRecyclerView.scrollToPosition(instance?.currentMediaItemIndex ?: 0)
				bottomSheetFullHeaderFrame.fadInAnimation(300)
				bottomSheetFullPlaylistFrame.fadInAnimation(300)
			} else {
				changeMovableFrame(true)
				bottomSheetFullHeaderFrame.fadOutAnimation(300, View.GONE)
				bottomSheetFullPlaylistFrame.fadOutAnimation(300, View.GONE)
			}
		}

		/*
		bottomSheetPlaylistButton.setOnClickListener {
			if (Build.VERSION.SDK_INT >= 23) {
				it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
			}
			val playlistBottomSheet = BottomSheetDialog(context)
			playlistBottomSheet.setContentView(R.layout.playlist_bottom_sheet)
			val recyclerView = playlistBottomSheet.findViewById<MyRecyclerView>(R.id.recyclerview)!!
			val playlistAdapter = PlaylistCardAdapter(dumpPlaylist(), activity)
			playlistNowPlaying = playlistBottomSheet.findViewById(R.id.now_playing)
			playlistNowPlaying!!.text = instance?.currentMediaItem?.mediaMetadata?.title
			playlistNowPlayingCover = playlistBottomSheet.findViewById(R.id.now_playing_cover)
			Glide
				.with(context)
				.load(instance?.currentMediaItem?.mediaMetadata?.artworkUri)
				.transition(DrawableTransitionOptions.withCrossFade())
				.placeholder(R.drawable.ic_default_cover)
				.into(playlistNowPlayingCover!!)
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = playlistAdapter
			recyclerView.scrollToPosition(instance?.currentMediaItemIndex ?: 0)
			recyclerView.fastScroll(null, null)
			playlistBottomSheet.setOnDismissListener {
				if (playlistNowPlaying != null) {
					Glide.with(context.applicationContext).clear(playlistNowPlayingCover!!)
					playlistNowPlayingCover = null
					playlistNowPlaying = null
				}
			}
			playlistBottomSheet.show()
		}

		 */
		bottomSheetFullControllerButton.setOnClickListener {
			if (Build.VERSION.SDK_INT >= 23) {
				it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
			}
			instance?.playOrPause()
		}
		bottomSheetFullPreviousButton.setOnClickListener {
			if (Build.VERSION.SDK_INT >= 23) {
				it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
			}
			instance?.seekToPreviousMediaItem()
		}
		bottomSheetFullNextButton.setOnClickListener {
			if (Build.VERSION.SDK_INT >= 23) {
				it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
			}
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
				}
			}
		}

		bottomSheetFullSlider.addOnSliderTouchListener(touchListener)

		bottomSheetShuffleButton.setOnClickListener {
			if (Build.VERSION.SDK_INT >= 23) {
				it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
			}
		}

		bottomSheetFullLyricRecyclerView.layoutManager =
			bottomSheetFullLyricLinearLayoutManager
		bottomSheetFullLyricRecyclerView.adapter =
			bottomSheetFullLyricAdapter

	}

	private val transformIn = MaterialContainerTransform().apply {
		startView = bottomSheetFullCoverFrame
		endView = bottomSheetFullPlaylistCoverFrame
		addTarget(bottomSheetFullPlaylistCoverFrame)
		scrimColor = Color.TRANSPARENT
		duration = 300
	}

	private val transformOut = MaterialContainerTransform().apply {
		startView = bottomSheetFullPlaylistCoverFrame
		endView = bottomSheetFullCoverFrame
		addTarget(bottomSheetFullCoverFrame)
		scrimColor = Color.TRANSPARENT
		duration = 300
	}

	private fun changeMovableFrame(isVisible: Boolean) {
		if (isVisible) {
			bottomSheetFullTextLayout.fadInAnimation(300) {}
			bottomSheetFullDragHandle.fadInAnimation(300) {}
			TransitionManager.beginDelayedTransition(this, transformOut)
			bottomSheetFullPlaylistCoverFrame.visibility = View.INVISIBLE
			bottomSheetFullCoverFrame.visibility = View.VISIBLE
		} else {
			bottomSheetFullTextLayout.fadOutAnimation(300) {}
			bottomSheetFullDragHandle.fadOutAnimation(300) {}
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

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		refreshSettings(key)
		if (key == "lyric_center" || key == "lyric_bold") {
			@Suppress("NotifyDataSetChanged")
			bottomSheetFullLyricAdapter.notifyDataSetChanged()
		}
	}

	private fun refreshSettings(key: String?) {
		if (key == null || key == "lyric_center" || key == "lyric_bold") {
			bottomSheetFullLyricAdapter.updateLyricStatus()
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
			/*
			if (activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition]
					.songList.contains(instance.currentMediaItem)) {
				bottomSheetFavoriteButton.isChecked = true
				// TODO
			} else {
				bottomSheetFavoriteButton.isChecked = false
				// TODO
			}

			 */
		}, MoreExecutors.directExecutor())
	}

	fun onStop() {
		runnableRunning = false
		instance?.removeListener(this)
		controllerFuture = null
	}

	override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
		super.setPadding(left, top, right, bottom)
		// this is to enable edge to edge for lyric view with some trickery
		bottomSheetFullLyricRecyclerView.updateLayoutParams<MarginLayoutParams> {
			topMargin = -top
			bottomMargin = -bottom
		}
		bottomSheetFullLyricRecyclerView.setPadding(0, top, 0, bottom)
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun onMediaItemTransition(
		mediaItem: MediaItem?,
		reason: Int
	) {
		if (instance?.mediaItemCount != 0) {
			val artworkUri = mediaItem?.mediaMetadata?.artworkUri
			Glide
				.with(context)
				.load(artworkUri)
				.error(R.drawable.ic_default_cover)
				.into(object : CustomTarget<Drawable>() {
					override fun onResourceReady(
						resource: Drawable,
						transition: Transition<in Drawable>?
					) {
						bottomSheetFullCover.setImageDrawable(resource)
						bottomSheetFullPlaylistCover.setImageDrawable(resource)
					}

					override fun onLoadFailed(errorDrawable: Drawable?) {
						bottomSheetFullCover.setImageDrawable(errorDrawable)
						bottomSheetFullPlaylistCover.setImageDrawable(errorDrawable)
					}

					override fun onLoadCleared(placeholder: Drawable?) {
						// TODO
					}
				})
			lastArtworkUri = artworkUri
			bottomSheetFullTitle.setTextAnimation(mediaItem?.mediaMetadata?.title, skipAnimation = firstTime)
			bottomSheetFullPlaylistTitle.setTextAnimation(mediaItem?.mediaMetadata?.title, skipAnimation = firstTime)
			bottomSheetFullSubtitle.setTextAnimation(
				mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist), skipAnimation = firstTime
			)
			bottomSheetFullPlaylistSubtitle.setTextAnimation(
				mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist), skipAnimation = firstTime
			)
			bottomSheetFullDuration.text =
				mediaItem?.mediaMetadata?.extras?.getLong("Duration")
					?.let { CalculationUtils.convertDurationToTimeStamp(it) }
			if (playlistNowPlaying != null) {
				playlistNowPlaying!!.text = mediaItem?.mediaMetadata?.title
				Glide
					.with(context)
					.load(mediaItem?.mediaMetadata?.artworkUri)
					.transition(DrawableTransitionOptions.withCrossFade())
					.placeholder(R.drawable.ic_default_cover)
					.into(playlistNowPlayingCover!!)
			}

			/*
			if (activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition]
					.songList.contains(instance.currentMediaItem)
			) {
				// TODO
			} else {
				// TODO
			}

			 */
			if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
				bottomSheetFullPlaylistAdapter.updatePlaylist(
					dumpPlaylist()
				)
			}
		} else {
			lastArtworkUri = null
			Glide.with(context.applicationContext).clear(bottomSheetFullCover)
			Glide.with(context.applicationContext).clear(bottomSheetFullPlaylistCover)
			if (playlistNowPlaying != null) {
				Glide.with(context.applicationContext).clear(playlistNowPlayingCover!!)
			}
		}
		val position = CalculationUtils.convertDurationToTimeStamp(instance?.currentPosition ?: 0)
		val duration = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
		if (duration != null && !isUserTracking) {
			bottomSheetFullSlider.valueTo = duration.toFloat()
			bottomSheetFullSlider.value =
				min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
			bottomSheetFullPosition.text = position
		}
		updateLyric(duration)
	}

	override fun onEvents(player: Player, events: Player.Events) {
		super.onEvents(player, events)
		if (events.size() == 2 &&
			events[0] == Player.EVENT_TIMELINE_CHANGED &&
			events[1] == Player.EVENT_POSITION_DISCONTINUITY &&
			instance != null &&
			instance!!.mediaItemCount > bottomSheetFullPlaylistAdapter.getPlaylistSize()
			) {
			bottomSheetFullPlaylistAdapter.updatePlaylist(
				dumpPlaylist()
			)
		}
	}

	override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
		bottomSheetShuffleButton.isChecked = shuffleModeEnabled
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
		//android.util.Log.e("hi","$keyCode") TODO this method is no-op, but why?
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

	private fun dumpPlaylist(): MutableList<MediaItem> {
		val items = mutableListOf<MediaItem>()
		for (i in 0 until instance!!.mediaItemCount) {
			items.add(instance!!.getMediaItemAt(i))
		}
		return items
	}

	private inner class LyricAdapter(
		private val lyricList: MutableList<MediaStoreUtils.Lyric>
	) : MyRecyclerView.Adapter<LyricAdapter.ViewHolder>() {

		private var defaultTextColor = MaterialColors.getColor(
			activity,
			com.google.android.material.R.attr.colorPrimaryVariant,
			-1
		)

		private var highlightTextColor = MaterialColors.getColor(
			activity,
			com.google.android.material.R.attr.colorPrimary,
			-1
		)

		var currentFocusPos = -1
		private var currentTranslationPos = -1
		private var isBoldEnabled = false
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

		override fun onBindViewHolder(holder: LyricAdapter.ViewHolder, position: Int) {
			val lyric = lyricList[position]

			with(holder.lyricCard) {
				setOnClickListener {
					if (Build.VERSION.SDK_INT >= 23) {
						performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
					}
					val instance = activity.getPlayer()
					if (instance?.isPlaying == false) {
						instance.play()
					}
					instance?.seekTo(lyric.timeStamp)
				}
			}

			with(holder.lyricTextView) {
				visibility = if (lyric.content.isNotEmpty()) View.VISIBLE else View.GONE
				text = lyric.content

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isBoldEnabled) {
					this.typeface = Typeface.create(null, 700, false)
				} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
					this.typeface = Typeface.create(null, 500, false)
				}

				if (isLyricCentered) {
					this.gravity = Gravity.CENTER
				} else {
					this.gravity = Gravity.START
				}

				val textSize = if (lyric.isTranslation) 20f else 28f
				val paddingTop = (if (lyric.isTranslation) 2 else 18).dpToPx(context)
				val paddingBottom = (if (position + 1 < lyricList.size &&
					lyricList[position + 1].isTranslation
				) 2 else 18).dpToPx(context)

				this.textSize = textSize
				setPadding(10.dpToPx(context), paddingTop, 10.dpToPx(context), paddingBottom)

				val isFocus = position == currentFocusPos || position == currentTranslationPos
				setTextColor(if (isFocus) highlightTextColor else defaultTextColor)
			}
		}

		override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
			super.onAttachedToRecyclerView(recyclerView)
			updateLyricStatus()
		}

		fun updateLyricStatus() {
			isBoldEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
					prefs.getBooleanStrict("lyric_bold", false)
			isLyricCentered = prefs.getBooleanStrict("lyric_center", false)
		}

		override fun getItemCount(): Int = lyricList.size

		inner class ViewHolder(
			view: View
		) : RecyclerView.ViewHolder(view) {
			val lyricTextView: TextView = view.findViewById(R.id.lyric)
			val lyricCard: MaterialCardView = view.findViewById(R.id.cardview)
		}

		@SuppressLint("NotifyDataSetChanged")
		fun updateTextColor(newColor: Int, newHighlightColor: Int) {
			defaultTextColor = newColor
			highlightTextColor = newHighlightColor
			notifyDataSetChanged()
		}

		fun updateHighlight(position: Int) {
			if (currentFocusPos == position) return
			if (position >= 0) {
				currentFocusPos.let {
					notifyItemChanged(it)
					currentFocusPos = position
					notifyItemChanged(currentFocusPos)
				}

				if (position + 1 < lyricList.size &&
					lyricList[position + 1].isTranslation
				) {
					currentTranslationPos.let {
						notifyItemChanged(it)
						currentTranslationPos = position + 1
						notifyItemChanged(currentTranslationPos)
					}
				} else if (currentTranslationPos != -1) {
					notifyItemChanged(currentTranslationPos)
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

		private val playlist: MutableList<MediaItem> = mutableListOf()

		@SuppressLint("NotifyDataSetChanged")
		fun updatePlaylist(content: MutableList<MediaItem>) {
			playlist.clear()
			playlist.addAll(content)
			notifyDataSetChanged()
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
			holder.songName.text = playlist[holder.bindingAdapterPosition].mediaMetadata.title
			holder.songArtist.text = playlist[holder.bindingAdapterPosition].mediaMetadata.artist
			Glide
				.with(holder.songCover.context)
				.load(playlist[position].mediaMetadata.artworkUri)
				.transition(DrawableTransitionOptions.withCrossFade())
				.placeholder(R.drawable.ic_default_cover)
				.into(holder.songCover)
			holder.closeButton.setOnClickListener {
				if (Build.VERSION.SDK_INT >= 23) {
					it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
				}
				val instance = activity.getPlayer()
				val pos = holder.bindingAdapterPosition
				playlist.removeAt(pos)
				notifyItemRemoved(pos)
				instance?.removeMediaItem(pos)
			}
			holder.itemView.setOnClickListener {
				if (Build.VERSION.SDK_INT >= 23) {
					it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
				}
				val instance = activity.getPlayer()
				instance?.seekToDefaultPosition(holder.absoluteAdapterPosition)
			}
		}

		override fun onViewRecycled(holder: ViewHolder) {
			super.onViewRecycled(holder)
			Glide.with(activity.applicationContext).clear(holder.songCover)
		}

		override fun getItemCount(): Int = playlist.size

		inner class ViewHolder(
			view: View,
		) : RecyclerView.ViewHolder(view) {
			val songName: TextView = view.findViewById(R.id.title)
			val songArtist: TextView = view.findViewById(R.id.artist)
			val songCover: ImageView = view.findViewById(R.id.cover)
			val songCoverFrame: MaterialCardView = view.findViewById(R.id.playlist_list_frame)
			val closeButton: MaterialButton = view.findViewById(R.id.close)
		}

	}

	/*
	@Suppress("DEPRECATION")
	private fun insertIntoPlaylist(song: MediaItem) {
		val playlistEntry = ContentValues()
		val playlistId = activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].id
		playlistEntry.put(MediaStore.Audio.Playlists.Members.PLAYLIST_ID, playlistId)
		playlistEntry.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId)

		context.contentResolver.insert(
			MediaStore.Audio.Playlists.Members.getContentUri(
				"external",
				playlistId
			), playlistEntry
		)
		activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].songList.add(song)
	}

	@Suppress("DEPRECATION")
	private fun removeFromPlaylist(song: MediaItem) {
		val selection = "${MediaStore.Audio.Playlists.Members.AUDIO_ID} = ?"
		val selectionArgs = arrayOf(song.mediaId)
		val playlistId = activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].id

		context.contentResolver.delete(
			MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
			selection,
			selectionArgs
		)
		activity.libraryViewModel.playlistList.value!![MediaStoreUtils.favPlaylistPosition].songList.remove(song)
	}

	 */


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

	private fun createSmoothScroller() =
		object : LinearSmoothScroller(context) {
			override fun calculateDtToFit(
				viewStart: Int,
				viewEnd: Int,
				boxStart: Int,
				boxEnd: Int,
				snapPreference: Int
			): Int {
				return (super.calculateDtToFit(
					viewStart,
					viewEnd,
					boxStart,
					boxEnd,
					snapPreference
				) + context.resources.displayMetrics.heightPixels / 3).coerceAtMost(viewEnd)
			}

			override fun getVerticalSnapPreference(): Int {
				return SNAP_TO_START
			}

			override fun calculateTimeForDeceleration(dx: Int): Int {
				return 500
			}
		}

	private val positionRunnable = object : Runnable {
		override fun run() {
			if (!runnableRunning) return
			val position =
				CalculationUtils.convertDurationToTimeStamp(instance?.currentPosition ?: 0)
			val duration = instance?.currentMediaItem?.mediaMetadata?.extras?.getLong("Duration")
			if (duration != null && !isUserTracking) {
				bottomSheetFullSlider.valueTo = duration.toFloat()
				bottomSheetFullSlider.value =
					min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
				bottomSheetFullPosition.text = position
			}
			updateLyric(duration)
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