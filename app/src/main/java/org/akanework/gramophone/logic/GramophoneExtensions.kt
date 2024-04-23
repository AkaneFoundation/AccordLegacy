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

package org.akanework.gramophone.logic

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.StrictMode
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowInsets
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.GramophoneApplication
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_GET_LYRICS
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QUERY_TIMER
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_SET_TIMER
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import java.io.File

fun MediaController.playOrPause() {
    if (isPlaying) {
        pause()
    } else {
        play()
    }
}

fun MediaItem.getUri(): Uri? {
    return localConfiguration?.uri
}

fun MediaItem.getFile(): File? {
    return getUri()?.path?.let { File(it) }
}

// uses reified for performant inlining only
inline fun <reified K, reified V> HashMap<K, V>.putIfAbsentSupport(key: K, value: V) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        putIfAbsent(key, value)
    } else {
        // meh...
        if (!containsKey(key))
            this[key] = value
    }
}

fun Activity.closeKeyboard(view: View) {
    if (ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
        WindowInsetsControllerCompat(window, view).hide(WindowInsetsCompat.Type.ime())
    }
}

fun Activity.showKeyboard(view: View) {
    view.requestFocus()
    if (ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == false) {
        WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.ime())
    }
}

fun Drawable.startAnimation() {
    when (this) {
        is AnimatedVectorDrawable -> start()
        is AnimatedVectorDrawableCompat -> start()
        else -> throw IllegalArgumentException()
    }
}

fun TextView.setTextAnimation(
    text: CharSequence?,
    duration: Long = 300,
    completion: (() -> Unit)? = null,
    startWhenAnimating: (() -> Unit)? = null,
    completionWhenAnimating: (() -> Unit)? = null,
    skipAnimation: Boolean = false
) {
    if (skipAnimation) {
        this.text = text
        completion?.let { it() }
    } else if (this.text != text) {
        startWhenAnimating?.let {
            it()
        }
        fadOutAnimation(duration) {
            this.text = text
            fadInAnimation(duration) {
                completion?.let {
                    it()
                }
                completionWhenAnimating?.let {
                    it()
                }
            }
        }
    } else {
        completion?.let { it() }
    }
}

// ViewExtensions

fun View.fadOutAnimation(
    duration: Long = 300,
    visibility: Int = View.INVISIBLE,
    completion: (() -> Unit)? = null
) {
    animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction {
            this.visibility = visibility
            completion?.let {
                it()
            }
        }
}

fun View.fadInAnimation(duration: Long = 300, completion: (() -> Unit)? = null) {
    alpha = 0f
    visibility = View.VISIBLE
    animate()
        .alpha(1f)
        .setDuration(duration)
        .withEndAction {
            completion?.let {
                it()
            }
        }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Int.dpToPx(context: Context): Int =
    (this.toFloat() * context.resources.displayMetrics.density).toInt()

@Suppress("NOTHING_TO_INLINE")
inline fun Int.pxToDp(context: Context): Int =
    (this.toFloat() / context.resources.displayMetrics.density).toInt()

fun MediaController.getTimer(): Int =
    sendCustomCommand(
        SessionCommand(SERVICE_QUERY_TIMER, Bundle.EMPTY),
        Bundle.EMPTY
    ).get().extras.getInt("duration")

fun MediaController.hasTimer(): Boolean = getTimer() > 0
fun MediaController.setTimer(value: Int) {
    sendCustomCommand(
        SessionCommand(SERVICE_SET_TIMER, Bundle.EMPTY).apply {
            customExtras.putInt("duration", value)
        }, Bundle.EMPTY
    )
}

fun MediaController.getLyrics(): MutableList<MediaStoreUtils.Lyric>? =
    sendCustomCommand(
        SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
        Bundle.EMPTY
    ).get().extras.let {
        @Suppress("UNCHECKED_CAST")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it.getParcelableArray("lyrics", MediaStoreUtils.Lyric::class.java)
        } else {
            @Suppress("deprecation")
            it.getParcelableArray("lyrics") as Array<MediaStoreUtils.Lyric>?
        }?.toMutableList()
    }

