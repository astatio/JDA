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
import net.dv8tion.jda.api.entities.Guild.NSFWLevel
import net.dv8tion.jda.api.entities.Guild.VerificationLevel
import net.dv8tion.jda.api.entities.GuildWelcomeScreen
import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.entities.Invite.EmbeddedApplication
import net.dv8tion.jda.api.entities.Invite.Group
import net.dv8tion.jda.api.entities.Invite.InviteTarget
import net.dv8tion.jda.api.entities.Invite.InviteType
import net.dv8tion.jda.api.entities.Invite.TargetType
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.utils.ImageFormat
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.requests.CompletedRestAction
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.time.OffsetDateTime
import javax.annotation.Nonnull
import javax.annotation.Nullable

class InviteImpl(
    private val api: JDAImpl,
    private val code: String,
    private val expanded: Boolean,
    @Nullable private val inviter: User?,
    private val maxAge: Int,
    private val maxUses: Int,
    private val temporary: Boolean,
    private val guest: Boolean,
    @Nullable private val timeCreated: OffsetDateTime?,
    private val uses: Int,
    @Nullable private val channel: Invite.Channel?,
    @Nullable private val guild: Invite.Guild?,
    @Nullable private val group: Group?,
    @Nullable private val target: InviteTarget?,
    private val type: InviteType,
) : Invite {
    companion object {
        @JvmStatic
        fun resolve(
            api: JDA,
            code: String,
            withCounts: Boolean,
        ): RestAction<Invite> {
            Checks.notNull(code, "code")
            Checks.notNull(api, "api")

            var route = Route.Invites.GET_INVITE.compile(code)

            if (withCounts) {
                route = route.withQueryParams("with_counts", "true")
            }

            val jda = api as JDAImpl
            return RestActionImpl(api, route) { response, _ -> jda.entityBuilder.createInvite(response.getObject()) }
        }
    }

    @Nonnull
    override fun delete(): AuditableRestAction<Void> {
        val route = Route.Invites.DELETE_INVITE.compile(code)

        return AuditableRestActionImpl(api, route)
    }

    @Nonnull
    // Mirrors the original Java control flow, which validates each precondition with a direct throw.
    @Suppress("ThrowsCount")
    override fun expand(): RestAction<Invite> {
        if (expanded) {
            return CompletedRestAction(getJDA(), this)
        }

        if (type != InviteType.GUILD) {
            throw IllegalStateException("Only guild invites can be expanded")
        }

        val guild = api.getGuildById(this.guild!!.idLong)

        if (guild == null) {
            throw UnsupportedOperationException("You're not in the guild this invite points to")
        }

        val member = guild.selfMember

        val route: Route.CompiledRoute

        val channel = guild.getChannelById(GuildChannel::class.java, this.channel!!.idLong)
        if (channel == null) {
            throw UnsupportedOperationException(
                "Cannot expand invite without known channel. Channel ID: " + this.channel.id,
            )
        }

        if (member.hasPermission(channel, Permission.MANAGE_CHANNEL)) {
            route = Route.Invites.GET_CHANNEL_INVITES.compile(channel.id)
        } else if (member.hasPermission(Permission.MANAGE_SERVER)) {
            route = Route.Invites.GET_GUILD_INVITES.compile(guild.id)
        } else {
            throw InsufficientPermissionException(
                channel,
                Permission.MANAGE_CHANNEL,
                "You don't have the permission to view the full invite info",
            )
        }

        return RestActionImpl(api, route) { response, _ ->
            val entityBuilder = api.entityBuilder
            val array: DataArray = response.getArray()
            for (i in 0 until array.length()) {
                val `object` = array.getObject(i)
                if (code == `object`.getString("code")) {
                    return@RestActionImpl entityBuilder.createInvite(`object`)
                }
            }
            throw IllegalStateException("Missing the invite in the channel/guild invite list")
        }
    }

    @Nonnull
    override fun getType(): InviteType = type

    @Nonnull
    override fun getTargetType(): TargetType = target?.type ?: TargetType.NONE

    @Nullable
    override fun getChannel(): Invite.Channel? = channel

    @Nonnull
    override fun getCode(): String = code

    @Nullable
    override fun getGuild(): Invite.Guild? = guild

    @Nullable
    override fun getGroup(): Group? = group

    @Nullable
    override fun getTarget(): InviteTarget? = target

    @Nullable
    override fun getInviter(): User? = inviter

    @Nonnull
    override fun getJDA(): JDAImpl = api

    override fun getMaxAge(): Int {
        if (!expanded) {
            throw IllegalStateException("Only valid for expanded invites")
        }
        return maxAge
    }

    override fun getMaxUses(): Int {
        if (!expanded) {
            throw IllegalStateException("Only valid for expanded invites")
        }
        return maxUses
    }

    @Nonnull
    override fun getTimeCreated(): OffsetDateTime {
        if (!expanded) {
            throw IllegalStateException("Only valid for expanded invites")
        }
        return timeCreated!!
    }

    override fun getUses(): Int {
        if (!expanded) {
            throw IllegalStateException("Only valid for expanded invites")
        }
        return uses
    }

    override fun isExpanded(): Boolean = expanded

    override fun isTemporary(): Boolean {
        if (!expanded) {
            throw IllegalStateException("Only valid for expanded invites")
        }
        return temporary
    }

    override fun isGuest(): Boolean = guest

    override fun hashCode(): Int = code.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is InviteImpl) {
            return false
        }
        return other.code == code
    }

    override fun toString(): String = EntityString(this).addMetadata("code", code).toString()

    class ChannelImpl(
        private val id: Long,
        private val name: String,
        private val type: ChannelType,
    ) : Invite.Channel {
        constructor(channel: GuildChannel) : this(channel.idLong, channel.name, channel.type)

        override fun getIdLong(): Long = id

        @Nonnull
        override fun getName(): String = name

        @Nonnull
        override fun getType(): ChannelType = type

        override fun toString(): String = EntityString(this).setType(getType()).setName(name).toString()
    }

    class GuildImpl(
        private val id: Long,
        @Nullable private val vanityCode: String?,
        @Nullable private val bannerId: String?,
        @Nullable private val iconId: String?,
        private val name: String,
        @Nullable private val splashId: String?,
        @Nullable private val description: String?,
        private val verificationLevel: VerificationLevel,
        private val nsfwLevel: NSFWLevel,
        private val presenceCount: Int,
        private val memberCount: Int,
        private val features: Set<String>,
        @Nullable private val welcomeScreen: GuildWelcomeScreen?,
    ) : Invite.Guild {
        constructor(guild: Guild) : this(
            guild.idLong,
            guild.vanityCode,
            guild.bannerId,
            guild.iconId,
            guild.name,
            guild.splashId,
            guild.description,
            guild.verificationLevel,
            guild.nsfwLevel,
            -1,
            -1,
            guild.features,
            null,
        )

        @Nullable
        override fun getVanityCode(): String? = vanityCode

        @Nullable
        override fun getBannerId(): String? = bannerId

        @Nullable
        override fun getDescription(): String? = description

        @Nullable
        override fun getIconId(): String? = iconId

        @Nullable
        override fun getIconUrl(): String? = getIconUrl(ImageFormat.PNG)

        override fun getIdLong(): Long = id

        @Nonnull
        override fun getName(): String = name

        @Nullable
        override fun getSplashId(): String? = splashId

        @Nullable
        override fun getSplashUrl(): String? = getSplashUrl(ImageFormat.PNG)

        @Nonnull
        override fun getVerificationLevel(): VerificationLevel = verificationLevel

        @Nonnull
        override fun getNSFWLevel(): NSFWLevel = nsfwLevel

        override fun getOnlineCount(): Int = presenceCount

        override fun getMemberCount(): Int = memberCount

        @Nonnull
        override fun getFeatures(): Set<String> = features

        @Nullable
        override fun getWelcomeScreen(): GuildWelcomeScreen? = welcomeScreen

        override fun toString(): String = EntityString(this).setName(name).toString()
    }

    class GroupImpl(
        @Nullable private val iconId: String?,
        private val name: String,
        private val id: Long,
        @Nullable private val users: List<String>?,
    ) : Group {
        @Nullable
        override fun getIconId(): String? = iconId

        @Nullable
        override fun getIconUrl(): String? = getIconUrl(ImageFormat.PNG)

        @Nonnull
        override fun getName(): String = name

        override fun getIdLong(): Long = id

        @Nullable
        override fun getUsers(): List<String>? = users

        override fun toString(): String = EntityString(this).setName(name).toString()
    }

    class InviteTargetImpl(
        private val type: TargetType,
        @Nullable private val targetApplication: EmbeddedApplication?,
        @Nullable private val targetUser: User?,
    ) : InviteTarget {
        @Nonnull
        override fun getType(): TargetType = type

        @Nonnull
        override fun getId(): String = getTargetEntity().id

        override fun getIdLong(): Long = getTargetEntity().idLong

        @Nullable
        override fun getUser(): User? = targetUser

        @Nullable
        override fun getApplication(): EmbeddedApplication? = targetApplication

        override fun toString(): String =
            EntityString(this)
                .setType(getType())
                .addMetadata("target", getTargetEntity())
                .toString()

        @Nonnull
        private fun getTargetEntity(): ISnowflake {
            if (targetUser != null) {
                return targetUser
            }
            if (targetApplication != null) {
                return targetApplication
            }
            throw IllegalStateException("No target entity")
        }
    }

    class EmbeddedApplicationImpl(
        @Nullable private val iconId: String?,
        private val name: String,
        private val description: String,
        @Nullable private val summary: String?,
        private val id: Long,
        private val maxParticipants: Int,
    ) : EmbeddedApplication {
        override fun getIdLong(): Long = id

        @Nonnull
        override fun getName(): String = name

        @Nonnull
        override fun getDescription(): String = description

        @Nullable
        override fun getSummary(): String? = summary

        @Nullable
        override fun getIconId(): String? = iconId

        @Nullable
        override fun getIconUrl(): String? = getIconUrl(ImageFormat.PNG)

        override fun getMaxParticipants(): Int = maxParticipants

        override fun toString(): String = EntityString(this).setName(name).toString()
    }
}
