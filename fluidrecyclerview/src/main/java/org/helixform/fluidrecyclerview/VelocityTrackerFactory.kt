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
import java.lang.RuntimeException

/**
 * A helper class to create different types of {@link VelocityTracker}
 * instances.
 */
class VelocityTrackerFactory private constructor() {
    companion object {
        /**
         * Velocity Tracker Impl: System.
         * This is the default implementation widely used across
         * Android frameworks and apps.
         */
        const val VELOCITY_TRACKER_IMPL_SYSTEM: Int = 1

        /**
         * Velocity Tracker Impl: Optimized.
         * An implementation with an iOS-like algorithm. This is
         * recommended in this library.
         */
        const val VELOCITY_TRACKER_IMPL_OPTIMIZED: Int = 2

        /**
         * Creates a velocity tracker with the specified implementation
         * type.
         *
         * @param impl The implementation type.
         * @return The velocity tracker.
         */
        @JvmStatic
        fun create(context: Context, impl: Int): VelocityTracker {
            when (impl) {
                VELOCITY_TRACKER_IMPL_SYSTEM -> return SystemVelocityTrackerAdapter()
                VELOCITY_TRACKER_IMPL_OPTIMIZED -> return FluidVelocityTracker(context)
            }

            throw RuntimeException("impl is not supported")
        }

        @JvmStatic
        fun create(context: Context): VelocityTracker {
            return create(context, VELOCITY_TRACKER_IMPL_OPTIMIZED)
        }
    }
}