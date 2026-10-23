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

package net.dv8tion.jda.internal.managers

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.RoleColors
import net.dv8tion.jda.api.exceptions.HierarchyException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.RoleManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.PermissionUtil
import okhttp3.RequestBody
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val NAME_MAX_LENGTH = 100

class RoleManagerImpl(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var role: Role,
) : ManagerBase<RoleManager>(
        role.getJDA(),
        Route.Roles.MODIFY_ROLE.compile(role.getGuild().getId(), role.getId()),
    ),
    RoleManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var name: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var colors: RoleColors? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var permissions: Long = 0

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var hoist: Boolean = false

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var mentionable: Boolean = false

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var icon: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var emoji: String? = null

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    @Nonnull
    override fun getRole(): Role {
        val realRole = role.getGuild().getRoleById(role.getIdLong())
        if (realRole != null) {
            role = realRole
        }
        return role
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): RoleManagerImpl {
        super.reset(fields)
        if (fields and RoleManager.NAME == RoleManager.NAME) {
            name = null
        }
        if (fields and RoleManager.COLOR == RoleManager.COLOR) {
            colors = null
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): RoleManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): RoleManagerImpl {
        super.reset()
        name = null
        colors = null
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): RoleManagerImpl {
        Checks.notBlank(name, "Name")
        val trimmed = name.trim()
        Checks.notEmpty(trimmed, "Name")
        Checks.notLonger(trimmed, NAME_MAX_LENGTH, "Name")
        this.name = trimmed
        set = set or RoleManager.NAME
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setPermissions(perms: Long): RoleManagerImpl {
        val selfPermissions = PermissionUtil.getEffectivePermission(getGuild().getSelfMember())
        setupPermissions()
        var missingPerms = perms // include permissions we want to set to
        missingPerms = missingPerms and selfPermissions.inv() // exclude permissions we have
        missingPerms = missingPerms and permissions.inv() // exclude permissions the role has
        // if any permissions remain, we have an issue
        if (missingPerms != 0L && isPermissionChecksEnabled()) {
            val permissionList = Permission.getPermissions(missingPerms)
            if (!permissionList.isEmpty()) {
                throw InsufficientPermissionException(
                    getGuild(),
                    permissionList.iterator().next(),
                )
            }
        }
        permissions = perms
        set = set or RoleManager.PERMISSION
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setColor(rgb: Int): RoleManagerImpl {
        colors = RoleColors(rgb, Role.DEFAULT_COLOR_RAW, Role.DEFAULT_COLOR_RAW)
        set = set or RoleManager.COLOR
        return this
    }

    @Nonnull
    override fun setColors(
        @Nullable colors: RoleColors?,
    ): RoleManager {
        this.colors = colors
        set = set or RoleManager.COLOR
        return this
    }

    @Nonnull
    override fun setGradientColors(
        primaryRgb: Int,
        secondaryRgb: Int,
    ): RoleManager {
        colors = RoleColors(primaryRgb, secondaryRgb, Role.DEFAULT_COLOR_RAW)
        set = set or RoleManager.COLOR
        return this
    }

    @Nonnull
    override fun useHolographicStyle(): RoleManager {
        colors = RoleColors.DEFAULT_HOLOGRAPHIC
        set = set or RoleManager.COLOR
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setHoisted(hoisted: Boolean): RoleManagerImpl {
        hoist = hoisted
        set = set or RoleManager.HOIST
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setMentionable(mentionable: Boolean): RoleManagerImpl {
        this.mentionable = mentionable
        set = set or RoleManager.MENTIONABLE
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setIcon(
        @Nullable icon: Icon?,
    ): RoleManagerImpl {
        this.icon = icon
        emoji = null
        set = set or RoleManager.ICON
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setIcon(
        @Nullable emoji: String?,
    ): RoleManagerImpl {
        this.emoji = emoji
        icon = null
        set = set or RoleManager.ICON
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun givePermissions(
        @Nonnull perms: Collection<Permission>,
    ): RoleManagerImpl {
        Checks.noneNull(perms, "Permissions")
        setupPermissions()
        return setPermissions(permissions or Permission.getRaw(perms))
    }

    @Nonnull
    @CheckReturnValue
    override fun revokePermissions(
        @Nonnull perms: Collection<Permission>,
    ): RoleManagerImpl {
        Checks.noneNull(perms, "Permissions")
        setupPermissions()
        return setPermissions(permissions and Permission.getRaw(perms).inv())
    }

    override fun finalizeData(): RequestBody {
        val obj = DataObject.empty().put("name", getRole().getName())
        if (shouldUpdate(RoleManager.NAME)) {
            obj.put("name", name)
        }
        if (shouldUpdate(RoleManager.PERMISSION)) {
            obj.put("permissions", permissions)
        }
        if (shouldUpdate(RoleManager.HOIST)) {
            obj.put("hoist", hoist)
        }
        if (shouldUpdate(RoleManager.MENTIONABLE)) {
            obj.put("mentionable", mentionable)
        }
        if (shouldUpdate(RoleManager.COLOR)) {
            obj.put("colors", colors)
        }
        if (shouldUpdate(RoleManager.ICON)) {
            obj.put("icon", icon?.getEncoding())
            obj.put("unicode_emoji", emoji)
        }
        reset()
        return getRequestBody(obj)
    }

    override fun checkPermissions(): Boolean {
        val selfMember: Member = getGuild().getSelfMember()
        if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            throw InsufficientPermissionException(getGuild(), Permission.MANAGE_ROLES)
        }
        if (!selfMember.canInteract(getRole())) {
            throw HierarchyException("Cannot modify a role that is higher or equal in hierarchy")
        }
        return super.checkPermissions()
    }

    private fun setupPermissions() {
        if (!shouldUpdate(RoleManager.PERMISSION)) {
            permissions = getRole().getPermissionsRaw()
        }
    }
}
