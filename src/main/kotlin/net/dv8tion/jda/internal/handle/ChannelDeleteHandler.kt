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

import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.requests.WebSocketClient

class ChannelDeleteHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val type = ChannelType.fromId(content.getInt("type"))

        var guildId = 0L
        if (type.isGuild) {
            guildId = content.getLong("guild_id")
            if (getJDA().getGuildSetupController().isLocked(guildId)) {
                return guildId
            }
        }

        val guild = getJDA().getGuildById(guildId) as GuildImpl?
        val channelId = content.getLong("id")

        if (guild == null) {
            val channel: PrivateChannel? = getJDA().getChannelsView().remove(ChannelType.PRIVATE, channelId)
            if (channel == null) {
                WebSocketClient.LOG.debug(
                    "CHANNEL_DELETE attempted to delete a private channel that is not yet cached. JSON: {}",
                    content,
                )
            }
            return null
        }

        val channel: GuildChannel? = guild.getGuildChannelById(channelId)
        if (channel == null) {
            WebSocketClient.LOG.debug(
                "CHANNEL_DELETE attempted to delete a guild channel that is not yet cached. JSON: {}",
                content,
            )
            return null
        }

        guild.uncacheChannel(channel, false)

        getJDA().handleEvent(ChannelDeleteEvent(getJDA(), responseNumber, channel))

        // Deleting any scheduled events associated to the deleted channel as they are deleted when
        // the channel gets deleted.
        // There is no delete event for the deletion of scheduled events in this case, so we do this
        // to keep the cache in sync.
        val location = java.lang.Long.toUnsignedString(channelId)
        guild
            .getScheduledEventsView()
            .stream()
            .filter { scheduledEvent ->
                scheduledEvent.getType().isChannel() &&
                    scheduledEvent.getLocation() == location
            }.forEach { scheduledEvent -> guild.getScheduledEventsView().remove(scheduledEvent.getIdLong()) }

        getJDA().getEventCache().clear(EventCache.Type.CHANNEL, channelId)
        return null
    }
}
