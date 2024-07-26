/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.preference.PreferenceManager
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.error
import coil3.size.Scale
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.ui.MyBottomSheetBehavior
import org.akanework.gramophone.logic.utils.EnvUtils
import org.akanework.gramophone.logic.visibilityChanged
import org.akanework.gramophone.ui.MainActivity


class PlayerBottomSheet private constructor(
    context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
) : FrameLayout(context, attributeSet, defStyleAttr, defStyleRes),
    Player.Listener, DefaultLifecycleObserver {
    constructor(context: Context, attributeSet: AttributeSet?)
            : this(context, attributeSet, 0, 0)

    companion object {
        const val TAG = "PlayerBottomSheet"
    }

    private var sessionToken: SessionToken? = null
    private var lastDisposable: Disposable? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var standardBottomSheetBehavior: MyBottomSheetBehavior<FrameLayout>? = null
    private var bottomSheetBackCallback: OnBackPressedCallback? = null
    val fullPlayer: FullBottomSheet
    private val previewPlayer: View
    private val bottomSheetPreviewCover: ImageView
    private val bottomSheetPreviewTitle: TextView
    private val bottomSheetPreviewControllerButton: MaterialButton
    private val bottomSheetPreviewNextButton: MaterialButton
    private val bottomSheetPreviewCoverFrame: MaterialCardView
    private val bottomSheetBlendView: BlendView
    private var shouldRetractBottomSheet = false

    private val activity
        get() = context as MainActivity
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val lifecycleOwner: LifecycleOwner
        get() = activity
    private val handler = Handler(Looper.getMainLooper())
    private val instance: MediaController?
        get() = if (controllerFuture?.isDone == false || controllerFuture?.isCancelled == true)
            null else controllerFuture?.get()
    private var lastActuallyVisible: Boolean? = null
    private var lastMeasuredHeight: Int? = null
    var visible = false
        set(value) {
            if (field != value) {
                field = value
                standardBottomSheetBehavior?.state =
                    if ((instance?.mediaItemCount ?: 0) > 0 && value) {
                        if (standardBottomSheetBehavior?.state
                            != BottomSheetBehavior.STATE_EXPANDED
                        )
                            BottomSheetBehavior.STATE_COLLAPSED
                        else BottomSheetBehavior.STATE_EXPANDED
                    } else {
                        BottomSheetBehavior.STATE_HIDDEN
                    }
            }
        }
    val actuallyVisible: Boolean
        get() = standardBottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN

    val isDarkMode: Boolean = EnvUtils.isDarkMode(context)

    val insetController = WindowCompat.getInsetsController(activity.window, this@PlayerBottomSheet)

    init {
        inflate(context, R.layout.bottom_sheet, this)
        this.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                R.color.contrast_colorBackgroundBottomNav
            )
        )
        previewPlayer = findViewById(R.id.preview_player)
        fullPlayer = findViewById(R.id.full_player)
        bottomSheetPreviewTitle = findViewById(R.id.preview_song_name)
        bottomSheetPreviewCover = findViewById(R.id.preview_album_cover)
        bottomSheetPreviewControllerButton = findViewById(R.id.preview_control)
        bottomSheetPreviewNextButton = findViewById(R.id.preview_next)
        bottomSheetBlendView = findViewById(R.id.blend)
        bottomSheetPreviewCoverFrame = findViewById(R.id.preview_album_frame)
        bottomSheetPreviewCoverFrame.visibilityChanged { view ->
            when (view.visibility) {
                View.VISIBLE -> {
                    animateAlbumCover(1f)
                }
                else -> {
                    animateAlbumCover(0f)
                }
            }
        }

        setOnClickListener {
            if (standardBottomSheetBehavior!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
                standardBottomSheetBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        bottomSheetPreviewControllerButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            instance?.playOrPause()
        }

        bottomSheetPreviewNextButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            instance?.seekToNextMediaItem()
        }
    }

    private val bottomSheetCallback = object : BottomSheetCallback() {
        override fun onStateChanged(
            bottomSheet: View,
            newState: Int,
        ) {
            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    fullPlayer.visibility = View.GONE
                    previewPlayer.visibility = View.VISIBLE
                    bottomSheetPreviewCoverFrame.visibility = View.VISIBLE
                    previewPlayer.alpha = 1f
                    bottomSheetBlendView.alpha = 0f
                    fullPlayer.alpha = 0f
                    bottomSheetBlendView.stopRotationAnimation()
                    bottomSheetBackCallback!!.isEnabled = false
                    bottomSheetBackCallback!!.remove()
                    if (!isDarkMode && !insetController.isAppearanceLightStatusBars) {
                        WindowCompat.getInsetsController(activity.window, this@PlayerBottomSheet)
                            .isAppearanceLightStatusBars = true
                    }
                    fullPlayer.applyElevation(true)
                }

                BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                    fullPlayer.visibility = View.VISIBLE
                    previewPlayer.visibility = View.VISIBLE
                    bottomSheetPreviewCoverFrame.visibility = View.VISIBLE
                    if (instance?.isPlaying == true) {
                        bottomSheetBlendView.startRotationAnimation()
                    }
                    if (!isDarkMode && !insetController.isAppearanceLightStatusBars) {
                        WindowCompat.getInsetsController(activity.window, this@PlayerBottomSheet)
                            .isAppearanceLightStatusBars = true
                    }
                    fullPlayer.changeBottomCoverVisibility(View.INVISIBLE)
                }

                BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                    previewPlayer.visibility = View.GONE
                    fullPlayer.visibility = View.VISIBLE
                    if (!isDarkMode && insetController.isAppearanceLightStatusBars) {
                        WindowCompat.getInsetsController(activity.window, this@PlayerBottomSheet)
                            .isAppearanceLightStatusBars = false
                    }
                    previewPlayer.alpha = 0f
                    fullPlayer.alpha = 1f
                    bottomSheetPreviewCoverFrame.visibility = View.GONE
                    fullPlayer.changeBottomCoverVisibility(View.VISIBLE)
                    if (instance?.isPlaying == true) {
                        bottomSheetBlendView.startRotationAnimation()
                    }
                    bottomSheetBackCallback!!.isEnabled = true
                    activity.onBackPressedDispatcher.addCallback(
                        activity,
                        bottomSheetBackCallback!!
                    )
                    if (!fullPlayer.isCoverFrameElevated()) {
                        fullPlayer.applyElevation(false)
                    }
                }

                BottomSheetBehavior.STATE_HIDDEN -> {
                    previewPlayer.visibility = View.GONE
                    fullPlayer.visibility = View.GONE
                    fullPlayer.alpha = 0f
                    previewPlayer.alpha = 0f
                    bottomSheetBlendView.alpha = 0f
                    bottomSheetBlendView.stopRotationAnimation()
                    bottomSheetBackCallback!!.isEnabled = false
                    bottomSheetBackCallback!!.remove()
                    if (!isDarkMode && !insetController.isAppearanceLightStatusBars) {
                        WindowCompat.getInsetsController(activity.window, this@PlayerBottomSheet)
                            .isAppearanceLightStatusBars = true
                    }
                    activity.scaleContainer(0f)
                }
            }
            dispatchBottomSheetInsets()
        }

        override fun onSlide(
            bottomSheet: View,
            slideOffset: Float,
        ) {
            if (slideOffset < 0) {
                // hidden state
                previewPlayer.alpha = 1 - (-1 * slideOffset)
                bottomSheetBlendView.alpha = 0f
                if (shouldRetractBottomSheet) {
                    activity.retractNavigationViewWithProgress(
                        previewPlayer.measuredHeight - fullPlayer.measuredHeight - getDistanceToBottom(
                            bottomSheet
                        ).toFloat()
                    )
                } else {
                    activity.retractNavigationViewWithProgress(
                        0f
                    )
                }
                return
            }
            activity.retractNavigationViewWithProgress(
                fullPlayer.measuredHeight - previewPlayer.measuredHeight + getDistanceToBottom(
                    bottomSheet
                ).toFloat()
            )
            activity.scaleContainer(slideOffset)
            animateAlbumCover(slideOffset)
            if (slideOffset <= 0.1) {
                bottomSheetBlendView.alpha = slideOffset * 10
                fullPlayer.alpha = slideOffset * 10
                previewPlayer.alpha = 1 - (slideOffset * 10)
            } else {
                bottomSheetBlendView.alpha = 1f
                fullPlayer.alpha = 1f
                previewPlayer.alpha = 0f
            }
        }
    }

    private fun animateAlbumCover(progress: Float) {
        val targetCoordinateX =
            if (fullPlayer.isPlaylistEnabled)
                fullPlayer.playlistCoverCoordinateX
            else
                fullPlayer.fullCoverFrameCoordinateX
        val targetCoordinateY =
            if (fullPlayer.isPlaylistEnabled)
                fullPlayer.playlistCoverCoordinateY
            else
                fullPlayer.fullCoverFrameCoordinateY
        val targetScale =
            if (fullPlayer.isPlaylistEnabled)
                fullPlayer.playlistCoverScale
            else
                fullPlayer.fullCoverFrameScale
        if (targetCoordinateX < bottomSheetPreviewCoverFrame.left) return
        bottomSheetPreviewCoverFrame.translationX = progress * (targetCoordinateX - bottomSheetPreviewCoverFrame.left)
        bottomSheetPreviewCoverFrame.translationY = progress * (targetCoordinateY - bottomSheetPreviewCoverFrame.top)
        bottomSheetPreviewCoverFrame.scaleX = 1f + progress * (targetScale)
        bottomSheetPreviewCoverFrame.scaleY = 1f + progress * (targetScale)
        if (!fullPlayer.isPlaylistEnabled) {
            bottomSheetPreviewCoverFrame.radius = (4f - progress * 3f).dpToPx(context)
        } else {
            bottomSheetPreviewCoverFrame.radius = 4f.dpToPx(context)
        }
        bottomSheetPreviewCoverFrame.strokeWidth = ((1f - progress) * 0.75f).dpToPx(context).toInt()
    }

    private fun getDistanceToBottom(view: View): Int {
        val windowMetrics =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
        val screenHeight = windowMetrics.bounds.height()

        val location = IntArray(2)
        view.getLocationOnScreen(location)

        return screenHeight - (location[1] + view.height)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        doOnLayout { // wait for CoordinatorLayout to finish to allow getting behaviour
            standardBottomSheetBehavior = MyBottomSheetBehavior.from(this)
            fullPlayer.minimize = {
                standardBottomSheetBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            fullPlayer.bottomSheetFullBlendView = bottomSheetBlendView
            bottomSheetBackCallback = object : OnBackPressedCallback(enabled = false) {
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    standardBottomSheetBehavior!!.startBackProgress(backEvent)
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    standardBottomSheetBehavior!!.updateBackProgress(backEvent)
                }

                override fun handleOnBackPressed() {
                    standardBottomSheetBehavior!!.handleBackInvoked()
                }

                override fun handleOnBackCancelled() {
                    standardBottomSheetBehavior!!.cancelBackProgress()
                }
            }
            standardBottomSheetBehavior!!.addBottomSheetCallback(bottomSheetCallback)
            // this is required after onRestoreSavedInstanceState() in BottomSheetBehaviour
            bottomSheetCallback.onStateChanged(this, standardBottomSheetBehavior!!.state)
            lifecycleOwner.lifecycle.addObserver(this)
            updatePeekHeight()
            dispatchBottomSheetInsets()
        }
    }

    fun shouldRetractBottomNavigation(decision: Boolean) {
        shouldRetractBottomSheet = decision
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastActuallyVisible = null
        lastMeasuredHeight = null
        fullPlayer.minimize = null
        fullPlayer.bottomSheetFullBlendView = null
        lifecycleOwner.lifecycle.removeObserver(this)
        standardBottomSheetBehavior!!.removeBottomSheetCallback(bottomSheetCallback)
        bottomSheetBackCallback!!.remove()
        standardBottomSheetBehavior = null
        onStop(lifecycleOwner)
    }

    private fun updatePeekHeight() {
        previewPlayer.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.UNSPECIFIED
        )
        standardBottomSheetBehavior?.setPeekHeight(previewPlayer.measuredHeight, false)
    }

    fun generateBottomSheetInsets(insets: WindowInsetsCompat): WindowInsetsCompat {
        val resolvedMeasuredHeight = if (lastActuallyVisible == true) lastMeasuredHeight ?: 0 else 0
        var navBar1 = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        var navBar2 = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        val bottomSheetInsets = Insets.of(0, 0, 0, resolvedMeasuredHeight)
        navBar1 = Insets.max(navBar1, bottomSheetInsets)
        navBar2 = Insets.max(navBar2, bottomSheetInsets)
        return WindowInsetsCompat.Builder(insets)
            .setInsets(WindowInsetsCompat.Type.navigationBars(), navBar1)
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), navBar2)
            .build()
    }

    private fun dispatchBottomSheetInsets() {
        if (lastMeasuredHeight == previewPlayer.measuredHeight &&
            lastActuallyVisible == actuallyVisible
        ) return
        if (BuildConfig.DEBUG) Log.i(TAG, "dispatching bottom sheet insets")
        lastMeasuredHeight = previewPlayer.measuredHeight
        lastActuallyVisible = actuallyVisible
        // This dispatches the last known insets again to force regeneration of
        // FragmentContainerView's insets which will in turn call generateBottomSheetInsets().
        val i = ViewCompat.getRootWindowInsets(activity.window.decorView)
        if (i != null) {
            ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, i.clone())
        } else Log.e(TAG, "getRootWindowInsets returned null, this should NEVER happen")
    }

    override fun dispatchApplyWindowInsets(platformInsets: WindowInsets): WindowInsets {
        val bottomNavigationHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_height)
        val insets = WindowInsetsCompat.toWindowInsetsCompat(platformInsets)
        val myInsets = insets.getInsets(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )
        // We here have to set up inset padding manually as the bottom sheet won't know what
        // View is behind the status bar, paddingTopSystemWindowInsets just allows it to go
        // behind it, which differs from the other padding*SystemWindowInsets. We can't use the
        // other padding*SystemWindowInsets to apply systemBars() because previewPlayer and
        // fullPlayer should extend into system bars AND display cutout. previewPlayer can't use
        // fitsSystemWindows because it doesn't want top padding from status bar.
        // We have to do it manually, duh.
        previewPlayer.setPadding(
            myInsets.left,
            0,
            myInsets.right,
            myInsets.bottom + bottomNavigationHeight
        )
        bottomSheetPreviewCoverFrame.setPadding(
            myInsets.left,
            bottomSheetPreviewCoverFrame.paddingTop,
            bottomSheetPreviewCoverFrame.paddingRight,
            bottomSheetPreviewCoverFrame.paddingBottom
        )
        val marginLayoutParams = bottomSheetPreviewCoverFrame.layoutParams as MarginLayoutParams
        marginLayoutParams.marginStart = myInsets.left + resources.getDimensionPixelSize(R.dimen.preview_album_cover_start)
        bottomSheetPreviewCoverFrame.layoutParams = marginLayoutParams
        // Let fullPlayer handle insets itself (and discard result as it's irrelevant to hierarchy)
        ViewCompat.dispatchApplyWindowInsets(fullPlayer, insets.clone())
        // Now make sure BottomSheetBehaviour has the correct View height set.
        if (isLaidOut && !isLayoutRequested) {
            updatePeekHeight()
        } else {
            doOnNextLayout {
                updatePeekHeight()
                dispatchBottomSheetInsets()
            }
        }
        val i = insets.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )
        return WindowInsetsCompat.Builder(insets)
            .setInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.of(0, myInsets.top, 0, 0)
            )
            .setInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.of(0, i.top, 0, 0)
            )
            .build()
            .toWindowInsets()!!
    }

    fun getPlayer(): MediaController? = instance

    @OptIn(ExperimentalCoilApi::class)
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if ((instance?.mediaItemCount ?: 0) > 0) {
            lastDisposable?.dispose()
            lastDisposable = context.imageLoader.enqueue(ImageRequest.Builder(context).apply {
                target(onSuccess = {
                    bottomSheetPreviewCover.setImageDrawable(it.asDrawable(context.resources))
                }, onError = {
                    bottomSheetPreviewCover.setImageDrawable(it?.asDrawable(context.resources))
                }) // do not react to onStart() which sets placeholder
                data(mediaItem?.mediaMetadata?.artworkUri)
                scale(Scale.FILL)
                error(R.drawable.ic_default_cover)
            }.build())
            mediaItem?.mediaMetadata?.artworkUri?.let {
                bottomSheetBlendView.setImageUri(
                    it
                )
            }
            bottomSheetPreviewTitle.text = mediaItem?.mediaMetadata?.title
        } else {
            lastDisposable?.dispose()
            lastDisposable = null
        }
        var newState = standardBottomSheetBehavior!!.state
        if ((instance?.mediaItemCount ?: 0) > 0 && visible) {
            if (newState != BottomSheetBehavior.STATE_EXPANDED) {
                newState = BottomSheetBehavior.STATE_COLLAPSED
            }
        } else {
            newState = BottomSheetBehavior.STATE_HIDDEN
        }
        handler.post {
            // if we are destroyed after onMediaItemTransition but before this runs,
            // standardBottomSheetBehavior will be null
            standardBottomSheetBehavior?.state = newState
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_BUFFERING) return
        val myTag = bottomSheetPreviewControllerButton.getTag(R.id.play_next) as Int?
        if (instance?.isPlaying == true && myTag != 1) {
            bottomSheetPreviewControllerButton.icon =
                AppCompatResources.getDrawable(context, R.drawable.ic_nowplaying_mp_pause)
            bottomSheetPreviewControllerButton.setTag(R.id.play_next, 1)
            bottomSheetBlendView.startRotationAnimation()
        } else if (instance?.isPlaying == false && myTag != 2) {
            bottomSheetPreviewControllerButton.icon =
                AppCompatResources.getDrawable(context, R.drawable.ic_nowplaying_mp_play)
            bottomSheetPreviewControllerButton.setTag(R.id.play_next, 2)
            bottomSheetBlendView.stopRotationAnimation()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        sessionToken =
            SessionToken(context, ComponentName(context, GramophonePlaybackService::class.java))
        controllerFuture =
            MediaController
                .Builder(context, sessionToken!!)
                .setListener(fullPlayer.sessionListener)
                .buildAsync()
        controllerFuture!!.addListener(
            {
                instance?.addListener(this)
                onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
                onMediaItemTransition(
                    instance?.currentMediaItem,
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                )
                if ((activity.consumeAutoPlay() || prefs.getBooleanStrict(
                        "autoplay",
                        false
                    )) && instance?.isPlaying != true
                ) {
                    instance?.play()
                }
            },
            MoreExecutors.directExecutor(),
        )
        fullPlayer.onStart(controllerFuture!!)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        fullPlayer.onStop()
        instance?.removeListener(this)
        instance?.release()
        controllerFuture?.cancel(true)
        controllerFuture = null
    }

}