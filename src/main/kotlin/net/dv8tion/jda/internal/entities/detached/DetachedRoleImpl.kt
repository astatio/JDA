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

package net.dv8tion.jda.internal.entities.detached

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.RoleColors
import net.dv8tion.jda.api.entities.RoleIcon
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.exceptions.DetachedEntityException
import net.dv8tion.jda.api.managers.RoleManager
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.RoleImpl.RoleTagsImpl
import net.dv8tion.jda.internal.entities.mixin.RoleMixin
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.PermissionUtil
import java.util.EnumSet
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DetachedRoleImpl(
    private val id: Long,
    private val guild: DetachedGuildImpl,
) : Role,
    RoleMixin<DetachedRoleImpl> {
    private val api: JDAImpl = guild.jda

    private var tags: RoleTagsImpl? = if (api.isCacheFlagSet(CacheFlag.ROLE_TAGS)) RoleTagsImpl() else null
    private var name: String? = null
    private var managed: Boolean = false
    private var hoisted: Boolean = false
    private var mentionable: Boolean = false
    private var rawPermissions: Long = 0

    private var primaryColor: Int = 0
    private var secondaryColor: Int = Role.DEFAULT_COLOR_RAW
    private var tertiaryColor: Int = Role.DEFAULT_COLOR_RAW

    private var rawPosition: Int = 0
    private var icon: RoleIcon? = null

    override fun isDetached(): Boolean = true

    override fun getPosition(): Int =
        throw DetachedEntityException(
            "Cannot get the position of a detached role, only the raw position is available",
        )

    override fun getPositionRaw(): Int = rawPosition

    @Nonnull
    override fun getName(): String = name as String

    override fun isManaged(): Boolean = managed

    override fun isHoisted(): Boolean = hoisted

    override fun isMentionable(): Boolean = mentionable

    override fun getPermissionsRaw(): Long = rawPermissions

    @Nonnull
    override fun getColors(): RoleColors = RoleColors(primaryColor, secondaryColor, tertiaryColor)

    @Nonnull
    override fun getPermissions(): EnumSet<Permission> = Permission.getPermissions(rawPermissions)

    @Nonnull
    override fun getPermissions(channel: GuildChannel): EnumSet<Permission> = throw detachedException()

    @Nonnull
    override fun getPermissionsExplicit(): EnumSet<Permission> = permissions

    @Nonnull
    override fun getPermissionsExplicit(channel: GuildChannel): EnumSet<Permission> = throw detachedException()

    override fun isPublicRole(): Boolean = idLong == this.guild.idLong

    override fun hasPermission(vararg permissions: Permission): Boolean {
        var effectivePerms = rawPermissions
        for (perm in permissions) {
            val rawValue = perm.rawValue
            if ((effectivePerms and rawValue) != rawValue) {
                return false
            }
        }
        return true
    }

    override fun hasPermission(
        channel: GuildChannel,
        vararg permissions: Permission,
    ): Boolean = throw detachedException()

    override fun canSync(
        targetChannel: IPermissionContainer,
        syncSource: IPermissionContainer,
    ): Boolean = throw detachedException()

    override fun canSync(channel: IPermissionContainer): Boolean = throw detachedException()

    override fun canInteract(role: Role): Boolean = PermissionUtil.canInteract(this, role)

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nonnull
    override fun getManager(): RoleManager = throw detachedException()

    @Nonnull
    override fun delete(): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun getTags(): Role.RoleTags = tags ?: RoleTagsImpl.EMPTY

    @Nullable
    override fun getIcon(): RoleIcon? = icon

    @Nonnull
    override fun getAsMention(): String = if (isPublicRole) "@everyone" else "<@&$id>"

    override fun getIdLong(): Long = id

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is DetachedRoleImpl) {
            return false
        }
        return this.idLong == other.idLong
    }

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun toString(): String = EntityString(this).setName(getName()).toString()

    // -- Setters --

    override fun setName(name: String): DetachedRoleImpl {
        this.name = name
        return this
    }

    override fun setPrimaryColor(color: Int): DetachedRoleImpl {
        this.primaryColor = color
        return this
    }

    override fun setSecondaryColor(color: Int): DetachedRoleImpl {
        this.secondaryColor = color
        return this
    }

    override fun setTertiaryColor(color: Int): DetachedRoleImpl {
        this.tertiaryColor = color
        return this
    }

    override fun setManaged(managed: Boolean): DetachedRoleImpl {
        this.managed = managed
        return this
    }

    override fun setHoisted(hoisted: Boolean): DetachedRoleImpl {
        this.hoisted = hoisted
        return this
    }

    override fun setMentionable(mentionable: Boolean): DetachedRoleImpl {
        this.mentionable = mentionable
        return this
    }

    override fun setRawPermissions(rawPermissions: Long): DetachedRoleImpl {
        this.rawPermissions = rawPermissions
        return this
    }

    override fun setRawPosition(rawPosition: Int): DetachedRoleImpl {
        this.rawPosition = rawPosition
        return this
    }

    override fun setTags(tags: DataObject): DetachedRoleImpl {
        if (this.tags == null) {
            return this
        }
        this.tags = RoleTagsImpl(tags)
        return this
    }

    override fun setIcon(
        @Nullable icon: RoleIcon?,
    ): DetachedRoleImpl {
        this.icon = icon
        return this
    }
}
