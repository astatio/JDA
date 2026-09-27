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

package net.dv8tion.jda.internal.entities.sticker

import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import net.dv8tion.jda.internal.utils.EntityString

class StickerSnowflakeImpl(
    private val id: Long,
) : StickerSnowflake {
    override fun getIdLong(): Long = id

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is StickerSnowflakeImpl) {
            return false
        }
        return other.id == id
    }

    override fun toString(): String = EntityString(this).toString()
}
