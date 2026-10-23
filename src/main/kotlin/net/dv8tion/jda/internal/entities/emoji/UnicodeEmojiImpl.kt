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
import net.dv8tion.jda.internal.utils.EncodingUtil
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull

class UnicodeEmojiImpl(
    private val name: String,
) : UnicodeEmoji,
    EmojiUnion {
    @Nonnull
    override fun getName(): String = name

    @Nonnull
    override fun getAsReactionCode(): String = name

    @Nonnull
    override fun getAsCodepoints(): String = EncodingUtil.encodeCodepoints(name)

    @Nonnull
    override fun toData(): DataObject = DataObject.empty().put("name", name)

    override fun hashCode(): Int = Objects.hash(name)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is UnicodeEmoji) {
            return false
        }
        return name == other.name
    }

    override fun toString(): String =
        EntityString(this)
            .addMetadata("codepoints", asCodepoints)
            .toString()

    @Nonnull
    override fun asUnicode(): UnicodeEmoji = this

    @Nonnull
    override fun asCustom(): CustomEmoji = throw IllegalStateException("Cannot convert UnicodeEmoji into CustomEmoji!")

    @Nonnull
    override fun asRich(): RichCustomEmoji = throw IllegalStateException("Cannot convert UnicodeEmoji into RichCustomEmoji!")

    @Nonnull
    override fun asApplication(): ApplicationEmoji = throw IllegalStateException("Cannot convert UnicodeEmoji to ApplicationEmoji!")
}
