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

open class MutableTriple<LEFT, MIDDLE, RIGHT> private constructor(
    @JvmField
    var middle: MIDDLE,
    left: LEFT?,
    right: RIGHT?,
) : MutablePair<LEFT, RIGHT>(left, right) {
    open fun getMiddle(): MIDDLE = middle

    open fun setMiddle(middle: MIDDLE) {
        this.middle = middle
    }

    companion object {
        @JvmStatic
        fun <LEFT, MIDDLE, RIGHT> of(
            left: LEFT,
            middle: MIDDLE,
            right: RIGHT,
        ): MutableTriple<LEFT, MIDDLE, RIGHT> = MutableTriple(middle, left, right)
    }
}
