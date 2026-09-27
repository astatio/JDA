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

import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.GuildUnavailableEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.requests.WebSocketClient

class GuildDeleteHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val id = content.getLong("id")
        val setupController = getJDA().getGuildSetupController()
        val wasInit = setupController.onDelete(id, content)
        if (wasInit || setupController.isUnavailable(id)) {
            return null
        }

        val guild = getJDA().getGuildById(id) as GuildImpl?
        val unavailable = content.getBoolean("unavailable")
        if (guild == null) {
            WebSocketClient.LOG.debug(
                "Received GUILD_DELETE for a Guild that is not currently cached. ID: {} unavailable: {}",
                id,
                unavailable,
            )
            return null
        }

        // If the event is attempting to mark the guild as unavailable,
        // but it is already unavailable, ignore the event
        if (setupController.isUnavailable(id) && unavailable) {
            return null
        }

        // Detach the guild cache from the global cache (also removes users if necessary)
        guild.invalidate()

        if (unavailable) {
            setupController.onUnavailable(id)
            getJDA().handleEvent(GuildUnavailableEvent(getJDA(), responseNumber, guild))
        } else {
            getJDA().handleEvent(GuildLeaveEvent(getJDA(), responseNumber, guild))
        }
        getJDA().getEventCache().clear(EventCache.Type.GUILD, id)
        return null
    }
}
