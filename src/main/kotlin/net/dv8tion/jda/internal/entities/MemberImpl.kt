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
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.ClientType
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPermissionContainerMixin
import net.dv8tion.jda.internal.entities.mixin.MemberMixin
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import net.dv8tion.jda.internal.utils.PermissionUtil
import java.time.OffsetDateTime
import java.util.Collections
import java.util.Comparator
import java.util.EnumSet
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import javax.annotation.Nonnull
import javax.annotation.Nullable

open class MemberImpl(
    guild: GuildImpl,
    user: User,
) : Member,
    MemberMixin<MemberImpl> {
    private val api: JDAImpl = user.jda as JDAImpl
    private val roles: MutableSet<Role> = ConcurrentHashMap.newKeySet()

    private var guild: GuildImpl = guild
    private var user: User = user
    private var nickname: String? = null
    private var avatarId: String? = null
    private var bannerId: String? = null
    private var joinDate: Long = 0
    private var boostDate: Long = 0
    private var timeOutEnd: Long = 0
    private var pending: Boolean = false
    private var flags: Int = 0

    override fun isDetached(): Boolean = false

    fun getPresence(): MemberPresenceImpl? {
        val presences: CacheView.SimpleCacheView<MemberPresenceImpl>? = guild.presenceView
        return presences?.get(idLong)
    }

    @Nonnull
    override fun getUser(): User {
        // Load user from cache if one exists,
        // ideally two members with the same id should wrap the same user object
        val realUser = jda.getUserById(user.idLong)
        if (realUser != null) {
            user = realUser
        }
        return user
    }

    @Nonnull
    override fun getGuild(): GuildImpl {
        val realGuild = api.getGuildById(guild.idLong) as GuildImpl?
        if (realGuild != null) {
            guild = realGuild
        }
        return guild
    }

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun getTimeJoined(): OffsetDateTime =
        if (hasTimeJoined()) {
            Helpers.toOffset(joinDate)
        } else {
            getGuild().timeCreated
        }

    override fun hasTimeJoined(): Boolean = joinDate != 0L

    @Nullable
    override fun getTimeBoosted(): OffsetDateTime? = if (isBoosting) Helpers.toOffset(boostDate) else null

    override fun isBoosting(): Boolean = boostDate != 0L

    @Nullable
    override fun getTimeOutEnd(): OffsetDateTime? = if (timeOutEnd != 0L) Helpers.toOffset(timeOutEnd) else null

    @Nullable
    override fun getVoiceState(): GuildVoiceStateImpl? = guild.getVoiceState(this)

    @Nonnull
    override fun getActivities(): List<Activity> {
        val presence = getPresence()
        return presence?.getActivities() ?: Collections.emptyList()
    }

    @Nonnull
    override fun getOnlineStatus(): OnlineStatus {
        val presence = getPresence()
        return presence?.getOnlineStatus() ?: OnlineStatus.OFFLINE
    }

    @Nonnull
    override fun getOnlineStatus(type: ClientType): OnlineStatus {
        Checks.notNull(type, "Type")
        val presence = getPresence() ?: return OnlineStatus.OFFLINE
        return presence.getClientStatus()[type] ?: OnlineStatus.OFFLINE
    }

    @Nonnull
    override fun getActiveClients(): EnumSet<ClientType> {
        val presence = getPresence()
        return if (presence == null) {
            EnumSet.noneOf(ClientType::class.java)
        } else {
            Helpers.copyEnumSet(ClientType::class.java, presence.getClientStatus().keys)
        }
    }

    @Nullable
    override fun getNickname(): String? = nickname

    @Nullable
    override fun getAvatarId(): String? = avatarId

    @Nullable
    override fun getBannerId(): String? = bannerId

    @Nonnull
    override fun getEffectiveName(): String = nickname ?: getUser().effectiveName

    @Nonnull
    override fun getRoles(): List<Role> {
        val roleList = ArrayList(roles)
        roleList.sortWith(Comparator.reverseOrder())
        return Collections.unmodifiableList(roleList)
    }

    @Nonnull
    override fun getUnsortedRoles(): Set<Role> = Collections.unmodifiableSet(roles)

    override fun getFlagsRaw(): Int = flags

    @Nonnull
    override fun getPermissions(): EnumSet<Permission> = Permission.getPermissions(PermissionUtil.getEffectivePermission(this))

    @Nonnull
    override fun getPermissions(channel: GuildChannel): EnumSet<Permission> {
        Checks.notNull(channel, "Channel")
        if (getGuild() != channel.guild) {
            throw IllegalArgumentException("Provided channel is not in the same guild as this member!")
        }
        return Permission.getPermissions(PermissionUtil.getEffectivePermission(channel, this))
    }

    @Nonnull
    override fun getPermissionsExplicit(): EnumSet<Permission> = Permission.getPermissions(PermissionUtil.getExplicitPermission(this))

    @Nonnull
    override fun getPermissionsExplicit(channel: GuildChannel): EnumSet<Permission> =
        Permission.getPermissions(PermissionUtil.getExplicitPermission(channel, this))

    override fun hasPermission(vararg permissions: Permission): Boolean = PermissionUtil.checkPermission(this, *permissions)

    override fun hasPermission(
        channel: GuildChannel,
        vararg permissions: Permission,
    ): Boolean = PermissionUtil.checkPermission(channel, this, *permissions)

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
        val userPerms = PermissionUtil.getEffectivePermission(targetChannel, this)
        if ((userPerms and Permission.MANAGE_PERMISSIONS.rawValue) == 0L) {
            return false // We can't manage permissions at all!
        }
        val channelPermissions = PermissionUtil.getExplicitPermission(targetChannel, this, false)
        // If the user has ADMINISTRATOR or MANAGE_PERMISSIONS
        // then it can also set any other permission on the channel
        val hasLocalAdmin =
            (
                (userPerms and Permission.ADMINISTRATOR.rawValue) or
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
            // If any permissions changed that the user doesn't have in the channel,
            // they can't sync it :(
            if (((allow or deny) and userPerms.inv()) != 0L) {
                return false
            }
        }
        return true
    }

    override fun canSync(channel: IPermissionContainer): Boolean {
        Checks.notNull(channel, "Channel")
        Checks.check(channel.guild == getGuild(), "Channels must be from the same guild!")
        val userPerms = PermissionUtil.getEffectivePermission(channel, this)
        if ((userPerms and Permission.MANAGE_PERMISSIONS.rawValue) == 0L) {
            return false // We can't manage permissions at all!
        }
        val channelPermissions = PermissionUtil.getExplicitPermission(channel, this, false)
        // If the user has ADMINISTRATOR or MANAGE_PERMISSIONS
        // then it can also set any other permission on the channel
        return (
            (userPerms and Permission.ADMINISTRATOR.rawValue) or
                (channelPermissions and Permission.MANAGE_PERMISSIONS.rawValue)
        ) != 0L
    }

    override fun canInteract(member: Member): Boolean = PermissionUtil.canInteract(this, member)

    override fun canInteract(role: Role): Boolean = PermissionUtil.canInteract(this, role)

    override fun canInteract(emoji: RichCustomEmoji): Boolean = PermissionUtil.canInteract(this, emoji)

    override fun isOwner(): Boolean = user.idLong == getGuild().ownerIdLong

    override fun isPending(): Boolean = pending

    override fun getIdLong(): Long = user.idLong

    @Nonnull
    override fun getAsMention(): String = "<@${user.id}>"

    @Nullable
    override fun getDefaultChannel(): DefaultGuildChannelUnion? =
        (
            Stream
                .concat(getGuild().textChannelCache.stream(), getGuild().newsChannelCache.stream())
                .filter { c -> hasPermission(c, Permission.VIEW_CHANNEL) }
                .min(Comparator.naturalOrder())
                .orElse(null)
        ) as DefaultGuildChannelUnion?

    @Nonnull
    override fun getDefaultAvatarId(): String = user.defaultAvatarId

    override fun setNickname(nickname: String): MemberImpl {
        this.nickname = nickname
        return this
    }

    override fun setAvatarId(avatarId: String): MemberImpl {
        this.avatarId = avatarId
        return this
    }

    override fun setBannerId(bannerId: String): MemberImpl {
        this.bannerId = bannerId
        return this
    }

    override fun setJoinDate(joinDate: Long): MemberImpl {
        this.joinDate = joinDate
        return this
    }

    override fun setBoostDate(boostDate: Long): MemberImpl {
        this.boostDate = boostDate
        return this
    }

    override fun setTimeOutEnd(time: Long): MemberImpl {
        this.timeOutEnd = time
        return this
    }

    override fun setPending(pending: Boolean): MemberImpl {
        this.pending = pending
        return this
    }

    override fun setFlags(flags: Int): MemberImpl {
        this.flags = flags
        return this
    }

    fun getRoleSet(): MutableSet<Role> = roles

    fun getBoostDateRaw(): Long = boostDate

    fun getTimeOutEndRaw(): Long = timeOutEnd

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is MemberImpl) {
            return false
        }

        return other.user.idLong == user.idLong && other.guild.idLong == guild.idLong
    }

    override fun hashCode(): Int = Objects.hash(guild.idLong, user.idLong)

    override fun toString(): String =
        EntityString(this)
            .setName(getEffectiveName())
            .addMetadata("user", getUser())
            .addMetadata("guild", getGuild())
            .toString()
}
