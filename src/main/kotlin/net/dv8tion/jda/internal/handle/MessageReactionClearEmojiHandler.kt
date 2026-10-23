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

package net.dv8tion.jda.internal.handle

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEmojiEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.requests.WebSocketClient

class MessageReactionClearEmojiHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getUnsignedLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }
        val guild: Guild? = getJDA().getGuildById(guildId)
        if (guild == null) {
            EventCache.LOG.debug("Caching MESSAGE_REACTION_REMOVE_EMOJI event for unknown guild {}", guildId)
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            return null
        }

        val channelId = content.getUnsignedLong("channel_id")
        val channel = guild.getChannelById(GuildMessageChannel::class.java, channelId)
        if (channel == null) {
            // If discord adds message support for unexpected types in the future,
            // drop the event instead of caching it
            val actual: GuildChannel? = guild.getGuildChannelById(channelId)
            if (actual != null) {
                WebSocketClient.LOG.debug(
                    "Dropping MESSAGE_REACTION_REMOVE_EMOJI for unexpected channel of type {}",
                    actual.getType(),
                )
                return null
            }

            EventCache.LOG.debug("Caching MESSAGE_REACTION_REMOVE_EMOJI event for unknown channel {}", channelId)
            getJDA().getEventCache().cache(EventCache.Type.CHANNEL, channelId, responseNumber, allContent, this::handle)
            return null
        }

        val messageId = content.getUnsignedLong("message_id")
        val emoji = content.getObject("emoji")
        val reactionEmoji: EmojiUnion = EntityBuilder.createEmoji(emoji)

        // We don't know if it is a normal or super reaction
        val self = booleanArrayOf(false, false)

        val reaction = MessageReaction(api, channel, reactionEmoji, channelId, messageId, self, null)

        getJDA().handleEvent(
            MessageReactionRemoveEmojiEvent(getJDA(), responseNumber, messageId, channel, reaction),
        )
        return null
    }
}
