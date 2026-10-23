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
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.PermOverrideManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPermissionContainerMixin
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

class PermOverrideManagerImpl(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var override: PermissionOverride,
) : ManagerBase<PermOverrideManager>(
        override.getJDA(),
        Route.Channels.MODIFY_PERM_OVERRIDE.compile(
            override.getChannel().getId(),
            override.getId(),
        ),
    ),
    PermOverrideManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val role: Boolean = override.isRoleOverride()

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var allowed: Long = override.getAllowedRaw()

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var denied: Long = override.getDeniedRaw()

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    private fun setupValues() {
        if (!shouldUpdate(PermOverrideManager.ALLOWED)) {
            allowed = getPermissionOverride().getAllowedRaw()
        }
        if (!shouldUpdate(PermOverrideManager.DENIED)) {
            denied = getPermissionOverride().getDeniedRaw()
        }
    }

    @Nonnull
    override fun getPermissionOverride(): PermissionOverride {
        val channel = override.getChannel() as IPermissionContainerMixin<*>
        val realOverride = channel.permissionOverrideMap[override.getIdLong()]
        if (realOverride != null) {
            override = realOverride
        }
        return override
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): PermOverrideManagerImpl {
        super.reset(fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): PermOverrideManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): PermOverrideManagerImpl {
        super.reset()
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun grant(permissions: Long): PermOverrideManagerImpl {
        if (permissions == 0L) {
            return this
        }
        setupValues()
        allowed = allowed or permissions
        denied = denied and permissions.inv()
        set = set or PermOverrideManager.PERMISSIONS
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun deny(permissions: Long): PermOverrideManagerImpl {
        if (permissions == 0L) {
            return this
        }
        setupValues()
        denied = denied or permissions
        allowed = allowed and permissions.inv()
        set = set or PermOverrideManager.PERMISSIONS
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun clear(permissions: Long): PermOverrideManagerImpl {
        setupValues()
        if (allowed and permissions != 0L) {
            allowed = allowed and permissions.inv()
            set = set or PermOverrideManager.ALLOWED
        }

        if (denied and permissions != 0L) {
            denied = denied and permissions.inv()
            set = set or PermOverrideManager.DENIED
        }

        return this
    }

    override fun finalizeData(): RequestBody {
        val targetId = override.getId()
        // setup missing values here
        setupValues()
        val data =
            getRequestBody(
                DataObject
                    .empty()
                    .put("id", targetId)
                    .put("type", if (role) "role" else "member")
                    .put("allow", allowed)
                    .put("deny", denied),
            )
        reset()
        return data
    }

    override fun checkPermissions(): Boolean {
        val selfMember = getGuild().getSelfMember()
        val channel: IPermissionContainer = getChannel() as IPermissionContainer
        Checks.checkAccess(selfMember, channel)
        if (!selfMember.hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
            throw InsufficientPermissionException(channel, Permission.MANAGE_PERMISSIONS)
        }
        return super.checkPermissions()
    }
}
