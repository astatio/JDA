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

import net.dv8tion.jda.api.entities.RoleColors
import net.dv8tion.jda.api.entities.RoleIcon
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorsEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdateHoistedEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdateIconEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdateMentionableEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdatePositionEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.AbstractEntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.RoleImpl
import java.util.Objects

class GuildRoleUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        val rolejson = content.getObject("role")
        val guild = getJDA().getGuildById(guildId) as GuildImpl?
        if (guild == null) {
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            EventCache.LOG.debug("Received a Role Update for a Guild that is not yet cached: {}", content)
            return null
        }

        val roleId = rolejson.getLong("id")
        val role = guild.getRolesView().get(roleId) as RoleImpl?
        if (role == null) {
            getJDA().getEventCache().cache(EventCache.Type.ROLE, roleId, responseNumber, allContent, this::handle)
            EventCache.LOG.debug("Received a Role Update for Role that is not yet cached: {}", content)
            return null
        }

        val name = rolejson.getString("name")
        val colors: RoleColors = AbstractEntityBuilder.createRoleColors(rolejson.getObject("colors"))

        val position = rolejson.getInt("position")
        val permissions = rolejson.getLong("permissions")
        val hoisted = rolejson.getBoolean("hoist")
        val mentionable = rolejson.getBoolean("mentionable")
        val iconId = rolejson.getString("icon", null)
        val emoji = rolejson.getString("unicode_emoji", null)

        rolejson.optObject("tags").ifPresent { role.setTags(it) }

        if (!Objects.equals(name, role.getName())) {
            val oldName = role.getName()
            role.setName(name)
            getJDA().handleEvent(RoleUpdateNameEvent(getJDA(), responseNumber, role, oldName))
        }
        if (colors != role.getColors()) {
            val oldColors: RoleColors = role.getColors()
            role.setPrimaryColor(colors.getPrimaryRaw())
            role.setSecondaryColor(colors.getSecondaryRaw())
            role.setTertiaryColor(colors.getTertiaryRaw())
            getJDA().handleEvent(RoleUpdateColorsEvent(getJDA(), responseNumber, role, oldColors))

            if (oldColors.getPrimaryRaw() != colors.getPrimaryRaw()) {
                @Suppress("DEPRECATION")
                val event = RoleUpdateColorEvent(getJDA(), responseNumber, role, oldColors.getPrimaryRaw())
                getJDA().handleEvent(event)
            }
        }
        if (position != role.getPositionRaw()) {
            val oldPosition = role.getPosition()
            val oldPositionRaw = role.getPositionRaw()
            role.setRawPosition(position)
            getJDA().handleEvent(
                RoleUpdatePositionEvent(getJDA(), responseNumber, role, oldPosition, oldPositionRaw),
            )
        }
        if (permissions != role.getPermissionsRaw()) {
            val oldPermissionsRaw = role.getPermissionsRaw()
            role.setRawPermissions(permissions)
            getJDA().handleEvent(RoleUpdatePermissionsEvent(getJDA(), responseNumber, role, oldPermissionsRaw))
        }

        if (hoisted != role.isHoisted()) {
            val wasHoisted = role.isHoisted()
            role.setHoisted(hoisted)
            getJDA().handleEvent(RoleUpdateHoistedEvent(getJDA(), responseNumber, role, wasHoisted))
        }
        if (mentionable != role.isMentionable()) {
            val wasMentionable = role.isMentionable()
            role.setMentionable(mentionable)
            getJDA().handleEvent(RoleUpdateMentionableEvent(getJDA(), responseNumber, role, wasMentionable))
        }

        val oldIcon: RoleIcon? = role.getIcon()
        val newIcon: RoleIcon? = if (iconId == null && emoji == null) null else RoleIcon(iconId, emoji, roleId)
        if (oldIcon != newIcon) {
            role.setIcon(newIcon)
            getJDA().handleEvent(RoleUpdateIconEvent(getJDA(), responseNumber, role, oldIcon))
        }
        return null
    }
}
