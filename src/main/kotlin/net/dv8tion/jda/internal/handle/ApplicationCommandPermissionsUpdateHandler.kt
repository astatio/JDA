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
import net.dv8tion.jda.api.events.interaction.command.ApplicationCommandUpdatePrivilegesEvent
import net.dv8tion.jda.api.events.interaction.command.ApplicationUpdatePrivilegesEvent
import net.dv8tion.jda.api.interactions.commands.privileges.IntegrationPrivilege
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.requests.WebSocketClient

class ApplicationCommandPermissionsUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guild: Guild
        if (!content.isNull("guild_id")) {
            val guildId = content.getUnsignedLong("guild_id")
            val cachedGuild = getJDA().getGuildById(guildId)
            if (getJDA().getGuildSetupController().isLocked(guildId)) {
                return guildId
            } else if (cachedGuild == null) {
                WebSocketClient.LOG.debug(
                    "Received APPLICATION_COMMAND_PERMISSIONS_UPDATE for a guild that is not cached: GuildID: {}",
                    guildId,
                )
                return null
            }
            guild = cachedGuild
        } else {
            return null
        }

        val id = content.getUnsignedLong("id")
        val applicationId = content.getUnsignedLong("application_id")

        val privileges = ArrayList<IntegrationPrivilege>()
        val permissions = content.getArray("permissions")
        for (i in 0 until permissions.length()) {
            val obj = permissions.getObject(i)
            privileges.add(
                IntegrationPrivilege(
                    guild,
                    IntegrationPrivilege.Type.fromKey(obj.getInt("type")),
                    obj.getBoolean("permission"),
                    obj.getUnsignedLong("id"),
                ),
            )
        }

        if (id != applicationId) {
            api.handleEvent(
                ApplicationCommandUpdatePrivilegesEvent(api, responseNumber, guild, id, applicationId, privileges),
            )
        } else {
            api.handleEvent(
                ApplicationUpdatePrivilegesEvent(api, responseNumber, guild, applicationId, privileges),
            )
        }
        return null
    }
}
