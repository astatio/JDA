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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.managers.ApplicationEmojiManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.managers.ApplicationEmojiManagerImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.EntityString
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ApplicationEmojiImpl(
    private val id: Long,
    private val api: JDAImpl,
    owner: User?,
) : ApplicationEmoji,
    EmojiUnion {
    private var animated: Boolean = false
    private var name: String? = null
    private val owner: User? = owner

    @Nonnull
    override fun getType(): Emoji.Type = Emoji.Type.CUSTOM

    @Nonnull
    override fun getAsReactionCode(): String = "$name:$id"

    @Nonnull
    override fun toData(): DataObject =
        DataObject
            .empty()
            .put("name", name)
            .put("animated", animated)
            .put("id", id)

    @Nonnull
    override fun getName(): String = name as String

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getJDA(): JDA = api

    @Nullable
    override fun getOwner(): User? = owner

    @Nonnull
    override fun getManager(): ApplicationEmojiManager = ApplicationEmojiManagerImpl(this)

    override fun isAnimated(): Boolean = animated

    @Nonnull
    override fun delete(): RestAction<Void> {
        val route: Route.CompiledRoute =
            Route.Applications.DELETE_APPLICATION_EMOJI.compile(
                getJDA().selfUser.applicationId,
                id.toString(),
            )
        return RestActionImpl(getJDA(), route)
    }

    // -- Setters --

    fun setName(name: String): ApplicationEmojiImpl {
        this.name = name
        return this
    }

    fun setAnimated(animated: Boolean): ApplicationEmojiImpl {
        this.animated = animated
        return this
    }

    // -- Object overrides --

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ApplicationEmojiImpl) {
            return false
        }
        return this.id == other.idLong
    }

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun toString(): String = EntityString(this).setName(getName()).toString()

    @Nonnull
    override fun asUnicode(): UnicodeEmoji = throw IllegalStateException("Cannot convert ApplicationEmoji to UnicodeEmoji!")

    @Nonnull
    override fun asCustom(): CustomEmoji = this

    @Nonnull
    override fun asRich(): RichCustomEmoji = throw IllegalStateException("Cannot convert ApplicationEmoji to RichCustomEmoji!")

    @Nonnull
    override fun asApplication(): ApplicationEmoji = this
}
