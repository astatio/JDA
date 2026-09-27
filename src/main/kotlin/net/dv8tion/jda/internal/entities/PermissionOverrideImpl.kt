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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.IPermissionHolder
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.unions.IPermissionContainerUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.requests.restaction.PermissionOverrideActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.EnumSet
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class PermissionOverrideImpl(
    channel: IPermissionContainer,
    private val id: Long,
    private val isRole: Boolean,
) : PermissionOverride {
    private val api: JDAImpl = channel.jda as JDAImpl
    private var channel: IPermissionContainer = channel

    private var allow: Long = 0
    private var deny: Long = 0

    override fun getAllowedRaw(): Long = allow

    override fun getInheritRaw(): Long = (allow or deny).inv()

    override fun getDeniedRaw(): Long = deny

    @Nonnull
    override fun getAllowed(): EnumSet<Permission> = Permission.getPermissions(allow)

    @Nonnull
    override fun getInherit(): EnumSet<Permission> = Permission.getPermissions(inheritRaw)

    @Nonnull
    override fun getDenied(): EnumSet<Permission> = Permission.getPermissions(deny)

    @Nonnull
    override fun getJDA(): JDA = api

    @Nullable
    override fun getPermissionHolder(): IPermissionHolder? = if (isRole) role else member

    override fun getMember(): Member? = guild.getMemberById(id)

    override fun getRole(): Role? = guild.getRoleById(id)

    @Nonnull
    override fun getChannel(): IPermissionContainerUnion {
        val realChannel: IPermissionContainer? = api.getChannelById(IPermissionContainer::class.java, channel.idLong)
        if (realChannel != null) {
            channel = realChannel
        }

        return channel as IPermissionContainerUnion
    }

    @Nonnull
    override fun getGuild(): Guild = getChannel().guild

    override fun isMemberOverride(): Boolean = !isRole

    override fun isRoleOverride(): Boolean = isRole

    @Nonnull
    override fun getManager(): PermissionOverrideAction {
        checkPermissions()
        return PermissionOverrideActionImpl(this).setOverride(false)
    }

    @Nonnull
    override fun delete(): AuditableRestAction<Void> {
        checkPermissions()

        val route = Route.Channels.DELETE_PERM_OVERRIDE.compile(channel.id, id.toString())
        return AuditableRestActionImpl(getJDA(), route)
    }

    override fun getIdLong(): Long = id

    fun setAllow(allow: Long): PermissionOverrideImpl {
        this.allow = allow
        return this
    }

    fun setDeny(deny: Long): PermissionOverrideImpl {
        this.deny = deny
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is PermissionOverrideImpl) {
            return false
        }
        return id == other.id && channel.idLong == other.channel.idLong
    }

    override fun hashCode(): Int = Objects.hash(id, channel.idLong)

    override fun toString(): String =
        EntityString(this)
            .setType(if (isMemberOverride()) "MEMBER" else "ROLE")
            .addMetadata("channel", channel)
            .toString()

    private fun checkPermissions() {
        val selfMember = guild.selfMember
        val channel = getChannel()
        Checks.checkAccess(selfMember, channel)
        if (!selfMember.hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
            throw InsufficientPermissionException(channel, Permission.MANAGE_PERMISSIONS)
        }
    }
}
