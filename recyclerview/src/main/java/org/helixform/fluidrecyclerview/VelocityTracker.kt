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

import android.view.MotionEvent

/**
 * Defines the common methods for different velocity tracker implementations.
 */
interface VelocityTracker {
    /**
     * Add a user's movement to the tracker.  You should call this for the
     * initial {@link MotionEvent#ACTION_DOWN}, the following
     * {@link MotionEvent#ACTION_MOVE} events that you receive, and the
     * final {@link MotionEvent#ACTION_UP}.  You can, however, call this
     * for whichever events you desire.
     *
     * @param event The MotionEvent you received and would like to track.
     */
    fun addMovement(event: MotionEvent)

    /**
     * Reset the velocity tracker back to its initial state.
     */
    fun clear()

    /**
     * Compute the current velocity based on the points that have been
     * collected.  Only call this when you actually want to retrieve velocity
     * information, as it is relatively expensive.  You can then retrieve
     * the velocity with {@link #getXVelocity()} and
     * {@link #getYVelocity()}.
     *
     * @param units The units you would like the velocity in.  A value of 1
     * provides pixels per millisecond, 1000 provides pixels per second, etc.
     * @param maxVelocity The maximum velocity that can be computed by this method.
     * This value must be declared in the same unit as the units parameter. This value
     * must be positive.
     */
    fun computeCurrentVelocity(units: Int, maxVelocity: Float)

    /**
     * Retrieve the last computed X velocity.  You must first call
     * {@link #computeCurrentVelocity(int, float)} before calling this function.
     *
     * @param id Which pointer's velocity to return.
     * @return The previously computed X velocity.
     */
    fun getXVelocity(id: Int): Float

    /**
     * Retrieve the last computed Y velocity.  You must first call
     * {@link #computeCurrentVelocity(int, float)} before calling this function.
     *
     * @param id Which pointer's velocity to return.
     * @return The previously computed Y velocity.
     */
    fun getYVelocity(id: Int): Float
}