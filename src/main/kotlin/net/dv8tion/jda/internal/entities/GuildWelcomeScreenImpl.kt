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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildWelcomeScreen
import net.dv8tion.jda.api.entities.GuildWelcomeScreen.Channel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.managers.GuildWelcomeScreenManager
import net.dv8tion.jda.api.utils.data.DataObject
import javax.annotation.Nonnull
import javax.annotation.Nullable

class GuildWelcomeScreenImpl(
    private val guild: Guild?,
    private val description: String?,
    private val channels: List<Channel>,
) : GuildWelcomeScreen {
    @Nullable
    override fun getGuild(): Guild? = guild

    @Nonnull
    override fun getManager(): GuildWelcomeScreenManager {
        if (guild == null) {
            throw IllegalStateException("Cannot modify a guild welcome screen from an Invite")
        }
        return guild.modifyWelcomeScreen()
    }

    @Nullable
    override fun getDescription(): String? = description

    @Nonnull
    override fun getChannels(): List<Channel> = channels

    /**
     * POJO for the recommended channels information provided by a welcome screen.
     * <br>Recommended channels are shown in the welcome screen after joining a server.
     *
     * @see GuildWelcomeScreen.getChannels
     */
    class ChannelImpl(
        private val guild: Guild?,
        private val id: Long,
        private val description: String,
        private val emoji: EmojiUnion?,
    ) : GuildWelcomeScreen.Channel {
        @Nullable
        override fun getGuild(): Guild? = guild

        override fun getIdLong(): Long = id

        @Nullable
        override fun getChannel(): GuildChannel? {
            if (guild == null) {
                return null
            }

            return guild.getGuildChannelById(id)
        }

        @Nonnull
        override fun getDescription(): String = description

        @Nullable
        override fun getEmoji(): EmojiUnion? = emoji

        @Nonnull
        override fun toData(): DataObject {
            val data = DataObject.empty()
            data.put("channel_id", id)
            data.put("description", description)
            if (emoji != null) {
                if (emoji.getType() == Emoji.Type.CUSTOM) {
                    data.put("emoji_id", (emoji as CustomEmoji).getId())
                }
                data.put("emoji_name", emoji.getName())
            }

            return data
        }
    }
}
