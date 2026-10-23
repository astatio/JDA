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

import net.dv8tion.jda.api.entities.sticker.StandardSticker
import net.dv8tion.jda.api.entities.sticker.Sticker.StickerFormat
import net.dv8tion.jda.internal.utils.EntityString
import javax.annotation.Nonnull

class StandardStickerImpl(
    id: Long,
    format: StickerFormat,
    name: String,
    tags: Set<String>,
    description: String,
    private val packId: Long,
    private val sortValue: Int,
) : RichStickerImpl(id, format, name, tags, description),
    StandardSticker {
    @Nonnull
    override fun asStandardSticker(): StandardSticker = this

    override fun getPackIdLong(): Long = packId

    override fun getSortValue(): Int = sortValue

    override fun toString(): String =
        EntityString(this)
            .setName(name)
            .addMetadata("pack", getPackId())
            .toString()

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is StandardStickerImpl) {
            return false
        }
        return id == other.id // Standard stickers shouldn't change, so we can just compare id
    }
}
