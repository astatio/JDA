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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.User.PrimaryGuild
import net.dv8tion.jda.api.entities.User.Profile
import net.dv8tion.jda.api.entities.User.UserFlag
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.concrete.PrivateChannelImpl
import net.dv8tion.jda.internal.requests.DeferredRestAction
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.util.EnumSet
import java.util.FormattableFlags
import java.util.Formatter
import javax.annotation.Nonnull
import javax.annotation.Nullable

// The pre-global-name discriminator system used modulo-5 avatar buckets
private const val LEGACY_DEFAULT_AVATAR_COUNT = 5

open class UserImpl(
    id: Long,
    @JvmField protected val api: JDAImpl,
) : UserSnowflakeImpl(id),
    User {
    @JvmField protected var discriminator: Short = 0

    @JvmField protected var name: String? = null

    @JvmField protected var globalName: String? = null

    @JvmField protected var avatarId: String? = null

    @JvmField protected var profile: Profile? = null

    @JvmField protected var privateChannelId: Long = 0L

    @JvmField protected var bot: Boolean = false

    @JvmField protected var system: Boolean = false

    @JvmField protected var flags: Int = 0

    @JvmField protected var primaryGuild: PrimaryGuild? = null

    @Nonnull
    override fun getName(): String = name!!

    @Nullable
    override fun getGlobalName(): String? = globalName

    @Nonnull
    override fun getDiscriminator(): String = if (discriminator.toInt() == 0) "0000" else Helpers.format("%04d", discriminator)

    @Nullable
    override fun getAvatarId(): String? = avatarId

    @Nonnull
    override fun retrieveProfile(): CacheRestAction<Profile> =
        DeferredRestAction(getJDA(), Profile::class.java, { profile }) {
            val route = Route.Users.GET_USER.compile(getId())
            RestActionImpl(getJDA(), route) { response, _ ->
                val json = response.`object`

                val bannerId = json.getString("banner", null)
                val accentColor = json.getInt("accent_color", User.DEFAULT_ACCENT_COLOR_RAW)

                Profile(id, bannerId, accentColor)
            }
        }

    fun getProfile(): Profile? = profile

    @Nonnull
    override fun getDefaultAvatarId(): String =
        // Backwards compatibility with old discriminator system
        if (discriminator.toInt() != 0) (discriminator % LEGACY_DEFAULT_AVATAR_COUNT).toString() else super.getDefaultAvatarId()

    @Nonnull
    override fun getAsTag(): String = getName() + '#' + getDiscriminator()

    override fun hasPrivateChannel(): Boolean = privateChannelId != 0L

    @Nonnull
    override fun openPrivateChannel(): CacheRestAction<PrivateChannel> =
        DeferredRestAction(getJDA(), PrivateChannel::class.java, { getPrivateChannel() }) {
            val route = Route.Self.CREATE_PRIVATE_CHANNEL.compile()
            val body = DataObject.empty().put("recipient_id", getId())
            RestActionImpl(getJDA(), route, body) { response, _ ->
                val priv = api.entityBuilder.createPrivateChannel(response.`object`, this)
                this.privateChannelId = priv.idLong
                priv
            }
        }

    open fun getPrivateChannel(): PrivateChannel? {
        if (!hasPrivateChannel()) {
            return null
        }
        return getJDA().getPrivateChannelById(privateChannelId)
            ?: PrivateChannelImpl(getJDA(), privateChannelId, this)
    }

    @Nonnull
    override fun getMutualGuilds(): List<Guild> = getJDA().getMutualGuilds(this)

    override fun isBot(): Boolean = bot

    override fun isSystem(): Boolean = system

    @Nonnull
    override fun getJDA(): JDAImpl = api

    @Nonnull
    override fun getFlags(): EnumSet<UserFlag> = UserFlag.getFlags(flags)

    override fun getFlagsRaw(): Int = flags

    @Nullable
    override fun getPrimaryGuild(): PrimaryGuild? = primaryGuild

    override fun toString(): String = EntityString(this).setName(getName()).toString()

    // -- Setters --

    fun setName(name: String): UserImpl {
        this.name = name
        return this
    }

    fun setGlobalName(globalName: String?): UserImpl {
        this.globalName = globalName
        return this
    }

    fun setDiscriminator(discriminator: Short): UserImpl {
        this.discriminator = discriminator
        return this
    }

    fun setAvatarId(avatarId: String?): UserImpl {
        this.avatarId = avatarId
        return this
    }

    fun setProfile(profile: Profile?): UserImpl {
        this.profile = profile
        return this
    }

    fun setPrivateChannel(privateChannel: PrivateChannel?): UserImpl {
        if (privateChannel != null) {
            this.privateChannelId = privateChannel.idLong
        }
        return this
    }

    fun setBot(bot: Boolean): UserImpl {
        this.bot = bot
        return this
    }

    fun setSystem(system: Boolean): UserImpl {
        this.system = system
        return this
    }

    fun setFlags(flags: Int): UserImpl {
        this.flags = flags
        return this
    }

    fun setPrimaryGuild(primaryGuild: PrimaryGuild?): UserImpl {
        this.primaryGuild = primaryGuild
        return this
    }

    fun getDiscriminatorInt(): Short = discriminator

    override fun formatTo(
        formatter: Formatter,
        flags: Int,
        width: Int,
        precision: Int,
    ) {
        val alt = (flags and FormattableFlags.ALTERNATE) == FormattableFlags.ALTERNATE
        val upper = (flags and FormattableFlags.UPPERCASE) == FormattableFlags.UPPERCASE
        val leftJustified = (flags and FormattableFlags.LEFT_JUSTIFY) == FormattableFlags.LEFT_JUSTIFY

        val out: String =
            when {
                !alt -> getAsMention()
                discriminator.toInt() == 0 && upper -> getName().uppercase(formatter.locale())
                discriminator.toInt() == 0 -> getName()
                upper -> getAsTag().uppercase(formatter.locale())
                else -> getAsTag()
            }

        MiscUtil.appendTo(formatter, width, precision, leftJustified, out)
    }
}
