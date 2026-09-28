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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.IPermissionHolder
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.PermissionOverrideImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPermissionContainerMixin
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.PermissionUtil
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

open class PermissionOverrideActionImpl :
    AuditableRestActionImpl<PermissionOverride>,
    PermissionOverrideAction {
    private var isOverride = true
    private var allowSet = false
    private var denySet = false

    private var allow = 0L
    private var deny = 0L
    private val channel: IPermissionContainerMixin<*>
    private val permissionHolder: IPermissionHolder
    private val isRole: Boolean
    private val id: Long

    constructor(override: PermissionOverride) : super(
        override.jda,
        Route.Channels.MODIFY_PERM_OVERRIDE.compile(override.channel.id, override.id),
    ) {
        this.channel = override.channel as IPermissionContainerMixin<*>
        this.permissionHolder = override.permissionHolder!!
        this.isRole = override.isRoleOverride
        this.id = override.idLong
    }

    constructor(api: JDA, channel: GuildChannel, permissionHolder: IPermissionHolder) : super(
        api,
        Route.Channels.CREATE_PERM_OVERRIDE.compile(channel.id, permissionHolder.id),
    ) {
        this.channel = channel as IPermissionContainerMixin<*>
        this.permissionHolder = permissionHolder
        this.isRole = permissionHolder is Role
        this.id = permissionHolder.idLong
    }

    // Whether to keep original value of the current override or not
    // by default we override the value
    fun setOverride(override: Boolean): PermissionOverrideActionImpl {
        isOverride = override
        return this
    }

    override fun finalizeChecks(): BooleanSupplier =
        BooleanSupplier {
            val selfMember = guild.selfMember
            Checks.checkAccess(selfMember, channel)
            if (!selfMember.hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
                throw InsufficientPermissionException(channel, Permission.MANAGE_PERMISSIONS)
            }
            true
        }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): PermissionOverrideActionImpl = super.setCheck(checks) as PermissionOverrideActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): PermissionOverrideActionImpl = super.timeout(timeout, unit) as PermissionOverrideActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): PermissionOverrideActionImpl = super.deadline(timestamp) as PermissionOverrideActionImpl

    @Nonnull
    override fun resetAllow(): PermissionOverrideAction {
        allow = getOriginalAllow()
        allowSet = false
        return this
    }

    @Nonnull
    override fun resetDeny(): PermissionOverrideAction {
        deny = getOriginalDeny()
        denySet = false
        return this
    }

    @Nonnull
    override fun getChannel(): IPermissionContainer = channel

    override fun getRole(): Role? = if (isRole()) permissionHolder as Role else null

    override fun getMember(): Member? = if (isMember()) permissionHolder as Member else null

    override fun getAllowed(): Long = getCurrentAllow()

    override fun getDenied(): Long = getCurrentDeny()

    override fun getInherited(): Long = getAllowed().inv() and getDenied().inv()

    override fun isMember(): Boolean = !isRole

    override fun isRole(): Boolean = isRole

    @Nonnull
    @CheckReturnValue
    override fun setAllowed(allowBits: Long): PermissionOverrideActionImpl {
        checkPermissions(getOriginalAllow() xor allowBits)
        this.allow = allowBits
        this.deny = getCurrentDeny() and allowBits.inv()
        allowSet = true
        denySet = true
        return this
    }

    @Nonnull
    override fun grant(allowBits: Long): PermissionOverrideAction = setAllowed(getCurrentAllow() or allowBits)

    @Nonnull
    @CheckReturnValue
    override fun setDenied(denyBits: Long): PermissionOverrideActionImpl {
        checkPermissions(getOriginalDeny() xor denyBits)
        this.deny = denyBits
        this.allow = getCurrentAllow() and denyBits.inv()
        allowSet = true
        denySet = true
        return this
    }

    @Nonnull
    override fun deny(denyBits: Long): PermissionOverrideAction = setDenied(getCurrentDeny() or denyBits)

    @Nonnull
    override fun clear(inheritedBits: Long): PermissionOverrideAction =
        setAllowed(getCurrentAllow() and inheritedBits.inv()).setDenied(getCurrentDeny() and inheritedBits.inv())

    protected fun checkPermissions(changed: Long) {
        val selfMember = guild.selfMember
        if (changed != 0L && !selfMember.hasPermission(Permission.ADMINISTRATOR)) {
            val channelPermissions = PermissionUtil.getExplicitPermission(channel, selfMember, false)
            if ((channelPermissions and Permission.MANAGE_PERMISSIONS.rawValue) == 0L) {
                // This implies we can only set permissions the bot also has in the channel
                val botPerms = PermissionUtil.getEffectivePermission(channel, selfMember)
                val missing = Permission.getPermissions(changed and botPerms.inv())
                if (!missing.isEmpty()) {
                    throw InsufficientPermissionException(
                        channel,
                        Permission.MANAGE_PERMISSIONS,
                        "You must have Permission.MANAGE_PERMISSIONS on the channel explicitly in order to set permissions you don't already have!",
                    )
                }
            }
        }
    }

    @Nonnull
    @CheckReturnValue
    override fun setPermissions(
        allowBits: Long,
        denyBits: Long,
    ): PermissionOverrideActionImpl = setAllowed(allowBits).setDenied(denyBits)

    private fun getCurrentAllow(): Long {
        if (allowSet) {
            return allow
        }
        return if (isOverride) 0 else getOriginalAllow()
    }

    private fun getCurrentDeny(): Long {
        if (denySet) {
            return deny
        }
        return if (isOverride) 0 else getOriginalDeny()
    }

    private fun getOriginalDeny(): Long {
        val override = channel.permissionOverrideMap[id]
        return override?.deniedRaw ?: 0
    }

    private fun getOriginalAllow(): Long {
        val override = channel.permissionOverrideMap[id]
        return override?.allowedRaw ?: 0
    }

    override fun finalizeData(): RequestBody? {
        val json = DataObject.empty()
        json.put("type", if (isRole()) 0 else 1)
        json.put("allow", getCurrentAllow())
        json.put("deny", getCurrentDeny())
        reset()
        return getRequestBody(json)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<PermissionOverride>,
    ) {
        val json = request.rawBody as DataObject
        val override = PermissionOverrideImpl(channel, id, isRole())
        override.setAllow(json.getLong("allow"))
        override.setDeny(json.getLong("deny"))
        // This is added by the event later
        // ((AbstractChannelImpl<?,?>) channel).getOverrideMap().put(id, override);
        request.onSuccess(override)
    }
}
