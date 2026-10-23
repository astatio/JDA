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

package net.dv8tion.jda.internal.entities.emoji

import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import javax.annotation.Nonnull

class CustomEmojiImpl(
    private val name: String,
    private val id: Long,
    private val animated: Boolean,
) : CustomEmoji,
    EmojiUnion {
    @Nonnull
    override fun getAsReactionCode(): String = "$name:$id"

    @Nonnull
    override fun getName(): String = name

    override fun getIdLong(): Long = id

    override fun isAnimated(): Boolean = animated

    @Nonnull
    override fun toData(): DataObject =
        DataObject
            .empty()
            .put("name", name)
            .put("id", id)
            .put("animated", animated)

    @Nonnull
    override fun getAsMention(): String = Helpers.format("<%s:%s:%s>", if (animated) "a" else "", name, id)

    @Nonnull
    override fun getFormatted(): String = super<CustomEmoji>.getFormatted()

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is CustomEmoji) {
            return false
        }
        return this.id == other.idLong
    }

    override fun toString(): String = EntityString(this).setName(name).toString()

    @Nonnull
    override fun asUnicode(): UnicodeEmoji = throw IllegalStateException("Cannot convert CustomEmoji into UnicodeEmoji!")

    @Nonnull
    override fun asCustom(): CustomEmoji = this

    @Nonnull
    override fun asRich(): RichCustomEmoji = throw IllegalStateException("Cannot convert CustomEmoji to RichCustomEmoji!")

    @Nonnull
    override fun asApplication(): ApplicationEmoji = throw IllegalStateException("Cannot convert CustomEmoji to ApplicationEmoji!")
}
