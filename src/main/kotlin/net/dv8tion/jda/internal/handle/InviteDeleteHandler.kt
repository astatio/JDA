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

import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl

class InviteDeleteHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getUnsignedLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }
        val guild = getJDA().getGuildById(guildId)
        if (guild == null) {
            EventCache.LOG.debug("Caching INVITE_DELETE for unknown guild {}", guildId)
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            return null
        }
        val channelId = content.getUnsignedLong("channel_id")
        val channel = guild.getGuildChannelById(channelId)
        if (channel == null) {
            EventCache.LOG.debug("Caching INVITE_DELETE for unknown channel {} in guild {}", channelId, guildId)
            getJDA()
                .getEventCache()
                .cache(EventCache.Type.CHANNEL, channelId, responseNumber, allContent, this::handle)
            return null
        }

        val code = content.getString("code")
        getJDA().handleEvent(GuildInviteDeleteEvent(getJDA(), responseNumber, code, channel))
        return null
    }
}
