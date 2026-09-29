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
import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.automod.AutoModExecutionImpl

class AutoModExecutionHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getUnsignedLong("guild_id")
        if (api.getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        val guild: Guild? = api.getGuildById(guildId)
        if (guild == null) {
            api.getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            EventCache.LOG.debug(
                "Received a AUTO_MODERATION_ACTION_EXECUTION for a guild that is not yet cached. JSON: {}",
                content,
            )
            return null
        }

        val execution = AutoModExecutionImpl(guild, content)
        api.handleEvent(AutoModExecutionEvent(api, responseNumber, execution))
        return null
    }
}
