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
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.GroupChannel
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.concrete.PrivateChannelImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedCategoryImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedForumChannelImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedGroupChannelImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedMediaChannelImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedNewsChannelImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedPrivateChannelImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedStageChannelImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedTextChannelImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedThreadChannelImpl
import net.dv8tion.jda.internal.entities.channel.concrete.detached.DetachedVoiceChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IInteractionPermissionMixin
import net.dv8tion.jda.internal.entities.detached.DetachedGuildImpl
import net.dv8tion.jda.internal.entities.detached.DetachedMemberImpl
import net.dv8tion.jda.internal.entities.detached.DetachedRoleImpl
import net.dv8tion.jda.internal.interactions.ChannelInteractionPermissions
import net.dv8tion.jda.internal.interactions.MemberInteractionPermissions
import net.dv8tion.jda.internal.utils.JDALogger
import org.slf4j.Logger

class InteractionEntityBuilder(
    api: JDAImpl,
    private val interactionChannelId: Long,
    private val interactionUserId: Long,
) : AbstractEntityBuilder(api) {
    private val entityBuilder: EntityBuilder = api.getEntityBuilder()

    fun getOrCreateGuild(guildJson: DataObject): Guild {
        val guildId = guildJson.getUnsignedLong("id")
        val guild = api.getGuildById(guildId)
        if (guild != null) {
            return guild
        }

        val featuresArray = guildJson.optArray("features")
        val locale = guildJson.getString("preferred_locale", "en-US")

        val detachedGuild = DetachedGuildImpl(api, guildId)
        detachedGuild.setLocale(DiscordLocale.from(locale))
        detachedGuild.setFeatures(
            featuresArray
                .map { array ->
                    array
                        .stream { d, i -> d.getString(i) }
                        // Prevent allocating the same feature string over and over
                        .map { it.intern() }
                        .collect(
                            java.util.stream.Collectors
                                .toSet(),
                        )
                }.orElse(emptySet()),
        )

        return detachedGuild
    }

    fun createGroupChannel(channelData: DataObject): GroupChannel {
        val id = channelData.getUnsignedLong("id")
        val channel = DetachedGroupChannelImpl(api, id)
        configureGroupChannel(channelData, channel)
        return channel
    }

    fun createGuildChannel(
        guild: Guild,
        channelData: DataObject,
    ): GuildChannel? {
        val channelType = ChannelType.fromId(channelData.getInt("type"))
        return when (channelType) {
            ChannelType.TEXT -> createTextChannel(guild, channelData)
            ChannelType.NEWS -> createNewsChannel(guild, channelData)
            ChannelType.STAGE -> createStageChannel(guild, channelData)
            ChannelType.VOICE -> createVoiceChannel(guild, channelData)
            ChannelType.CATEGORY -> createCategory(guild, channelData)
            ChannelType.FORUM -> createForumChannel(guild, channelData)
            ChannelType.MEDIA -> createMediaChannel(guild, channelData)
            else -> {
                LOG.debug("Cannot create channel for type " + channelData.getInt("type"))
                null
            }
        }
    }

    fun createCategory(
        guild: Guild,
        json: DataObject,
    ): Category? {
        val channelId = json.getUnsignedLong("id")
        if (!guild.isDetached) {
            val channel = guild.getCategoryById(channelId)
            if (channel == null || !channel.isObfuscated) {
                return channel
            }
        }

        val channel = DetachedCategoryImpl(channelId, guild)
        configureCategory(json, channel)
        configureChannelInteractionPermissions(channel, json)
        return channel
    }

    fun createTextChannel(
        guild: Guild,
        json: DataObject,
    ): TextChannel? {
        val channelId = json.getUnsignedLong("id")
        if (!guild.isDetached) {
            val channel = guild.getTextChannelById(channelId)
            if (channel == null || !channel.isObfuscated) {
                return channel
            }
        }

        val channel = DetachedTextChannelImpl(channelId, guild)
        configureTextChannel(json, channel)
        configureChannelInteractionPermissions(channel, json)
        return channel
    }

    fun createNewsChannel(
        guild: Guild,
        json: DataObject,
    ): NewsChannel? {
        val channelId = json.getUnsignedLong("id")
        if (!guild.isDetached) {
            val channel = guild.getNewsChannelById(channelId)
            if (channel == null || !channel.isObfuscated) {
                return channel
            }
        }

        val channel = DetachedNewsChannelImpl(channelId, guild)
        configureNewsChannel(json, channel)
        configureChannelInteractionPermissions(channel, json)
        return channel
    }

    fun createVoiceChannel(
        guild: Guild,
        json: DataObject,
    ): VoiceChannel? {
        val channelId = json.getUnsignedLong("id")
        if (!guild.isDetached) {
            val channel = guild.getVoiceChannelById(channelId)
            if (channel == null || !channel.isObfuscated) {
                return channel
            }
        }

        val channel = DetachedVoiceChannelImpl(channelId, guild)
        configureVoiceChannel(json, channel)
        configureChannelInteractionPermissions(channel, json)
        return channel
    }

    fun createStageChannel(
        guild: Guild,
        json: DataObject,
    ): StageChannel? {
        val channelId = json.getUnsignedLong("id")
        if (!guild.isDetached) {
            val channel = guild.getStageChannelById(channelId)
            if (channel == null || !channel.isObfuscated) {
                return channel
            }
        }

        val channel = DetachedStageChannelImpl(channelId, guild)
        configureStageChannel(json, channel)
        configureChannelInteractionPermissions(channel, json)
        return channel
    }

    fun createMediaChannel(
        guild: Guild,
        json: DataObject,
    ): MediaChannel? {
        val channelId = json.getUnsignedLong("id")
        if (!guild.isDetached) {
            val channel = guild.getMediaChannelById(channelId)
            if (channel == null || !channel.isObfuscated) {
                return channel
            }
        }

        val channel = DetachedMediaChannelImpl(channelId, guild)
        configureMediaChannel(json, channel)
        configureChannelInteractionPermissions(channel, json)
        return channel
    }

    // Mirrors the original Java control flow, which returns from within the non-detached branch.
    @Suppress("ReturnCount")
    fun createThreadChannel(
        guild: Guild,
        json: DataObject,
    ): ThreadChannel? {
        val channelId = json.getUnsignedLong("id")
        if (!guild.isDetached) {
            val threadChannel = guild.getThreadChannelById(channelId)
            if (threadChannel == null) {
                return entityBuilder.createThreadChannel(guild as GuildImpl, json, guild.idLong, false)
            } else if (!threadChannel.isObfuscated) {
                return threadChannel
            }
        }

        val type = ChannelType.fromId(json.getInt("type"))
        val channel = DetachedThreadChannelImpl(channelId, guild, type)
        configureThreadChannel(json, channel)
        configureChannelInteractionPermissions(channel, json)
        return channel
    }

    fun createForumChannel(
        guild: Guild,
        json: DataObject,
    ): ForumChannel? {
        val channelId = json.getUnsignedLong("id")
        if (!guild.isDetached) {
            val channel = guild.getForumChannelById(channelId)
            if (channel == null || !channel.isObfuscated) {
                return channel
            }
        }

        val channel = DetachedForumChannelImpl(channelId, guild)
        configureForumChannel(json, channel)
        configureChannelInteractionPermissions(channel, json)
        return channel
    }

    private fun configureChannelInteractionPermissions(
        channel: IInteractionPermissionMixin<*>,
        json: DataObject,
    ) {
        channel.interactionPermissions = ChannelInteractionPermissions(interactionUserId, json.getLong("permissions"))
    }

    fun createMember(
        guild: Guild,
        memberJson: DataObject,
    ): Member {
        if (!guild.isDetached) {
            return entityBuilder.createMember(guild as GuildImpl, memberJson)
        }

        val user = entityBuilder.createUser(memberJson.getObject("user"))
        val member = DetachedMemberImpl(guild as DetachedGuildImpl, user)
        configureMember(memberJson, member)

        // Absent outside interactions and in message mentions
        if (memberJson.hasKey("permissions")) {
            member.setInteractionPermissions(
                MemberInteractionPermissions(interactionChannelId, memberJson.getLong("permissions")),
            )
        }

        return member
    }

    fun createRole(
        guild: Guild,
        roleJson: DataObject,
    ): Role? {
        if (!guild.isDetached) {
            return guild.getRoleById(roleJson.getUnsignedLong("id"))
        }

        val id = roleJson.getUnsignedLong("id")
        val role = DetachedRoleImpl(id, guild as DetachedGuildImpl)
        configureRole(roleJson, role, id)
        return role
    }

    fun createPrivateChannel(
        json: DataObject,
        interactionUser: User,
    ): PrivateChannel {
        val channelId = json.getUnsignedLong("id")
        val recipientObj =
            json
                .optArray("recipients")
                .filter { d -> !d.isEmpty }
                .map { d -> d.getObject(0) }
                .orElse(null)

        val channel: PrivateChannel
        if (recipientObj != null) {
            // Let's try not to DM ourselves
            if (api.selfUser.idLong == recipientObj.getUnsignedLong("id")) {
                channel = PrivateChannelImpl(getJDA(), channelId, interactionUser)
            } else {
                // This still needs to be detached,
                // as there is no open channel between the bot and the friend,
                channel = DetachedPrivateChannelImpl(getJDA(), channelId, entityBuilder.createUser(recipientObj))
            }
        } else {
            LOG.warn(
                "Private channel has no recipient and will fallback to a detached PrivateChannel with no user," +
                    " please report to the devs, channel JSON: {}",
                json.toPrettyString(),
            )
            channel = DetachedPrivateChannelImpl(getJDA(), channelId, null)
        }
        configurePrivateChannel(json, channel)
        return channel
    }

    companion object {
        private val LOG: Logger = JDALogger.getLog(InteractionEntityBuilder::class.java)
    }
}
