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

import gnu.trove.map.TLongObjectMap
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.Role.RoleTags
import net.dv8tion.jda.api.entities.RoleColors
import net.dv8tion.jda.api.entities.RoleIcon
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.exceptions.HierarchyException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.RoleManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPermissionContainerMixin
import net.dv8tion.jda.internal.entities.mixin.RoleMixin
import net.dv8tion.jda.internal.managers.RoleManagerImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.PermissionUtil
import net.dv8tion.jda.internal.utils.cache.SortedSnowflakeCacheViewImpl
import java.util.EnumSet
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class RoleImpl(
    private val id: Long,
    guild: Guild,
) : Role,
    RoleMixin<RoleImpl> {
    private val api: JDAImpl = guild.jda as JDAImpl
    private var guild: Guild = guild

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
    private var frozenPosition: Int = Int.MIN_VALUE // this is used exclusively for delete events
    private var icon: RoleIcon? = null

    override fun isDetached(): Boolean = false

    // Mirrors the original Java control flow, which validates each precondition with a direct throw.
    @Suppress("ThrowsCount", "ReturnCount")
    override fun getPosition(): Int {
        if (frozenPosition > Int.MIN_VALUE) {
            return frozenPosition
        }
        val guild = getGuild()
        if (this == guild.publicRole) {
            return -1
        }

        // Subtract 1 to get into 0-index, and 1 to disregard the everyone role.
        var i = guild.roles.size - 2
        for (r in guild.roles) {
            if (this == r) {
                return i
            }
            i--
        }
        throw IllegalStateException(
            String.format(
                "Could not determine position of role %d in guild %d\n" +
                    "- Make sure you do not keep entities stored, prefer getting them by ID\n" +
                    "- Check your bot is not processing events requiring role positions after JDA receives events " +
                    "deleting this role, this is typically caused when events are processed asynchronously",
                id,
                getGuild().idLong,
            ),
        )
    }

    override fun getPositionRaw(): Int = rawPosition

    @Nonnull
    override fun getName(): String = name as String

    override fun isManaged(): Boolean = managed

    override fun isHoisted(): Boolean = hoisted

    override fun isMentionable(): Boolean = mentionable

    override fun getPermissionsRaw(): Long = rawPermissions

    @Nonnull
    override fun getPermissions(): EnumSet<Permission> = Permission.getPermissions(rawPermissions)

    @Nonnull
    override fun getPermissions(channel: GuildChannel): EnumSet<Permission> =
        Permission.getPermissions(PermissionUtil.getEffectivePermission(channel, this))

    @Nonnull
    override fun getPermissionsExplicit(): EnumSet<Permission> = permissions

    @Nonnull
    override fun getPermissionsExplicit(channel: GuildChannel): EnumSet<Permission> =
        Permission.getPermissions(PermissionUtil.getExplicitPermission(channel, this))

    @Nonnull
    override fun getColors(): RoleColors = RoleColors(primaryColor, secondaryColor, tertiaryColor)

    override fun isPublicRole(): Boolean = this == getGuild().publicRole

    override fun hasPermission(vararg permissions: Permission): Boolean {
        val effectivePerms = rawPermissions or getGuild().publicRole.permissionsRaw
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
    ): Boolean {
        val effectivePerms = PermissionUtil.getEffectivePermission(channel, this)
        for (perm in permissions) {
            val rawValue = perm.rawValue
            if ((effectivePerms and rawValue) != rawValue) {
                return false
            }
        }
        return true
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    override fun canSync(
        targetChannel: IPermissionContainer,
        syncSource: IPermissionContainer,
    ): Boolean {
        Checks.notNull(targetChannel, "Channel")
        Checks.notNull(syncSource, "Channel")
        Checks.check(targetChannel.guild == getGuild(), "Channels must be from the same guild!")
        Checks.check(syncSource.guild == getGuild(), "Channels must be from the same guild!")
        val rolePerms = PermissionUtil.getEffectivePermission(targetChannel, this)
        if ((rolePerms and Permission.MANAGE_PERMISSIONS.rawValue) == 0L) {
            return false // Role can't manage permissions at all!
        }
        val channelPermissions = PermissionUtil.getExplicitPermission(targetChannel, this, false)
        // If the role has ADMINISTRATOR or MANAGE_PERMISSIONS
        // then it can also set any other permission on the channel
        val hasLocalAdmin =
            (
                (rolePerms and Permission.ADMINISTRATOR.rawValue) or
                    (channelPermissions and Permission.MANAGE_PERMISSIONS.rawValue)
            ) != 0L
        if (hasLocalAdmin) {
            return true
        }

        val existingOverrides: TLongObjectMap<PermissionOverride> =
            (targetChannel as IPermissionContainerMixin<*>).permissionOverrideMap
        for (override in syncSource.permissionOverrides) {
            val existing = existingOverrides.get(override.idLong)
            var allow = override.allowedRaw
            var deny = override.deniedRaw
            if (existing != null) {
                allow = allow xor existing.allowedRaw
                deny = deny xor existing.deniedRaw
            }
            // If any permissions changed that the role doesn't have in the channel,
            // the role can't sync it :(
            if (((allow or deny) and rolePerms.inv()) != 0L) {
                return false
            }
        }
        return true
    }

    override fun canSync(channel: IPermissionContainer): Boolean {
        Checks.notNull(channel, "Channel")
        Checks.check(channel.guild == getGuild(), "Channels must be from the same guild!")
        val rolePerms = PermissionUtil.getEffectivePermission(channel, this)
        if ((rolePerms and Permission.MANAGE_PERMISSIONS.rawValue) == 0L) {
            return false // Role can't manage permissions at all!
        }
        val channelPermissions = PermissionUtil.getExplicitPermission(channel, this, false)
        // If the role has ADMINISTRATOR or MANAGE_PERMISSIONS
        // then it can also set any other permission on the channel
        return (
            (rolePerms and Permission.ADMINISTRATOR.rawValue) or
                (channelPermissions and Permission.MANAGE_PERMISSIONS.rawValue)
        ) != 0L
    }

    override fun canInteract(role: Role): Boolean = PermissionUtil.canInteract(this, role)

    @Nonnull
    override fun getGuild(): Guild {
        val realGuild = api.getGuildById(guild.idLong)
        if (realGuild != null) {
            guild = realGuild
        }
        return guild
    }

    @Nonnull
    override fun getManager(): RoleManager = RoleManagerImpl(this)

    @Nonnull
    // Mirrors the original Java control flow, which validates each precondition with a direct throw.
    @Suppress("ThrowsCount")
    override fun delete(): AuditableRestAction<Void> {
        val guild = getGuild()
        if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            throw InsufficientPermissionException(guild, Permission.MANAGE_ROLES)
        }
        if (!PermissionUtil.canInteract(guild.selfMember, this)) {
            throw HierarchyException("Can't delete role >= highest self-role")
        }
        if (managed) {
            throw UnsupportedOperationException("Cannot delete a Role that is managed. ")
        }

        val route = Route.Roles.DELETE_ROLE.compile(guild.id, getId())
        return AuditableRestActionImpl(getJDA(), route)
    }

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun getTags(): RoleTags = tags ?: RoleTagsImpl.EMPTY

    @Nullable
    override fun getIcon(): RoleIcon? = icon

    @Nonnull
    override fun getAsMention(): String = if (isPublicRole) "@everyone" else "<@&$id>"

    override fun getIdLong(): Long = id

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is RoleImpl) {
            return false
        }
        return this.idLong == other.idLong
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = EntityString(this).setName(getName()).toString()

    // -- Setters --

    override fun setName(name: String): RoleImpl {
        this.name = name
        return this
    }

    override fun setPrimaryColor(color: Int): RoleImpl {
        this.primaryColor = color
        return this
    }

    override fun setSecondaryColor(color: Int): RoleImpl {
        this.secondaryColor = color
        return this
    }

    override fun setTertiaryColor(color: Int): RoleImpl {
        this.tertiaryColor = color
        return this
    }

    override fun setManaged(managed: Boolean): RoleImpl {
        this.managed = managed
        return this
    }

    override fun setHoisted(hoisted: Boolean): RoleImpl {
        this.hoisted = hoisted
        return this
    }

    override fun setMentionable(mentionable: Boolean): RoleImpl {
        this.mentionable = mentionable
        return this
    }

    override fun setRawPermissions(rawPermissions: Long): RoleImpl {
        this.rawPermissions = rawPermissions
        return this
    }

    override fun setRawPosition(rawPosition: Int): RoleImpl {
        val roleCache = getGuild().roleCache as SortedSnowflakeCacheViewImpl<Role>
        roleCache.clearCachedLists()
        this.rawPosition = rawPosition
        return this
    }

    override fun setTags(tags: DataObject): RoleImpl {
        if (this.tags == null) {
            return this
        }
        this.tags = RoleTagsImpl(tags)
        return this
    }

    override fun setIcon(
        @Nullable icon: RoleIcon?,
    ): RoleImpl {
        this.icon = icon
        return this
    }

    fun freezePosition() {
        this.frozenPosition = getPosition()
    }

    class RoleTagsImpl : RoleTags {
        private val botId: Long
        private val integrationId: Long
        private val subscriptionListingId: Long
        private val premiumSubscriber: Boolean
        private val availableForPurchase: Boolean
        private val isGuildConnections: Boolean

        constructor() {
            botId = 0L
            integrationId = 0L
            subscriptionListingId = 0L
            premiumSubscriber = false
            availableForPurchase = false
            isGuildConnections = false
        }

        constructor(tags: DataObject) {
            botId = tags.getUnsignedLong("bot_id", 0L)
            integrationId = tags.getUnsignedLong("integration_id", 0L)
            subscriptionListingId = tags.getUnsignedLong("subscription_listing_id", 0L)
            premiumSubscriber = tags.hasKey("premium_subscriber")
            availableForPurchase = tags.hasKey("available_for_purchase")
            isGuildConnections = tags.hasKey("guild_connections")
        }

        override fun isBot(): Boolean = botId != 0L

        override fun getBotIdLong(): Long = botId

        override fun isBoost(): Boolean = premiumSubscriber

        override fun isIntegration(): Boolean = integrationId != 0L

        override fun getIntegrationIdLong(): Long = integrationId

        override fun getSubscriptionIdLong(): Long = subscriptionListingId

        override fun isAvailableForPurchase(): Boolean = availableForPurchase

        override fun isLinkedRole(): Boolean = isGuildConnections

        override fun hashCode(): Int =
            Objects.hash(
                botId,
                integrationId,
                premiumSubscriber,
                availableForPurchase,
                subscriptionListingId,
                isGuildConnections,
            )

        override fun equals(other: Any?): Boolean {
            if (other === this) {
                return true
            }
            if (other !is RoleTagsImpl) {
                return false
            }
            return botId == other.botId &&
                integrationId == other.integrationId &&
                premiumSubscriber == other.premiumSubscriber &&
                availableForPurchase == other.availableForPurchase &&
                subscriptionListingId == other.subscriptionListingId &&
                isGuildConnections == other.isGuildConnections
        }

        override fun toString(): String =
            EntityString(this)
                .addMetadata("bot", getBotId())
                .addMetadata("integration", getIntegrationId())
                .addMetadata("subscriptionListing", getSubscriptionId())
                .addMetadata("isBoost", isBoost())
                .addMetadata("isAvailableForPurchase", isAvailableForPurchase())
                .addMetadata("isGuildConnections", isLinkedRole())
                .toString()

        companion object {
            @JvmField
            val EMPTY: RoleTags = RoleTagsImpl()
        }
    }
}
