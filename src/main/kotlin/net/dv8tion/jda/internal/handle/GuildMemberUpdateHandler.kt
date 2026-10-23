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

import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.MemberImpl
import java.util.ArrayList

class GuildMemberUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val id = content.getLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(id)) {
            return id
        }

        val userJson = content.getObject("user")
        val userId = userJson.getLong("id")
        val guild = getJDA().getGuildById(id) as GuildImpl?
        if (guild == null) {
            // Do not cache this here, it will be outdated once we receive the GUILD_CREATE and
            // could cause invalid cache
            EventCache.LOG.debug(
                "Got GuildMember update but JDA currently does not have the Guild cached. Ignoring. {}",
                content,
            )
            return null
        }

        var member = guild.getMembersView().get(userId) as MemberImpl?
        if (member == null) {
            member = getJDA().getEntityBuilder().createMember(guild, content)
        } else {
            val newRoles: List<Role>? = toRolesList(guild, content.getArray("roles"))
            getJDA().getEntityBuilder().updateMember(guild, member, content, newRoles)
        }

        getJDA().getEntityBuilder().updateMemberCache(member)
        getJDA().handleEvent(GuildMemberUpdateEvent(getJDA(), responseNumber, member))
        return null
    }

    private fun toRolesList(
        guild: GuildImpl,
        array: DataArray,
    ): List<Role>? {
        val roles: MutableList<Role> = ArrayList()
        for (i in 0 until array.length()) {
            val id = array.getLong(i)
            val r = guild.getRolesView().get(id)
            if (r != null) {
                roles.add(r)
            } else {
                getJDA().getEventCache().cache(EventCache.Type.ROLE, id, responseNumber, allContent, this::handle)
                EventCache.LOG.debug("Got GuildMember update but one of the Roles for the Member is not yet cached.")
                return null
            }
        }
        return roles
    }
}
