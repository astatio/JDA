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
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.guild.SystemChannelFlag
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.GuildManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import okhttp3.RequestBody
import java.util.HashSet
import java.util.Locale
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

private const val NAME_MAX_LENGTH = 100

class GuildManagerImpl(
    @JvmField protected var guild: Guild,
) : ManagerBase<GuildManager>(
        guild.getJDA(),
        Route.Guilds.MODIFY_GUILD.compile(guild.getId()),
    ),
    GuildManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var name: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var icon: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var splash: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var banner: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var afkChannel: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var systemChannel: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var rulesChannel: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var communityUpdatesChannel: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var safetyAlertsChannel: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var description: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var afkTimeout: Int = 0

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var notificationLevel: Int = 0

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var explicitContentLevel: Int = 0

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var verificationLevel: Int = 0

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var boostProgressBarEnabled: Boolean = false

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var features: MutableSet<String>? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var systemChannelFlags: MutableSet<SystemChannelFlag>? = null

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    @Nonnull
    override fun getGuild(): Guild {
        val realGuild = api.getGuildById(guild.getIdLong())
        if (realGuild != null) {
            guild = realGuild
        }
        return guild
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): GuildManagerImpl {
        super.reset(fields)
        if (fields and GuildManager.NAME == GuildManager.NAME) {
            name = null
        }
        if (fields and GuildManager.ICON == GuildManager.ICON) {
            icon = null
        }
        if (fields and GuildManager.SPLASH == GuildManager.SPLASH) {
            splash = null
        }
        if (fields and GuildManager.AFK_CHANNEL == GuildManager.AFK_CHANNEL) {
            afkChannel = null
        }
        if (fields and GuildManager.SYSTEM_CHANNEL == GuildManager.SYSTEM_CHANNEL) {
            systemChannel = null
        }
        if (fields and GuildManager.RULES_CHANNEL == GuildManager.RULES_CHANNEL) {
            rulesChannel = null
        }
        if (fields and GuildManager.COMMUNITY_UPDATES_CHANNEL == GuildManager.COMMUNITY_UPDATES_CHANNEL) {
            communityUpdatesChannel = null
        }
        if (fields and GuildManager.SAFETY_ALERTS_CHANNEL == GuildManager.SAFETY_ALERTS_CHANNEL) {
            safetyAlertsChannel = null
        }
        if (fields and GuildManager.DESCRIPTION == GuildManager.DESCRIPTION) {
            description = null
        }
        if (fields and GuildManager.BANNER == GuildManager.BANNER) {
            banner = null
        }
        if (fields and GuildManager.FEATURES == GuildManager.FEATURES) {
            features = null
        }
        if (fields and GuildManager.SYSTEM_CHANNEL_FLAGS == GuildManager.SYSTEM_CHANNEL_FLAGS) {
            systemChannelFlags = null
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): GuildManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): GuildManagerImpl {
        super.reset()
        name = null
        icon = null
        splash = null
        description = null
        banner = null
        afkChannel = null
        systemChannel = null
        features = null
        systemChannelFlags = null
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): GuildManagerImpl {
        Checks.notEmpty(name, "Name")
        Checks.notLonger(name, NAME_MAX_LENGTH, "Name")
        this.name = name
        set = set or GuildManager.NAME
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setIcon(icon: Icon?): GuildManagerImpl {
        this.icon = icon
        set = set or GuildManager.ICON
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setSplash(splash: Icon?): GuildManagerImpl {
        checkFeature("INVITE_SPLASH")
        this.splash = splash
        set = set or GuildManager.SPLASH
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setAfkChannel(afkChannel: VoiceChannel?): GuildManagerImpl {
        Checks.check(
            afkChannel == null || afkChannel.getGuild() == getGuild(),
            "Channel must be from the same guild",
        )
        this.afkChannel = afkChannel?.id
        set = set or GuildManager.AFK_CHANNEL
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setSystemChannel(systemChannel: TextChannel?): GuildManagerImpl {
        Checks.check(
            systemChannel == null || systemChannel.getGuild() == getGuild(),
            "Channel must be from the same guild",
        )
        this.systemChannel = systemChannel?.id
        set = set or GuildManager.SYSTEM_CHANNEL
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setRulesChannel(rulesChannel: TextChannel?): GuildManagerImpl {
        Checks.check(
            rulesChannel == null || rulesChannel.getGuild() == getGuild(),
            "Channel must be from the same guild",
        )
        this.rulesChannel = rulesChannel?.id
        set = set or GuildManager.RULES_CHANNEL
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setCommunityUpdatesChannel(communityUpdatesChannel: TextChannel?): GuildManagerImpl {
        Checks.check(
            communityUpdatesChannel == null || communityUpdatesChannel.getGuild() == getGuild(),
            "Channel must be from the same guild",
        )
        this.communityUpdatesChannel = communityUpdatesChannel?.id
        set = set or GuildManager.COMMUNITY_UPDATES_CHANNEL
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setSafetyAlertsChannel(safetyAlertsChannel: TextChannel?): GuildManagerImpl {
        Checks.check(
            safetyAlertsChannel == null || safetyAlertsChannel.getGuild() == getGuild(),
            "Channel must be from the same guild",
        )
        this.safetyAlertsChannel = safetyAlertsChannel?.id
        set = set or GuildManager.SAFETY_ALERTS_CHANNEL
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setAfkTimeout(
        @Nonnull timeout: Guild.Timeout,
    ): GuildManagerImpl {
        Checks.notNull(timeout, "Timeout")
        afkTimeout = timeout.seconds
        set = set or GuildManager.AFK_TIMEOUT
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setVerificationLevel(
        @Nonnull level: Guild.VerificationLevel,
    ): GuildManagerImpl {
        Checks.notNull(level, "Level")
        Checks.check(level != Guild.VerificationLevel.UNKNOWN, "Level must not be UNKNOWN")
        verificationLevel = level.key
        set = set or GuildManager.VERIFICATION_LEVEL
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setDefaultNotificationLevel(
        @Nonnull level: Guild.NotificationLevel,
    ): GuildManagerImpl {
        Checks.notNull(level, "Level")
        Checks.check(level != Guild.NotificationLevel.UNKNOWN, "Level must not be UNKNOWN")
        notificationLevel = level.key
        set = set or GuildManager.NOTIFICATION_LEVEL
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setExplicitContentLevel(
        @Nonnull level: Guild.ExplicitContentLevel,
    ): GuildManagerImpl {
        Checks.notNull(level, "Level")
        Checks.check(level != Guild.ExplicitContentLevel.UNKNOWN, "Level must not be UNKNOWN")
        explicitContentLevel = level.key
        set = set or GuildManager.EXPLICIT_CONTENT_LEVEL
        return this
    }

    @Nonnull
    override fun setBanner(banner: Icon?): GuildManager {
        checkFeature("BANNER")
        this.banner = banner
        set = set or GuildManager.BANNER
        return this
    }

    @Nonnull
    override fun setDescription(description: String?): GuildManager {
        checkFeature("VERIFIED")
        this.description = description
        set = set or GuildManager.DESCRIPTION
        return this
    }

    @Nonnull
    override fun setBoostProgressBarEnabled(enabled: Boolean): GuildManager {
        boostProgressBarEnabled = enabled
        set = set or GuildManager.BOOST_PROGRESS_BAR_ENABLED
        return this
    }

    @Nonnull
    override fun setFeatures(
        @Nonnull features: Collection<String>,
    ): GuildManager {
        Checks.noneNull(features, "Features")
        this.features = features.stream().map { it.uppercase(Locale.ROOT) }.collect(Collectors.toSet())
        set = set or GuildManager.FEATURES
        return this
    }

    @Nonnull
    override fun addFeatures(
        @Nonnull features: Collection<String>,
    ): GuildManager = updateFeatures(features) { this.features!!.add(it) }

    @Nonnull
    override fun removeFeatures(
        @Nonnull features: Collection<String>,
    ): GuildManager = updateFeatures(features) { this.features!!.remove(it) }

    private fun updateFeatures(
        changed: Collection<String>,
        op: Consumer<String>,
    ): GuildManager {
        Checks.noneNull(changed, "Features")
        if (features == null) {
            features = HashSet(getGuild().features)
        }
        changed.stream().map { it.uppercase(Locale.ROOT) }.forEach(op)
        set = set or GuildManager.FEATURES
        return this
    }

    @Nonnull
    override fun setSystemChannelFlags(
        @Nonnull flags: Collection<SystemChannelFlag>,
    ): GuildManager {
        Checks.noneNull(flags, "System channel flag")
        systemChannelFlags = Helpers.copyEnumSet(SystemChannelFlag::class.java, flags)
        set = set or GuildManager.SYSTEM_CHANNEL_FLAGS
        return this
    }

    @Nonnull
    override fun enableSystemChannelFlags(
        @Nonnull flags: Collection<SystemChannelFlag>,
    ): GuildManager = updateSystemChannelFlags(flags) { s, c -> s.addAll(c) }

    @Nonnull
    override fun disableSystemChannelFlags(
        @Nonnull flags: Collection<SystemChannelFlag>,
    ): GuildManager = updateSystemChannelFlags(flags) { s, c -> s.removeAll(c) }

    private fun updateSystemChannelFlags(
        flags: Collection<SystemChannelFlag>,
        bulkUpdateOp: BiConsumer<MutableSet<SystemChannelFlag>, Collection<SystemChannelFlag>>,
    ): GuildManager {
        Checks.noneNull(flags, "System channel flag")
        if (systemChannelFlags == null) {
            systemChannelFlags = Helpers.copyEnumSet(SystemChannelFlag::class.java, getGuild().systemChannelFlags)
        }
        bulkUpdateOp.accept(systemChannelFlags!!, flags)
        set = set or GuildManager.SYSTEM_CHANNEL_FLAGS
        return this
    }

    override fun finalizeData(): RequestBody {
        val body = DataObject.empty().put("name", getGuild().name)
        if (shouldUpdate(GuildManager.NAME)) {
            body.put("name", name)
        }
        if (shouldUpdate(GuildManager.AFK_TIMEOUT)) {
            body.put("afk_timeout", afkTimeout)
        }
        if (shouldUpdate(GuildManager.ICON)) {
            body.put("icon", icon?.getEncoding())
        }
        if (shouldUpdate(GuildManager.SPLASH)) {
            body.put("splash", splash?.getEncoding())
        }
        if (shouldUpdate(GuildManager.AFK_CHANNEL)) {
            body.put("afk_channel_id", afkChannel)
        }
        if (shouldUpdate(GuildManager.SYSTEM_CHANNEL)) {
            body.put("system_channel_id", systemChannel)
        }
        if (shouldUpdate(GuildManager.RULES_CHANNEL)) {
            body.put("rules_channel_id", rulesChannel)
        }
        if (shouldUpdate(GuildManager.COMMUNITY_UPDATES_CHANNEL)) {
            body.put("public_updates_channel_id", communityUpdatesChannel)
        }
        if (shouldUpdate(GuildManager.SAFETY_ALERTS_CHANNEL)) {
            body.put("safety_alerts_channel_id", safetyAlertsChannel)
        }
        if (shouldUpdate(GuildManager.VERIFICATION_LEVEL)) {
            body.put("verification_level", verificationLevel)
        }
        if (shouldUpdate(GuildManager.NOTIFICATION_LEVEL)) {
            body.put("default_message_notifications", notificationLevel)
        }
        if (shouldUpdate(GuildManager.EXPLICIT_CONTENT_LEVEL)) {
            body.put("explicit_content_filter", explicitContentLevel)
        }
        if (shouldUpdate(GuildManager.BANNER)) {
            body.put("banner", banner?.getEncoding())
        }
        if (shouldUpdate(GuildManager.DESCRIPTION)) {
            body.put("description", description)
        }
        if (shouldUpdate(GuildManager.BOOST_PROGRESS_BAR_ENABLED)) {
            body.put("premium_progress_bar_enabled", boostProgressBarEnabled)
        }
        if (shouldUpdate(GuildManager.FEATURES)) {
            body.put("features", features)
        }
        if (shouldUpdate(GuildManager.SYSTEM_CHANNEL_FLAGS)) {
            body.put("system_channel_flags", SystemChannelFlag.getRaw(systemChannelFlags!!))
        }

        reset()
        return getRequestBody(body)
    }

    override fun checkPermissions(): Boolean {
        if (!getGuild().selfMember.hasPermission(Permission.MANAGE_SERVER)) {
            throw InsufficientPermissionException(getGuild(), Permission.MANAGE_SERVER)
        }
        return super.checkPermissions()
    }

    private fun checkFeature(feature: String) {
        if (!getGuild().features.contains(feature)) {
            throw IllegalStateException("This guild doesn't have the $feature feature enabled")
        }
    }
}
