/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.internal.utils.tuple

/**
 * This class has been copied from [Lang 3](https://commons.apache.org/proper/commons-lang/)
 *
 * A mutable pair consisting of two `Object` elements.
 *
 * Not thread-safe.
 *
 * @param L the left element type
 * @param R the right element type
 */
open class MutablePair<L, R> : Pair<L, R> {
    /** Left object */
    @JvmField
    var left: L? = null

    /** Right object */
    @JvmField
    var right: R? = null

    /**
     * Create a new pair instance of two nulls.
     */
    constructor() : super()

    /**
     * Create a new pair instance.
     *
     * @param left the left value, may be null
     * @param right the right value, may be null
     */
    constructor(left: L?, right: R?) : super() {
        this.left = left
        this.right = right
    }

    override fun getLeft(): L? = left

    /**
     * Sets the left element of the pair.
     *
     * @param left the new value of the left element, may be null
     */
    open fun setLeft(left: L?) {
        this.left = left
    }

    override fun getRight(): R? = right

    /**
     * Sets the right element of the pair.
     *
     * @param right the new value of the right element, may be null
     */
    open fun setRight(right: R?) {
        this.right = right
    }

    companion object {
        /**
         * Obtains a mutable pair of from two objects inferring the generic types.
         *
         * This factory allows the pair to be created using inference to obtain the generic types.
         *
         * @param L the left element type
         * @param R the right element type
         * @param left the left element, may be null
         * @param right the right element, may be null
         *
         * @return a pair formed from the two parameters, not null
         */
        @JvmStatic
        fun <L, R> of(
            left: L?,
            right: R?,
        ): MutablePair<L, R> = MutablePair(left, right)
    }
}
