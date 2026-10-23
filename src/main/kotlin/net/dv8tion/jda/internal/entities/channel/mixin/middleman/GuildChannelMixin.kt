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

package net.dv8tion.jda.internal.entities.channel.mixin.middleman

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.exceptions.MissingAccessException
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.internal.entities.channel.mixin.ChannelMixin
import net.dv8tion.jda.internal.entities.detached.mixin.IDetachableEntityMixin
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

interface GuildChannelMixin<T : GuildChannelMixin<T>> :
    GuildChannel,
    GuildChannelUnion,
    ChannelMixin<T>,
    IDetachableEntityMixin {
    // ---- Default implementations of interface ----
    @Nonnull
    @CheckReturnValue
    override fun delete(): AuditableRestAction<Void> {
        checkCanAccess()
        checkCanManage()

        val route = Route.Channels.DELETE_CHANNEL.compile(id)
        return AuditableRestActionImpl(jda, route)
    }

    // ---- Setters ---
    fun setFlags(flags: Int): T

    // ---- Helpers ----
    fun hasPermission(permission: Permission): Boolean = guild.selfMember.hasPermission(this, permission)

    fun checkPermission(permission: Permission) {
        checkPermission(permission, null)
    }

    fun checkPermission(
        permission: Permission,
        message: String?,
    ) {
        if (!hasPermission(permission)) {
            if (message != null) {
                throw InsufficientPermissionException(this, permission, message)
            } else {
                throw InsufficientPermissionException(this, permission)
            }
        }
    }

    // Overridden by ThreadChannelImpl
    fun checkCanManage() {
        checkPermission(Permission.MANAGE_CHANNEL)
    }

    // Overridden by AudioChannelMixin
    override fun checkCanAccess() {
        checkAttached()
        if (!hasPermission(Permission.VIEW_CHANNEL)) {
            throw MissingAccessException(this, Permission.VIEW_CHANNEL)
        }
    }
}
