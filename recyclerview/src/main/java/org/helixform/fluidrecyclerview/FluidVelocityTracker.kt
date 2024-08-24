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
import android.graphics.PointF
import android.view.MotionEvent
import java.util.LinkedList
import kotlin.math.pow

private const val MAX_SAMPLE_COUNT = 4

class FluidVelocityTracker(context: Context) : VelocityTracker {

    private var positions: LinkedList<PointF> = LinkedList()
    private var times: LinkedList<Long> = LinkedList()

    private var calculatedVelocity: PointF = PointF(0f, 0f)

    private val displayMetrics = context.resources.displayMetrics

    override fun addMovement(event: MotionEvent) {
        if (positions.size >= MAX_SAMPLE_COUNT) {
            positions.removeFirst()
            times.removeFirst()
        }
        times.add(event.eventTime)
        positions.add(PointF(event.x, event.y))
    }

    override fun clear() {
        positions.clear()
        times.clear()
        calculatedVelocity = PointF(0f, 0f)
    }

    override fun computeCurrentVelocity(units: Int, maxVelocity: Float) {
        if (times.isEmpty()) {
            calculatedVelocity = PointF(0f, 0f)
            return
        }
        val firstTime = times.first()
        val transformedTimes = times.map { (it - firstTime).toFloat() }.toMutableList()
        val xValues = positions.map { it.x }.toTypedArray()
        val yValues = positions.map { it.y }.toTypedArray()
        var xVelocity = calculateRecurrenceRelationVelocity(transformedTimes.toTypedArray(), xValues)
        var yVelocity = calculateRecurrenceRelationVelocity(transformedTimes.toTypedArray(), yValues)
        if (xVelocity.pow(2) + yVelocity.pow(2) < 0.0576 * displayMetrics.density.pow(2)) {
            xVelocity = 0f
            yVelocity = 0f
        }
        calculatedVelocity = PointF(xVelocity * units, yVelocity * units)
    }

    override fun getXVelocity(id: Int): Float {
        return calculatedVelocity.x
    }

    override fun getYVelocity(id: Int): Float {
        return calculatedVelocity.y
    }

    private fun calculateRecurrenceRelationVelocity(
        times: Array<Float>,
        values: Array<Float>
    ): Float {
        if (times.size != values.size) {
            throw IllegalArgumentException("The number of times and values must be equal")
        }

        val sampleCount = times.size
        if (sampleCount < 2) {
            throw IllegalArgumentException("At least two points must be provided")
        }

        val points = values.zip(times).toMutableList()
        val samples = points
            .windowed(2)
            .mapNotNull { window ->
                val deltaTime = window[1].second - window[0].second
                if (deltaTime == 0f) {
                    // The two points are at the same time, so we can't calculate a velocity.
                    // Discard this sample.
                    null
                } else {
                    (window[1].first - window[0].first) / deltaTime
                }
            }

        // Save the velocity values of the last two times.
        var previousVelocity: Float? = null
        var currentVelocity: Float? = null
        samples.windowed(2).forEach { window ->
            val velocity = window[0] * 0.4f + window[1] * 0.6f
            if (currentVelocity != null) {
                previousVelocity = currentVelocity
                // Weighted average of the velocity with a ratio of 8:2 compared to the previous time.
                currentVelocity = currentVelocity!! * 0.8f + velocity * 0.2f
            } else {
                currentVelocity = velocity
            }
        }

        return if (currentVelocity != null) {
            if (previousVelocity != null) {
                previousVelocity!! * 0.75f + currentVelocity!! * 0.25f
            } else {
                currentVelocity!!
            }
        } else {
            samples.first()
        }
    }
}