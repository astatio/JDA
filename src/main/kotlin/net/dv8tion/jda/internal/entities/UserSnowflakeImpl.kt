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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.internal.utils.EntityString
import javax.annotation.Nonnull

open class UserSnowflakeImpl(
    @JvmField protected val id: Long,
) : UserSnowflake {
    override fun getIdLong(): Long = this.id

    @Nonnull
    override fun getAsMention(): String = "<@" + id + ">"

    @Nonnull
    override fun getDefaultAvatarId(): String = ((id shr TIMESTAMP_SHIFT) % DEFAULT_AVATAR_COUNT).toString()

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is UserSnowflakeImpl) {
            return false
        }
        return other.idLong == this.id
    }

    override fun toString(): String = EntityString(this).toString()

    private companion object {
        // Discord snowflake layout: 42-bit timestamp followed by 10-bit machine id and 12-bit increment
        const val TIMESTAMP_SHIFT = 22
        const val DEFAULT_AVATAR_COUNT = 6
    }
}
