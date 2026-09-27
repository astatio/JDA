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

import net.dv8tion.jda.api.entities.sticker.Sticker.StickerFormat
import net.dv8tion.jda.api.entities.sticker.StickerItem
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull

open class StickerItemImpl(
    @JvmField protected val id: Long,
    @JvmField protected val format: StickerFormat,
    @JvmField protected var name: String,
) : StickerItem {
    override fun getIdLong(): Long = id

    @Nonnull
    override fun getFormatType(): StickerFormat = format

    @Nonnull
    override fun getName(): String = name

    fun setName(name: String): StickerItemImpl {
        this.name = name
        return this
    }

    override fun toString(): String = EntityString(this).setName(name).toString()

    override fun hashCode(): Int = Objects.hash(id, format, name)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is StickerItemImpl) {
            return false
        }
        return id == other.id && format == other.format && Objects.equals(name, other.name)
    }
}
