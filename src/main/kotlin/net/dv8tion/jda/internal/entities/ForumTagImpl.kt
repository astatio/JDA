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

import net.dv8tion.jda.api.entities.channel.forums.ForumTag
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.emoji.CustomEmojiImpl
import net.dv8tion.jda.internal.utils.EntityString
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ForumTagImpl(
    id: Long,
) : ForumTagSnowflakeImpl(id),
    ForumTag {
    private var moderated: Boolean = false
    private var name: String? = null
    private var position: Int = 0
    private var emoji: Emoji? = null

    override fun getPosition(): Int = position

    @Nonnull
    override fun getName(): String = name!!

    override fun isModerated(): Boolean = moderated

    @Nullable
    override fun getEmoji(): EmojiUnion? = emoji as EmojiUnion?

    fun setModerated(moderated: Boolean): ForumTagImpl {
        this.moderated = moderated
        return this
    }

    fun setName(name: String): ForumTagImpl {
        this.name = name
        return this
    }

    fun setPosition(position: Int): ForumTagImpl {
        this.position = position
        return this
    }

    fun setEmoji(json: DataObject): ForumTagImpl {
        val id = json.getUnsignedLong("emoji_id", 0)
        if (id != 0L) {
            this.emoji = CustomEmojiImpl(json.getString("emoji_name", ""), id, false)
        } else if (!json.isNull("emoji_name")) {
            this.emoji = Emoji.fromUnicode(json.getString("emoji_name"))
        } else {
            this.emoji = null
        }
        return this
    }

    override fun toString(): String = EntityString(this).apply { if (name != null) setName(name!!) }.toString()
}
