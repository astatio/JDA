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

package net.dv8tion.jda.internal.handle

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions
import net.dv8tion.jda.api.entities.guild.SecurityIncidentDetections
import net.dv8tion.jda.api.entities.guild.SystemChannelFlag
import net.dv8tion.jda.api.events.guild.update.GuildUpdateAfkChannelEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateAfkTimeoutEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBannerEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBoostCountEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBoostTierEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateCommunityUpdatesChannelEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateDescriptionEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateExplicitContentLevelEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateFeaturesEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateIconEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateLocaleEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateMFALevelEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateMaxMembersEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateMaxPresencesEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNSFWLevelEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNotificationLevelEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateOwnerEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateRulesChannelEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateSafetyAlertsChannelEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateSecurityIncidentActionsEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateSecurityIncidentDetectionsEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateSplashEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateSystemChannelEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateSystemChannelFlagsEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateVanityCodeEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateVerificationLevelEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.requests.WebSocketClient
import java.util.Collections
import java.util.stream.Collectors

class GuildUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount", "LongMethod") // faithfully ported sequential update/event dispatch from Java
    protected override fun handleInternally(content: DataObject): Long? {
        val id = content.getLong("id")
        if (getJDA().getGuildSetupController().isLocked(id)) {
            return id
        }

        val guild = getJDA().getGuildById(id) as GuildImpl?
        if (guild == null) {
            EventCache.LOG.debug("Caching GUILD_UPDATE for guild with id: {}", id)
            getJDA().getEventCache().cache(EventCache.Type.GUILD, id, responseNumber, allContent, this::handle)
            return null
        }

        // When member limits aren't initialized we don't fire an update event for them
        val maxMembers = content.getInt("max_members", 0)
        val maxPresences = content.getInt("max_presences", 5000)
        if (guild.getMaxMembers() == 0) {
            // Initialize member limits to avoid unwanted update events
            guild.setMaxPresences(maxPresences)
            guild.setMaxMembers(maxMembers)
        }

        val ownerId = content.getLong("owner_id")
        val boostCount = content.getInt("premium_subscription_count", 0)
        val boostTier = content.getInt("premium_tier", 0)
        val description = content.getString("description", null)
        val vanityCode = content.getString("vanity_url_code", null)
        val bannerId = content.getString("banner", null)
        val name = content.getString("name")
        val iconId = content.getString("icon", null)
        val splashId = content.getString("splash", null)
        val securityIncidentActions =
            content
                .optObject("incidents_data")
                .map(api.getEntityBuilder()::createSecurityIncidentsActions)
                .orElse(SecurityIncidentActions.disabled())
        val securityIncidentDetections =
            content
                .optObject("incidents_data")
                .map(api.getEntityBuilder()::createSecurityIncidentsDetections)
                .orElse(SecurityIncidentDetections.EMPTY)
        val verificationLevel = Guild.VerificationLevel.fromKey(content.getInt("verification_level"))
        val notificationLevel = Guild.NotificationLevel.fromKey(content.getInt("default_message_notifications"))
        val mfaLevel = Guild.MFALevel.fromKey(content.getInt("mfa_level"))
        val nsfwLevel = Guild.NSFWLevel.fromKey(content.getInt("nsfw_level", -1))
        val explicitContentLevel = Guild.ExplicitContentLevel.fromKey(content.getInt("explicit_content_filter"))
        val afkTimeout = Guild.Timeout.fromKey(content.getInt("afk_timeout"))
        val locale = DiscordLocale.from(content.getString("preferred_locale", "en-US"))
        val afkChannel: VoiceChannel? =
            if (content.isNull("afk_channel_id")) {
                null
            } else {
                guild.getChannelById(VoiceChannel::class.java, content.getLong("afk_channel_id"))
            }
        val systemChannel: TextChannel? =
            if (content.isNull("system_channel_id")) {
                null
            } else {
                guild.getChannelById(TextChannel::class.java, content.getLong("system_channel_id"))
            }
        val rulesChannel: TextChannel? =
            if (content.isNull("rules_channel_id")) {
                null
            } else {
                guild.getChannelById(TextChannel::class.java, content.getLong("rules_channel_id"))
            }
        val communityUpdatesChannel: TextChannel? =
            if (content.isNull("public_updates_channel_id")) {
                null
            } else {
                guild.getChannelById(TextChannel::class.java, content.getLong("public_updates_channel_id"))
            }
        val safetyAlertsChannel: TextChannel? =
            if (content.isNull("safety_alerts_channel_id")) {
                null
            } else {
                guild.getChannelById(TextChannel::class.java, content.getLong("safety_alerts_channel_id"))
            }

        val systemChannelFlagBitmask = content.getInt("system_channel_flags", 0)

        val features: Set<String> =
            if (!content.isNull("features")) {
                val featureArr = content.getArray("features")
                featureArr.stream(DataArray::getString).map(String::intern).collect(Collectors.toSet())
            } else {
                Collections.emptySet()
            }

        if (ownerId != guild.getOwnerIdLong()) {
            val oldOwnerId = guild.getOwnerIdLong()
            val oldOwner: Member? = guild.getOwner()
            val newOwner: Member? = guild.getMembersView().get(ownerId)
            if (newOwner == null) {
                WebSocketClient.LOG.debug(
                    "Received {} with owner not in cache. UserId: {} GuildId: {}",
                    allContent.get("t"),
                    ownerId,
                    id,
                )
            }
            guild.setOwner(newOwner)
            guild.setOwnerId(ownerId)
            getJDA().handleEvent(
                GuildUpdateOwnerEvent(getJDA(), responseNumber, guild, oldOwner, oldOwnerId, ownerId),
            )
        }
        if (systemChannelFlagBitmask != guild.getSystemChannelFlagsRaw()) {
            val oldSystemChannelFlags = guild.getSystemChannelFlags()
            val systemChannelFlags: Set<SystemChannelFlag> =
                Collections.unmodifiableSet(SystemChannelFlag.getFlags(systemChannelFlagBitmask))
            guild.setSystemChannelFlags(systemChannelFlagBitmask)
            getJDA().handleEvent(
                GuildUpdateSystemChannelFlagsEvent(
                    getJDA(),
                    responseNumber,
                    guild,
                    oldSystemChannelFlags,
                    systemChannelFlags,
                ),
            )
        }
        if (description != guild.getDescription()) {
            val oldDescription = guild.getDescription()
            guild.setDescription(description)
            getJDA().handleEvent(GuildUpdateDescriptionEvent(getJDA(), responseNumber, guild, oldDescription))
        }
        if (bannerId != guild.getBannerId()) {
            val oldBanner = guild.getBannerId()
            guild.setBannerId(bannerId)
            getJDA().handleEvent(GuildUpdateBannerEvent(getJDA(), responseNumber, guild, oldBanner))
        }
        if (vanityCode != guild.getVanityCode()) {
            val oldCode = guild.getVanityCode()
            guild.setVanityCode(vanityCode)
            getJDA().handleEvent(GuildUpdateVanityCodeEvent(getJDA(), responseNumber, guild, oldCode))
        }
        if (maxMembers != guild.getMaxMembers()) {
            val oldMax = guild.getMaxMembers()
            guild.setMaxMembers(maxMembers)
            getJDA().handleEvent(GuildUpdateMaxMembersEvent(getJDA(), responseNumber, guild, oldMax))
        }
        if (maxPresences != guild.getMaxPresences()) {
            val oldMax = guild.getMaxPresences()
            guild.setMaxPresences(maxPresences)
            getJDA().handleEvent(GuildUpdateMaxPresencesEvent(getJDA(), responseNumber, guild, oldMax))
        }
        if (boostCount != guild.getBoostCount()) {
            val oldCount = guild.getBoostCount()
            guild.setBoostCount(boostCount)
            getJDA().handleEvent(GuildUpdateBoostCountEvent(getJDA(), responseNumber, guild, oldCount))
        }
        if (Guild.BoostTier.fromKey(boostTier) != guild.getBoostTier()) {
            val oldTier = guild.getBoostTier()
            guild.setBoostTier(boostTier)
            getJDA().handleEvent(GuildUpdateBoostTierEvent(getJDA(), responseNumber, guild, oldTier))
        }
        if (name != guild.getName()) {
            val oldName = guild.getName()
            guild.setName(name)
            getJDA().handleEvent(GuildUpdateNameEvent(getJDA(), responseNumber, guild, oldName))
        }
        if (iconId != guild.getIconId()) {
            val oldIconId = guild.getIconId()
            guild.setIconId(iconId)
            getJDA().handleEvent(GuildUpdateIconEvent(getJDA(), responseNumber, guild, oldIconId))
        }
        if (features != guild.getFeatures()) {
            val oldFeatures = guild.getFeatures()
            guild.setFeatures(features)
            getJDA().handleEvent(GuildUpdateFeaturesEvent(getJDA(), responseNumber, guild, oldFeatures))
        }
        if (splashId != guild.getSplashId()) {
            val oldSplashId = guild.getSplashId()
            guild.setSplashId(splashId)
            getJDA().handleEvent(GuildUpdateSplashEvent(getJDA(), responseNumber, guild, oldSplashId))
        }
        if (verificationLevel != guild.getVerificationLevel()) {
            val oldVerificationLevel = guild.getVerificationLevel()
            guild.setVerificationLevel(verificationLevel)
            getJDA().handleEvent(
                GuildUpdateVerificationLevelEvent(getJDA(), responseNumber, guild, oldVerificationLevel),
            )
        }
        if (notificationLevel != guild.getDefaultNotificationLevel()) {
            val oldNotificationLevel = guild.getDefaultNotificationLevel()
            guild.setDefaultNotificationLevel(notificationLevel)
            getJDA().handleEvent(
                GuildUpdateNotificationLevelEvent(getJDA(), responseNumber, guild, oldNotificationLevel),
            )
        }
        if (mfaLevel != guild.getRequiredMFALevel()) {
            val oldMfaLevel = guild.getRequiredMFALevel()
            guild.setRequiredMFALevel(mfaLevel)
            getJDA().handleEvent(GuildUpdateMFALevelEvent(getJDA(), responseNumber, guild, oldMfaLevel))
        }
        if (explicitContentLevel != guild.getExplicitContentLevel()) {
            val oldExplicitContentLevel = guild.getExplicitContentLevel()
            guild.setExplicitContentLevel(explicitContentLevel)
            getJDA().handleEvent(
                GuildUpdateExplicitContentLevelEvent(getJDA(), responseNumber, guild, oldExplicitContentLevel),
            )
        }
        if (afkTimeout != guild.getAfkTimeout()) {
            val oldAfkTimeout = guild.getAfkTimeout()
            guild.setAfkTimeout(afkTimeout)
            getJDA().handleEvent(GuildUpdateAfkTimeoutEvent(getJDA(), responseNumber, guild, oldAfkTimeout))
        }
        if (locale != guild.getLocale()) {
            val oldLocale = guild.getLocale()
            guild.setLocale(locale)
            getJDA().handleEvent(GuildUpdateLocaleEvent(getJDA(), responseNumber, guild, oldLocale))
        }
        if (afkChannel != guild.getAfkChannel()) {
            val oldAfkChannel = guild.getAfkChannel()
            guild.setAfkChannel(afkChannel)
            getJDA().handleEvent(GuildUpdateAfkChannelEvent(getJDA(), responseNumber, guild, oldAfkChannel))
        }
        if (systemChannel != guild.getSystemChannel()) {
            val oldSystemChannel = guild.getSystemChannel()
            guild.setSystemChannel(systemChannel)
            getJDA().handleEvent(GuildUpdateSystemChannelEvent(getJDA(), responseNumber, guild, oldSystemChannel))
        }
        if (rulesChannel != guild.getRulesChannel()) {
            val oldRulesChannel = guild.getRulesChannel()
            guild.setRulesChannel(rulesChannel)
            getJDA().handleEvent(GuildUpdateRulesChannelEvent(getJDA(), responseNumber, guild, oldRulesChannel))
        }
        if (communityUpdatesChannel != guild.getCommunityUpdatesChannel()) {
            val oldCommunityUpdatesChannel = guild.getCommunityUpdatesChannel()
            guild.setCommunityUpdatesChannel(communityUpdatesChannel)
            getJDA().handleEvent(
                GuildUpdateCommunityUpdatesChannelEvent(
                    getJDA(),
                    responseNumber,
                    guild,
                    oldCommunityUpdatesChannel,
                ),
            )
        }
        if (safetyAlertsChannel != guild.getSafetyAlertsChannel()) {
            val oldSafetyAlertsChannel = guild.getSafetyAlertsChannel()
            guild.setSafetyAlertsChannel(safetyAlertsChannel)
            getJDA().handleEvent(
                GuildUpdateSafetyAlertsChannelEvent(getJDA(), responseNumber, guild, oldSafetyAlertsChannel),
            )
        }
        if (securityIncidentActions != guild.getSecurityIncidentActions()) {
            val oldIncidentActions = guild.getSecurityIncidentActions()
            guild.setSecurityIncidentActions(securityIncidentActions)
            api.handleEvent(
                GuildUpdateSecurityIncidentActionsEvent(getJDA(), responseNumber, guild, oldIncidentActions),
            )
        }
        if (securityIncidentDetections != guild.getSecurityIncidentDetections()) {
            val oldIncidentDetections = guild.getSecurityIncidentDetections()
            guild.setSecurityIncidentDetections(securityIncidentDetections)
            api.handleEvent(
                GuildUpdateSecurityIncidentDetectionsEvent(getJDA(), responseNumber, guild, oldIncidentDetections),
            )
        }
        if (content.hasKey("nsfw_level") && nsfwLevel != guild.getNSFWLevel()) {
            val oldNSFWLevel = guild.getNSFWLevel()
            guild.setNSFWLevel(nsfwLevel)
            getJDA().handleEvent(GuildUpdateNSFWLevelEvent(getJDA(), responseNumber, guild, oldNSFWLevel))
        }
        return null
    }
}
