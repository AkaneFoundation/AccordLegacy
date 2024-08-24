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
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import kotlin.math.hypot
import android.widget.OverScroller as AndroidOverScroller

/**
 * A class that provides the required methods from {@link android.widget.OverScroller}
 * class, of which some operations are replaced by the custom scroller.
 */
class OverScrollerAdapter(
    private val context: Context,
    private val interpolator: Interpolator
) {
    private val systemOverScroller: AndroidOverScroller by lazy {
        AndroidOverScroller(context, interpolator)
    }
    private val fluidScrollerX: FluidScroller by lazy {
        FluidScroller()
    }
    private val fluidScrollerY: FluidScroller by lazy {
        FluidScroller()
    }
    private var isSmoothAnimation = false
    private var animationStartTime: Long = -1L
    private var animationElapsedTime: Long = -1L

    fun computeScrollOffset(): Boolean {
        if (isSmoothAnimation) {
            return systemOverScroller.computeScrollOffset()
        }

        if (animationStartTime == -1L) {
            return false
        }
        animationElapsedTime = AnimationUtils.currentAnimationTimeMillis() - animationStartTime
        return !isFinished()
    }

    fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        isSmoothAnimation = true
        systemOverScroller.startScroll(startX, startY, dx, dy, duration)
    }

    /**
     * Start scrolling based on a fling gesture. The distance traveled will
     * depend on the initial velocity of the fling.
     *
     * @param startX Starting point of the scroll (X)
     * @param startY Starting point of the scroll (Y)
     * @param velocityX Initial velocity of the fling (X) measured in pixels per
     *            second.
     * @param velocityY Initial velocity of the fling (Y) measured in pixels per
     *            second
     * @param minX Minimum X value. The scroller will not scroll past this point
     *            unless overX > 0. If overfling is allowed, it will use minX as
     *            a springback boundary.
     * @param maxX Maximum X value. The scroller will not scroll past this point
     *            unless overX > 0. If overfling is allowed, it will use maxX as
     *            a springback boundary.
     * @param minY Minimum Y value. The scroller will not scroll past this point
     *            unless overY > 0. If overfling is allowed, it will use minY as
     *            a springback boundary.
     * @param maxY Maximum Y value. The scroller will not scroll past this point
     *            unless overY > 0. If overfling is allowed, it will use maxY as
     *            a springback boundary.
     */
    fun fling(
        startX: Int, startY: Int, velocityX: Int, velocityY: Int,
        minX: Int, maxX: Int, minY: Int, maxY: Int
    ) {
        isSmoothAnimation = false
        fluidScrollerX.fling(startX.toFloat(), velocityX.toFloat() / 1000)
        fluidScrollerY.fling(startY.toFloat(), velocityY.toFloat() / 1000)
        animationStartTime = AnimationUtils.currentAnimationTimeMillis()
    }

    fun abortAnimation() {
        if (isSmoothAnimation) {
            systemOverScroller.abortAnimation()
        } else {
            animationStartTime = -1
        }
    }

    fun getCurrX(): Int {
        if (isSmoothAnimation) {
            return systemOverScroller.currX
        }
        return fluidScrollerX.value(animationElapsedTime.toFloat()).value.toInt()
    }

    fun getCurrY(): Int {
        if (isSmoothAnimation) {
            return systemOverScroller.currY
        }
        return fluidScrollerY.value(animationElapsedTime.toFloat()).value.toInt()
    }

    /**
     * Returns the absolute value of the current velocity.
     *
     * @return The original velocity less the deceleration, norm of the X and Y
     * velocity vector. Note that the unit of the velocity is pixels per second.
     */
    fun getCurrVelocity(): Float {
        if (isSmoothAnimation) {
            return systemOverScroller.currVelocity
        }
        val vx = fluidScrollerX.value(animationElapsedTime.toFloat()).velocity
        val vy = fluidScrollerY.value(animationElapsedTime.toFloat()).velocity
        return hypot(vx, vy) * 1000
    }

    fun getFinalX(): Int {
        if (isSmoothAnimation) {
            return systemOverScroller.finalX
        }
        return fluidScrollerX.finalOffset().toInt()
    }

    fun getFinalY(): Int {
        if (isSmoothAnimation) {
            return systemOverScroller.finalY
        }
        return fluidScrollerY.finalOffset().toInt()
    }

    fun isFinished(): Boolean {
        if (isSmoothAnimation) {
            return systemOverScroller.isFinished
        }
        val xVal = fluidScrollerX.value(animationElapsedTime.toFloat())
        val yVal = fluidScrollerY.value(animationElapsedTime.toFloat())
        return xVal.isFinished && yVal.isFinished
    }
}