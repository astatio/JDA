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

import net.dv8tion.jda.api.entities.sticker.GuildSticker
import net.dv8tion.jda.api.entities.sticker.RichSticker
import net.dv8tion.jda.api.entities.sticker.StandardSticker
import net.dv8tion.jda.api.entities.sticker.Sticker.StickerFormat
import net.dv8tion.jda.api.entities.sticker.StickerUnion
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Collections
import javax.annotation.Nonnull

abstract class RichStickerImpl(
    id: Long,
    format: StickerFormat,
    name: String,
    tags: Set<String>,
    description: String,
) : StickerItemImpl(id, format, name),
    RichSticker,
    StickerUnion {
    @JvmField protected var tags: Set<String> = Collections.unmodifiableSet(tags)

    @JvmField protected var description: String = description

    @Nonnull
    override fun asStandardSticker(): StandardSticker =
        throw IllegalStateException("Cannot convert sticker of type $type to StandardSticker!")

    @Nonnull
    override fun asGuildSticker(): GuildSticker = throw IllegalStateException("Cannot convert sticker of type $type to GuildSticker!")

    @Nonnull
    override fun getTags(): Set<String> = tags

    @Nonnull
    override fun getDescription(): String = description

    fun setTags(tags: Set<String>): RichStickerImpl {
        this.tags = Collections.unmodifiableSet(tags)
        return this
    }

    fun setDescription(description: String): RichStickerImpl {
        this.description = description
        return this
    }

    override fun toString(): String = EntityString(this).setType(type).setName(name).toString()
}
