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

import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.requests.WebSocketClient

class ChannelCreateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val type = ChannelType.fromId(content.getInt("type"))

        var guildId = 0L
        val jda = getJDA()
        if (type.isGuild) {
            guildId = content.getLong("guild_id")
            if (jda.getGuildSetupController().isLocked(guildId)) {
                return guildId
            }
        }

        val channel: Channel? = buildChannel(type, content, guildId)
        if (channel == null) {
            WebSocketClient.LOG.debug(
                "Discord provided an CREATE_CHANNEL event with an unknown channel type! JSON: {}",
                content,
            )
            return null
        }

        jda.handleEvent(ChannelCreateEvent(jda, responseNumber, channel))

        return null
    }

    private fun buildChannel(
        type: ChannelType,
        content: DataObject,
        guildId: Long,
    ): Channel? {
        val builder: EntityBuilder = getJDA().getEntityBuilder()
        return when (type) {
            ChannelType.TEXT -> builder.createTextChannel(content, guildId)
            ChannelType.NEWS -> builder.createNewsChannel(content, guildId)
            ChannelType.VOICE -> builder.createVoiceChannel(content, guildId)
            ChannelType.STAGE -> builder.createStageChannel(content, guildId)
            ChannelType.CATEGORY -> builder.createCategory(content, guildId)
            ChannelType.FORUM -> builder.createForumChannel(content, guildId)
            ChannelType.MEDIA -> builder.createMediaChannel(content, guildId)

            else -> null
        }
    }
}