// https://twitter.com/Piwai/status/1529510076196630528
fun Handler.postAtFrontOfQueueAsync(callback: Runnable) {
    sendMessageAtFrontOfQueue(Message.obtain(this, callback).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            isAsynchronous = true
        }
    })
}

fun View.enableEdgeToEdgePaddingListener(ime: Boolean = false, top: Boolean = false,
                                         extra: ((Insets) -> Unit)? = null) {
    if (fitsSystemWindows) throw IllegalArgumentException("must have fitsSystemWindows disabled")
    if (this is AppBarLayout) {
        if (ime) throw IllegalArgumentException("AppBarLayout must have ime flag disabled")
        // AppBarLayout fitsSystemWindows does not handle left/right for a good reason, it has
        // to be applied to children to look good; we rewrite fitsSystemWindows in a way mostly specific
        // to Gramophone to support shortEdges displayCutout
        if (fitsSystemWindows) throw IllegalArgumentException("must have fitsSystemWindows disabled")
        val collapsingToolbarLayout = children.find { it is CollapsingToolbarLayout } as CollapsingToolbarLayout?
        collapsingToolbarLayout?.let {
            // The CollapsingToolbarLayout mustn't consume insets, we handle padding here anyway
            ViewCompat.setOnApplyWindowInsetsListener(it) { _, insets -> insets }
        }
        val expandedTitleMarginStart = collapsingToolbarLayout?.expandedTitleMarginStart
        val expandedTitleMarginEnd = collapsingToolbarLayout?.expandedTitleMarginEnd
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val cutoutAndBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            (v as AppBarLayout).children.forEach {
                if (it is CollapsingToolbarLayout) {
                    val es = expandedTitleMarginStart!! + if (it.layoutDirection
                        == View.LAYOUT_DIRECTION_LTR) cutoutAndBars.left else cutoutAndBars.right
                    if (es != it.expandedTitleMarginStart) it.expandedTitleMarginStart = es
                    val ee = expandedTitleMarginEnd!! + if (it.layoutDirection
                        == View.LAYOUT_DIRECTION_RTL) cutoutAndBars.left else cutoutAndBars.right
                    if (ee != it.expandedTitleMarginEnd) it.expandedTitleMarginEnd = ee
                }
                it.setPadding(cutoutAndBars.left, 0, cutoutAndBars.right, 0)
            }
            v.setPadding(0, cutoutAndBars.top, 0, 0)
            val i = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout())
            extra?.invoke(cutoutAndBars)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.of(cutoutAndBars.left, 0, cutoutAndBars.right, cutoutAndBars.bottom))
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.of(i.left, 0, i.right, i.bottom))
                .build()
        }
    } else {
        val pl = paddingLeft
        val pt = paddingTop
        val pr = paddingRight
        val pb = paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val mask = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    if (ime) WindowInsetsCompat.Type.ime() else 0
            val i = insets.getInsets(mask)
            v.setPadding(pl + i.left, pt + (if (top) i.top else 0), pr + i.right,
                pb + i.bottom)
            extra?.invoke(i)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(insets)
                .setInsets(mask, Insets.NONE)
                .setInsetsIgnoringVisibility(mask, Insets.NONE)
                .build()
        }
    }
}

data class Margin(var left: Int, var top: Int, var right: Int, var bottom: Int) {
    companion object {
        @Suppress("NOTHING_TO_INLINE")
        internal inline fun fromLayoutParams(marginLayoutParams: MarginLayoutParams): Margin {
            return Margin(
                marginLayoutParams.leftMargin, marginLayoutParams.topMargin,
                marginLayoutParams.rightMargin, marginLayoutParams.bottomMargin
            )
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    internal inline fun apply(marginLayoutParams: MarginLayoutParams) {
        marginLayoutParams.leftMargin = left
        marginLayoutParams.topMargin = top
        marginLayoutParams.rightMargin = right
        marginLayoutParams.bottomMargin = bottom
    }
}

fun View.updateMargin(
    block: Margin.() -> Unit
) {
    val oldMargin = Margin.fromLayoutParams(layoutParams as MarginLayoutParams)
    val newMargin = oldMargin.copy().also { it.block() }
    if (oldMargin != newMargin) {
        updateLayoutParams<MarginLayoutParams> {
            newMargin.apply(this)
        }
    }
}

// enableEdgeToEdge() without enforcing contrast, magic based on androidx EdgeToEdge.kt
fun ComponentActivity.enableEdgeToEdgeProperly() {
    if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES) {
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
    } else {
        val darkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, darkScrim))
    }
}

