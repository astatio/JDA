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

package net.dv8tion.jda.internal.requests.restaction

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.RoleColors
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.RoleAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

private const val MAX_ROLE_NAME_LENGTH = 100

open class RoleActionImpl(
    @JvmField protected val guild: Guild,
) : AuditableRestActionImpl<Role>(guild.jda, Route.Roles.CREATE_ROLE.compile(guild.id)),
    RoleAction {
    @JvmField
    protected var permissions: Long? = null

    @JvmField
    protected var name: String? = null

    @JvmField
    protected var colors: RoleColors? = null

    @JvmField
    protected var hoisted: Boolean? = null

    @JvmField
    protected var mentionable: Boolean? = null

    @JvmField
    protected var icon: Icon? = null

    @JvmField
    protected var emoji: String? = null

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): RoleActionImpl = super.setCheck(checks) as RoleActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): RoleActionImpl = super.timeout(timeout, unit) as RoleActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): RoleActionImpl = super.deadline(timestamp) as RoleActionImpl

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nonnull
    @CheckReturnValue
    override fun setName(name: String?): RoleActionImpl {
        if (name != null) {
            Checks.notEmpty(name, "Name")
            Checks.notLonger(name, MAX_ROLE_NAME_LENGTH, "Name")
        }
        this.name = name
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setHoisted(hoisted: Boolean?): RoleActionImpl {
        this.hoisted = hoisted
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setMentionable(mentionable: Boolean?): RoleActionImpl {
        this.mentionable = mentionable
        return this
    }

    @Nonnull
    override fun setGradientColors(
        primaryRgb: Int,
        secondaryRgb: Int,
    ): RoleAction {
        this.colors = RoleColors(primaryRgb, secondaryRgb, Role.DEFAULT_COLOR_RAW)
        return this
    }

    @Nonnull
    override fun useHolographicStyle(): RoleAction {
        this.colors = RoleColors.DEFAULT_HOLOGRAPHIC
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setColor(rgb: Int?): RoleActionImpl {
        this.colors =
            RoleColors(
                rgb ?: Role.DEFAULT_COLOR_RAW,
                Role.DEFAULT_COLOR_RAW,
                Role.DEFAULT_COLOR_RAW,
            )
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setColors(colors: RoleColors?): RoleAction {
        this.colors = colors
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setPermissions(permissions: Long?): RoleActionImpl {
        if (permissions != null) {
            for (p in Permission.getPermissions(permissions)) {
                checkPermission(p)
            }
        }
        this.permissions = permissions
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setIcon(icon: Icon?): RoleActionImpl {
        this.icon = icon
        this.emoji = null
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setIcon(emoji: String?): RoleActionImpl {
        this.emoji = emoji
        this.icon = null
        return this
    }

    override fun finalizeData(): RequestBody? {
        val json = DataObject.empty()
        if (name != null) {
            json.put("name", name)
        }
        if (colors != null && !colors!!.isDefault) {
            json.put("colors", colors)
        }
        if (permissions != null) {
            json.put("permissions", permissions)
        }
        if (hoisted != null) {
            json.put("hoist", hoisted)
        }
        if (mentionable != null) {
            json.put("mentionable", mentionable)
        }
        if (icon != null) {
            json.put("icon", icon!!.encoding)
        }
        if (emoji != null) {
            json.put("unicode_emoji", emoji)
        }

        return getRequestBody(json)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<Role>,
    ) {
        request.onSuccess(
            api.entityBuilder.createRole(guild as GuildImpl, response.getObject(), guild.idLong),
        )
    }

    private fun checkPermission(permission: Permission) {
        if (!guild.selfMember.hasPermission(permission)) {
            throw InsufficientPermissionException(guild, permission)
        }
    }
}
