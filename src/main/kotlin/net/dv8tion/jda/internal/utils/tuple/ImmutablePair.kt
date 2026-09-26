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
 * An immutable pair consisting of two `Object` elements.
 *
 * Although the implementation is immutable, there is no restriction on the objects
 * that may be stored. If mutable objects are stored in the pair, then the pair
 * itself effectively becomes mutable. The class is also `final`, so a subclass
 * can not add undesirable behaviour.
 *
 * Thread-safe if both paired objects are thread-safe.
 *
 * @param L the left element type
 * @param R the right element type
 */
class ImmutablePair<L, R>(
    /** Left object */
    @JvmField
    val left: L,
    /** Right object */
    @JvmField
    val right: R,
) : Pair<L, R>() {
    override fun getLeft(): L? = left

    override fun getRight(): R? = right

    companion object {
        /**
         * Obtains an immutable pair of from two objects inferring the generic types.
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
            left: L,
            right: R,
        ): ImmutablePair<L, R> = ImmutablePair(left, right)
    }
}
