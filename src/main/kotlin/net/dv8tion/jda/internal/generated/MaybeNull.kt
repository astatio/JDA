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

package net.dv8tion.jda.internal.generated

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull

@JsonSerialize(using = MaybeNullSerializer::class)
@JsonDeserialize(using = MaybeNullDeserializer::class)
class MaybeNull<T>(
    private val value: T,
) {
    fun value(): T = value

    fun isPresent(): Boolean = value != null

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is MaybeNull<*>) {
            return false
        }
        return Objects.equals(value, other.value)
    }

    override fun hashCode(): Int = Objects.hash(value)

    override fun toString(): String = EntityString(this).addMetadata("value", value).toString()

    companion object {
        private val EMPTY: MaybeNull<*> = MaybeNull<Any?>(null)

        @Nonnull
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T> empty(): MaybeNull<T> = EMPTY as MaybeNull<T>
    }
}
