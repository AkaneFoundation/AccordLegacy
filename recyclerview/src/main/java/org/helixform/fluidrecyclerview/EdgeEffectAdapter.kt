/*
 * Copyright (C) 2023 Helixform
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.helixform.fluidrecyclerview

import android.content.Context
import android.util.TypedValue
import android.view.animation.AnimationUtils
import kotlin.math.abs
import kotlin.math.sign

/**
 * A class that provides the required methods from {@link android.widget.EdgeEffect}
 * class, of which some operations are replaced by the custom edge effect.
 */
class EdgeEffectAdapter(context: Context) {

    companion object {
        private const val MODE_IDLE: Int = 0
        private const val MODE_PULLING: Int = 1
        private const val MODE_SPRING_BACK: Int = 2
    }

    private var viewportRange = 0f

    private var animationStartTime: Long = -1L
    private var animationElapsedTime: Long = -1L

    private var springBack = FluidSpringBack()
    private var currentDistance = 0f

    private var totalPullDistance = 0f
    private var pullDistanceSign = 0f

    private var mode: Int = MODE_IDLE
    var isFinished = true
        private set

    private val displayMetrics = context.resources.displayMetrics

    /**
     * Returns the pull distance needed to be released to remove the showing effect.
     *
     * @return The pull distance that must be released to remove the showing effect.
     */
    fun getDistance(): Float {
        return currentDistance
    }

    /**
     * Call when the effect absorbs an impact at the given velocity.
     * Used when a fling reaches the scroll boundary.
     *
     * @param velocity Velocity at impact in pixels per second.
     */
    fun onAbsorb(velocity: Int) {
        val velocityPxPerMs = velocity / 1000f
        val velocityDpPerMs = velocityPxPerMs.dp()

        animationStartTime = AnimationUtils.currentAnimationTimeMillis()
        springBack.absorb(velocityDpPerMs, 0f)
        mode = MODE_SPRING_BACK
        isFinished = false
    }

    /**
     * A view should call this when content is pulled away from an edge by the user.
     * This will update the state of the current visual effect and its associated animation.
     * The host view should always {@link android.view.View#invalidate()} after this
     * and draw the results accordingly.
     *
     * @param deltaDistance Change in distance since the last call.
     * @param forReleasing Is releasing the edge effect.
     * @return The amount of <code>deltaDistance</code> that was consumed, a number between
     * 0 and <code>deltaDistance</code>.
     */
    fun onPullDistance(deltaDistance: Float, forReleasing: Boolean): Float {
        val releasing = deltaDistance.sign == currentDistance.sign
        if (releasing != forReleasing) {
            // The rubber band is being stretched tight.
            return 0f
        }

        val dpDelta = deltaDistance.dp()
        if (mode != MODE_PULLING) {
            // Calculate the distance pulled by reversing the current position.
            totalPullDistance =
                FluidRubberBand.distance(
                    abs(currentDistance.dp()),
                    viewportRange
                ) * -currentDistance.sign
            pullDistanceSign = if (totalPullDistance == 0f) {
                dpDelta.sign
            } else {
                totalPullDistance.sign
            }
        }
        totalPullDistance += dpDelta
        var distance = totalPullDistance
        if (distance.sign != pullDistanceSign) {
            // The rubber band is being pulled in the opposite direction.
            distance = 0f
        }
        val offset = FluidRubberBand.offset(abs(distance), viewportRange)
        currentDistance = (-offset * distance.sign).px()
        mode = MODE_PULLING
        isFinished = false
        return if (releasing) {
            deltaDistance
        } else {
            0f
        }
    }

    fun onRelease() {
        val initialValue = currentDistance.dp()

        animationStartTime = AnimationUtils.currentAnimationTimeMillis();
        springBack.absorb(-initialValue / 100f, initialValue)
        mode = MODE_SPRING_BACK
        isFinished = false
    }

    fun computeScrollOffset(): Boolean {
        if (animationStartTime == -1L) {
            return false
        }

        if (mode == MODE_PULLING) {
            // The edge effect is in pulling mode, there is no need
            // to update any animation state.
            return true
        }

        animationElapsedTime = AnimationUtils.currentAnimationTimeMillis() - animationStartTime
        val value = springBack.value(animationElapsedTime.toFloat())
        currentDistance = value.value.px()
        isFinished = value.isFinished

        if (isFinished) {
            mode = MODE_IDLE
        }

        return !isFinished
    }

    fun updateViewportRange(px: Int) {
        viewportRange = px.toFloat().dp()
    }

    private fun Float.px(): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, this, displayMetrics
        )
    }

    private fun Float.dp(): Float {
        return this / displayMetrics.density
    }
}