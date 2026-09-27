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

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.GuildBanEvent
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl

class GuildBanHandler(
    api: JDAImpl,
    private val banned: Boolean,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val id = content.getLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(id)) {
            return id
        }

        val userJson = content.getObject("user")
        val guild = getJDA().getGuildById(id) as GuildImpl?
        if (guild == null) {
            getJDA().getEventCache().cache(EventCache.Type.GUILD, id, responseNumber, allContent, this::handle)
            EventCache.LOG.debug(
                "Received Guild Member {} event for a Guild not yet cached.",
                if (banned) "Ban" else "Unban",
            )
            return null
        }

        val user: User = getJDA().getEntityBuilder().createUser(userJson)

        if (banned) {
            getJDA().handleEvent(GuildBanEvent(getJDA(), responseNumber, guild, user))
        } else {
            getJDA().handleEvent(GuildUnbanEvent(getJDA(), responseNumber, guild, user))
        }
        return null
    }
}
