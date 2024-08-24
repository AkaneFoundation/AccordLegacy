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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

class FluidSpringBack {

    private var c1: Float = 0f
    private var c2: Float = 0f
    private var lambda: Float = 0f

    private val valueThreshold = 0.1f
    private val velocityThreshold = 1e-2f

    data class SpringBackValue(
        val value: Float,
        val isFinished: Boolean
    )

    fun absorb(velocity: Float, initialValue: Float) {
        lambda = 2.0f * PI.toFloat() / 0.575f
        c1 = initialValue
        // The formula needs to be calculated in units of points per second.
        c2 = velocity * 1000f + lambda * initialValue
    }

    fun value(time: Float): SpringBackValue {
        // Convert time from milliseconds to seconds.
        val t = time / 1000f
        val offset = (c1 + c2 * t) * exp(-lambda * t)

        val velocity = velocityAt(t)
        // The velocity threshold is in units of points per millisecond.
        // We need to convert velocity to match the unit.
        return if (abs(offset) < valueThreshold && abs(velocity) / 1000f < velocityThreshold) {
            SpringBackValue(0f, true)
        } else {
            SpringBackValue(offset, false)
        }
    }

    private fun velocityAt(time: Float): Float {
        return (c2 - lambda * (c1 + c2 * time)) * exp(-lambda * time)
    }
}