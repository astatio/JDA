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
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.ClientType
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Member.MemberFlag
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.exceptions.MissingEntityInteractionPermissionsException
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IInteractionPermissionMixin
import net.dv8tion.jda.internal.entities.mixin.MemberMixin
import net.dv8tion.jda.internal.interactions.ChannelInteractionPermissions
import net.dv8tion.jda.internal.interactions.MemberInteractionPermissions
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.time.OffsetDateTime
import java.util.EnumSet
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DetachedMemberImpl(
    private val guild: DetachedGuildImpl,
    private var user: User,
) : Member,
    MemberMixin<DetachedMemberImpl> {
    private val api: JDAImpl = user.jda as JDAImpl

    private var nickname: String? = null
    private var avatarId: String? = null
    private var bannerId: String? = null
    private var joinDate: Long = 0
    private var boostDate: Long = 0
    private var timeOutEnd: Long = 0
    private var pending: Boolean = false
    private var flags: Int = 0

    // Permissions calculated by Discord
    private lateinit var interactionPermissions: MemberInteractionPermissions

    override fun isDetached(): Boolean = true

    @Nonnull
    override fun getUser(): User {
        // The user could come from another guild
        // Load user from cache if one exists,
        // ideally two members with the same id should wrap the same user object
        val realUser = jda.getUserById(user.idLong)
        if (realUser != null) {
            this.user = realUser
        }
        return user
    }

    @Nonnull
    override fun getGuild(): Guild = guild

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

    override fun getVoiceState(): GuildVoiceState = throw detachedException()

    @Nonnull
    override fun getActivities(): List<Activity> = throw detachedException()

    @Nonnull
    override fun getOnlineStatus(): OnlineStatus = throw detachedException()

    @Nonnull
    override fun getOnlineStatus(type: ClientType): OnlineStatus = throw detachedException()

    @Nonnull
    override fun getActiveClients(): EnumSet<ClientType> = throw detachedException()

    override fun getNickname(): String? = nickname

    override fun getAvatarId(): String? = avatarId

    override fun getBannerId(): String? = bannerId

    @Nonnull
    override fun getEffectiveName(): String = nickname ?: getUser().effectiveName

    @Nonnull
    override fun getRoles(): List<Role> = throw detachedException()

    @Nonnull
    override fun getUnsortedRoles(): Set<Role> = throw detachedException()

    override fun getFlagsRaw(): Int = flags

    @Nonnull
    override fun getPermissions(): EnumSet<Permission> = throw detachedRequiresChannelException()

    @Nonnull
    override fun getPermissions(channel: GuildChannel): EnumSet<Permission> =
        Permission.getPermissions(getRawInteractionPermissions(channel))

    @Nonnull
    override fun getPermissionsExplicit(): EnumSet<Permission> = throw detachedRequiresChannelException()

    @Nonnull
    override fun getPermissionsExplicit(channel: GuildChannel): EnumSet<Permission> =
        Permission.getPermissions(getRawInteractionPermissions(channel))

    override fun hasPermission(vararg permissions: Permission): Boolean = throw detachedRequiresChannelException()

    override fun hasPermission(
        channel: GuildChannel,
        vararg permissions: Permission,
    ): Boolean {
        val rawPermissions = Permission.getRaw(*permissions)
        return (getRawInteractionPermissions(channel) and rawPermissions) == rawPermissions
    }

    private fun getRawInteractionPermissions(channel: GuildChannel): Long {
        if (interactionPermissions.channelId == channel.idLong) {
            return interactionPermissions.permissions
        }

        if (channel is IInteractionPermissionMixin<*>) {
            val channelInteractionPermissions: ChannelInteractionPermissions = channel.interactionPermissions
            if (channelInteractionPermissions.memberId == this.idLong) {
                return channelInteractionPermissions.permissions
            }
        }

        throw MissingEntityInteractionPermissionsException(
            "Detached member permissions can only be retrieved in the interaction channel, " +
                "and channels only contain the permissions of the interaction caller",
        )
    }

    override fun canSync(
        targetChannel: IPermissionContainer,
        syncSource: IPermissionContainer,
    ): Boolean = throw detachedException()

    override fun canSync(channel: IPermissionContainer): Boolean = throw detachedException()

    override fun canInteract(member: Member): Boolean = throw detachedException()

    override fun canInteract(role: Role): Boolean = throw detachedException()

    override fun canInteract(emoji: RichCustomEmoji): Boolean = throw detachedException()

    override fun isOwner(): Boolean = throw detachedException()

    override fun isPending(): Boolean = pending

    override fun getIdLong(): Long = user.idLong

    @Nonnull
    override fun getAsMention(): String = user.asMention

    @Nullable
    override fun getDefaultChannel(): DefaultGuildChannelUnion? = throw detachedException()

    @Nonnull
    override fun getDefaultAvatarId(): String = user.defaultAvatarId

    @Nonnull
    fun getInteractionPermissions(): MemberInteractionPermissions = interactionPermissions

    @Nonnull
    override fun modifyFlags(newFlags: Collection<MemberFlag>): AuditableRestAction<Void> = throw detachedException()

    override fun setNickname(nickname: String): DetachedMemberImpl {
        this.nickname = nickname
        return this
    }

    override fun setAvatarId(avatarId: String): DetachedMemberImpl {
        this.avatarId = avatarId
        return this
    }

    override fun setBannerId(bannerId: String): DetachedMemberImpl {
        this.bannerId = bannerId
        return this
    }

    override fun setJoinDate(joinDate: Long): DetachedMemberImpl {
        this.joinDate = joinDate
        return this
    }

    override fun setBoostDate(boostDate: Long): DetachedMemberImpl {
        this.boostDate = boostDate
        return this
    }

    override fun setTimeOutEnd(time: Long): DetachedMemberImpl {
        this.timeOutEnd = time
        return this
    }

    override fun setPending(pending: Boolean): DetachedMemberImpl {
        this.pending = pending
        return this
    }

    override fun setFlags(flags: Int): DetachedMemberImpl {
        this.flags = flags
        return this
    }

    fun setInteractionPermissions(interactionPermissions: MemberInteractionPermissions): DetachedMemberImpl {
        this.interactionPermissions = interactionPermissions
        return this
    }

    fun getBoostDateRaw(): Long = boostDate

    fun getTimeOutEndRaw(): Long = timeOutEnd

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is DetachedMemberImpl) {
            return false
        }
        return other.user.idLong == user.idLong && other.guild.idLong == guild.idLong
    }

    override fun hashCode(): Int = Objects.hash(guild.idLong, user.idLong)

    override fun toString(): String =
        EntityString(this)
            .setName(effectiveName)
            .addMetadata("user", getUser())
            .toString()
}
