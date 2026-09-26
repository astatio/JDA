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

import java.io.Serializable
import java.util.Objects

// The Java original declared no serialVersionUID either, so the compiler-computed value is
// preserved here (declaring one would change serialization compatibility with the Java build).

/**
 * This class has been copied from [Lang 3](https://commons.apache.org/proper/commons-lang/)
 *
 * A pair consisting of two elements.
 *
 * This class is an abstract implementation defining the basic API.
 * It refers to the elements as 'left' and 'right'.
 *
 * Subclass implementations may be mutable or immutable.
 * However, there is no restriction on the type of the stored objects that may be stored.
 * If mutable objects are stored in the pair, then the pair itself effectively becomes mutable.
 *
 * @param L the left element type
 * @param R the right element type
 */
@Suppress("SerialVersionUIDInSerializableClass")
abstract class Pair<L, R> : Serializable {
    /**
     * Gets the left element from this pair.
     *
     * When treated as a key-value pair, this is the key.
     *
     * @return the left element, may be null
     */
    abstract fun getLeft(): L?

    /**
     * Gets the right element from this pair.
     *
     * When treated as a key-value pair, this is the value.
     *
     * @return the right element, may be null
     */
    abstract fun getRight(): R?

    /**
     * Compares this pair to another based on the two elements.
     *
     * @param obj the object to compare to, null returns false
     *
     * @return true if the elements of the pair are equal
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other is Pair<*, *>) {
            return Objects.equals(getLeft(), other.getLeft()) && Objects.equals(getRight(), other.getRight())
        }
        return false
    }

    /**
     * Returns a suitable hash code.
     * The hash code follows the definition in `Map.Entry`.
     *
     * @return the hash code
     */
    override fun hashCode(): Int =
        // see Map.Entry API specification
        Objects.hashCode(getLeft()) xor Objects.hashCode(getRight())

    /**
     * Returns a String representation of this pair using the format `($left,$right)`.
     *
     * @return a string describing this object, not null
     */
    override fun toString(): String = "(" + getLeft() + ',' + getRight() + ')'

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
        ): Pair<L, R> = ImmutablePair(left, right)
    }
}