val Context.gramophoneApplication
    get() = applicationContext as GramophoneApplication

@SuppressLint("DiscouragedPrivateApi")
private fun WindowInsets.unconsumeIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        // Api21Impl of getRootWindowInsets returns already-consumed WindowInsets with correct data
        // Said consumed insets cannot be dispatched again because well, they are already consumed
        // Workaround this using some reflection (Api23Impl+ are not affected so this is safe)
        val mSystemWindowInsetsConsumed = WindowInsets::class.java
            .getDeclaredField("mSystemWindowInsetsConsumed")
            .apply { isAccessible = true }
        val mWindowDecorInsetsConsumed = WindowInsets::class.java
            .getDeclaredField("mWindowDecorInsetsConsumed")
            .apply { isAccessible = true }
        val mStableInsetsConsumed = WindowInsets::class.java
            .getDeclaredField("mStableInsetsConsumed")
            .apply { isAccessible = true }
        mSystemWindowInsetsConsumed.set(this, false)
        mWindowDecorInsetsConsumed.set(this, false)
        mStableInsetsConsumed.set(this, false)
    }
}

// Pitfall: WindowInsetsCompat.Builder(insets) mutates the platform insets
fun WindowInsetsCompat.clone(): WindowInsetsCompat =
    WindowInsetsCompat.toWindowInsetsCompat(WindowInsets(toWindowInsets()).also {
        it.unconsumeIfNeeded()
    })

inline fun Semaphore.runInBg(crossinline runnable: suspend () -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
        acquire()
        try {
            runnable()
        } finally {
            release()
        }
    }
}

// the whole point of this function is to do literally nothing at all (but without impacting
// performance) in release builds and ignore StrictMode violations in debug builds
inline fun <reified T> useSharedPrefs(doIt: () -> T): T {
    return if (BuildConfig.DEBUG) {
        if (Looper.getMainLooper() != Looper.myLooper()) throw IllegalStateException()
        val policy = StrictMode.allowThreadDiskReads()
        try {
            StrictMode.allowThreadDiskWrites()
            doIt()
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    } else doIt()
}

inline fun <reified T> SharedPreferences.use(doIt: SharedPreferences.() -> T): T {
    return useSharedPrefs { doIt() }
}

// use below functions if accessing from UI thread only
@Suppress("NOTHING_TO_INLINE")
inline fun SharedPreferences.getStringStrict(key: String, defValue: String?): String? {
    return use { getString(key, defValue) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun SharedPreferences.getIntStrict(key: String, defValue: Int): Int {
    return use { getInt(key, defValue) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun SharedPreferences.getBooleanStrict(key: String, defValue: Boolean): Boolean {
    return use { getBoolean(key, defValue) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun SharedPreferences.getStringSetStrict(key: String, defValue: Set<String>?): Set<String>? {
    return use { getStringSet(key, defValue) }
}

fun Context.resourceUri(resourceId: Int): Uri = with(resources) {
    Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(getResourcePackageName(resourceId))
        .appendPath(getResourceTypeName(resourceId))
        .appendPath(getResourceEntryName(resourceId))
        .build()
}

@Suppress("NOTHING_TO_INLINE")
inline fun mayThrowForegroundServiceStartNotAllowed(): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2

@Suppress("NOTHING_TO_INLINE")
inline fun needsMissingOnDestroyCallWorkarounds(): Boolean =
    Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE