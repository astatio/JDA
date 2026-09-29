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

import net.dv8tion.jda.api.events.role.RoleDeleteEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.MemberImpl
import net.dv8tion.jda.internal.entities.RoleImpl
import net.dv8tion.jda.internal.entities.emoji.RichCustomEmojiImpl
import net.dv8tion.jda.internal.requests.WebSocketClient

class GuildRoleDeleteHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        val guild = getJDA().getGuildById(guildId) as GuildImpl?
        if (guild == null) {
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            EventCache.LOG.debug("GUILD_ROLE_DELETE was received for a Guild that is not yet cached: {}", content)
            return null
        }

        val roleId = content.getLong("role_id")
        val removedRole = guild.getRolesView().get(roleId) as RoleImpl?
        if (removedRole == null) {
            WebSocketClient.LOG.debug("GUILD_ROLE_DELETE was received for a Role that is not yet cached: {}", content)
            return null
        }

        // Allow for position to still be retrievable in event handling
        removedRole.freezePosition()
        guild.getRolesView().remove(roleId)

        // Now that the role is removed from the Guild, remove it from all users and emojis.
        guild.getMembersView().forEach { m ->
            val member = m as MemberImpl
            member.getRoleSet().remove(removedRole)
        }

        for (emoji in guild.getEmojiCache()) {
            val impl = emoji as RichCustomEmojiImpl
            impl.getRoleSet().remove(removedRole)
        }

        getJDA().handleEvent(RoleDeleteEvent(getJDA(), responseNumber, removedRole))
        getJDA().getEventCache().clear(EventCache.Type.ROLE, roleId)
        return null
    }
}
