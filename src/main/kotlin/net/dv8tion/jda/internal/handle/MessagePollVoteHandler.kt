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
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.poll.MessagePollVoteAddEvent
import net.dv8tion.jda.api.events.message.poll.MessagePollVoteRemoveEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.requests.WebSocketClient

class MessagePollVoteHandler(
    api: JDAImpl,
    private val add: Boolean,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val answerId = content.getLong("answer_id")
        val userId = content.getUnsignedLong("user_id")
        val messageId = content.getUnsignedLong("message_id")
        val channelId = content.getUnsignedLong("channel_id")
        val guildId = content.getUnsignedLong("guild_id", 0)

        if (api.getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        val guild: Guild? = api.getGuildById(guildId)
        var channel = api.getChannelById(MessageChannel::class.java, channelId)
        if (channel == null) {
            if (guild != null) {
                val actual: GuildChannel? = guild.getGuildChannelById(channelId)
                if (actual != null) {
                    WebSocketClient.LOG.debug(
                        "Dropping message poll vote event for unexpected channel of type {}",
                        actual.getType(),
                    )
                    return null
                }
            }

            if (guildId != 0L) {
                api.getEventCache().cache(EventCache.Type.CHANNEL, channelId, responseNumber, allContent, this::handle)
                EventCache.LOG.debug("Received a vote for a channel that JDA does not currently have cached")
                return null
            }

            channel =
                getJDA()
                    .getEntityBuilder()
                    .createPrivateChannel(DataObject.empty().put("id", channelId))
        }

        if (add) {
            api.handleEvent(MessagePollVoteAddEvent(channel, responseNumber, messageId, userId, answerId))
        } else {
            api.handleEvent(MessagePollVoteRemoveEvent(channel, responseNumber, messageId, userId, answerId))
        }

        return null
    }
}
