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

import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.RoleColors
import net.dv8tion.jda.api.entities.RoleIcon
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.forums.ForumTag
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPostContainerMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.CategoryMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.ForumChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.GroupChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.MediaChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.NewsChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.PrivateChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.StageChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.TextChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.ThreadChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.VoiceChannelMixin
import net.dv8tion.jda.internal.entities.mixin.MemberMixin
import net.dv8tion.jda.internal.entities.mixin.RoleMixin
import net.dv8tion.jda.internal.utils.Helpers
import net.dv8tion.jda.internal.utils.cache.SortedSnowflakeCacheViewImpl

abstract class AbstractEntityBuilder protected constructor(
    @JvmField protected val api: JDAImpl,
) {
    open fun getJDA(): JDAImpl = api

    protected open fun configureCategory(
        json: DataObject,
        channel: CategoryMixin<*>,
    ) {
        channel
            .setName(json.getString("name"))
            .setPosition(json.getInt("position"))
            .setFlags(json.getInt("flags", 0))
    }

    protected open fun configureTextChannel(
        json: DataObject,
        channel: TextChannelMixin<*>,
    ) {
        channel
            .setParentCategory(json.getLong("parent_id", 0))
            .setLatestMessageIdLong(json.getLong("last_message_id", 0))
            .setName(json.getString("name"))
            .setFlags(json.getInt("flags", 0))
            .setTopic(json.getString("topic", null))
            .setPosition(json.getInt("position"))
            .setNSFW(json.getBoolean("nsfw"))
            .setDefaultThreadSlowmode(json.getInt("default_thread_rate_limit_per_user", 0))
            .setSlowmode(json.getInt("rate_limit_per_user", 0))
    }

    protected open fun configureNewsChannel(
        json: DataObject,
        channel: NewsChannelMixin<*>,
    ) {
        channel
            .setParentCategory(json.getLong("parent_id", 0))
            .setLatestMessageIdLong(json.getLong("last_message_id", 0))
            .setName(json.getString("name"))
            .setFlags(json.getInt("flags", 0))
            .setTopic(json.getString("topic", null))
            .setPosition(json.getInt("position"))
            .setNSFW(json.getBoolean("nsfw"))
    }

    protected open fun configureVoiceChannel(
        json: DataObject,
        channel: VoiceChannelMixin<*>,
    ) {
        channel
            .setParentCategory(json.getLong("parent_id", 0))
            .setLatestMessageIdLong(json.getLong("last_message_id", 0))
            .setName(json.getString("name"))
            .setFlags(json.getInt("flags", 0))
            .setStatus(json.getString("status", ""))
            .setPosition(json.getInt("position"))
            .setUserLimit(json.getInt("user_limit", 0))
            .setNSFW(json.getBoolean("nsfw"))
            .setBitrate(json.getInt("bitrate"))
            .setRegion(json.getString("rtc_region", null))
            //
            // .setDefaultThreadSlowmode(json.getInt("default_thread_rate_limit_per_user", 0))
            .setSlowmode(json.getInt("rate_limit_per_user", 0))
    }

    protected open fun configureStageChannel(
        json: DataObject,
        channel: StageChannelMixin<*>,
    ) {
        channel
            .setParentCategory(json.getLong("parent_id", 0))
            .setLatestMessageIdLong(json.getLong("last_message_id", 0))
            .setName(json.getString("name"))
            .setFlags(json.getInt("flags", 0))
            .setPosition(json.getInt("position"))
            .setBitrate(json.getInt("bitrate"))
            .setUserLimit(json.getInt("user_limit", 0))
            .setNSFW(json.getBoolean("nsfw"))
            .setRegion(json.getString("rtc_region", null))
            //
            // .setDefaultThreadSlowmode(json.getInt("default_thread_rate_limit_per_user", 0))
            .setSlowmode(json.getInt("rate_limit_per_user", 0))
    }

    protected open fun configureThreadChannel(
        json: DataObject,
        channel: ThreadChannelMixin<*>,
    ) {
        val threadMetadata = json.getObject("thread_metadata")

        channel
            .setName(json.getString("name"))
            .setFlags(json.getInt("flags", 0))
            .setOwnerId(json.getLong("owner_id"))
            .setMemberCount(json.getInt("member_count"))
            .setMessageCount(json.getInt("message_count"))
            .setTotalMessageCount(json.getInt("total_message_count", 0))
            .setLatestMessageIdLong(json.getLong("last_message_id", 0))
            .setSlowmode(json.getInt("rate_limit_per_user", 0))
            .setLocked(threadMetadata.getBoolean("locked"))
            .setArchived(threadMetadata.getBoolean("archived"))
            .setInvitable(threadMetadata.getBoolean("invitable"))
            .setArchiveTimestamp(Helpers.toTimestamp(threadMetadata.getString("archive_timestamp")))
            .setCreationTimestamp(
                if (threadMetadata.isNull("create_timestamp")) {
                    0
                } else {
                    Helpers.toTimestamp(threadMetadata.getString("create_timestamp"))
                },
            ).setAutoArchiveDuration(
                ThreadChannel.AutoArchiveDuration.fromKey(threadMetadata.getInt("auto_archive_duration")),
            )
    }

    protected open fun configureForumChannel(
        json: DataObject,
        channel: ForumChannelMixin<*>,
    ) {
        if (api.isCacheFlagSet(CacheFlag.FORUM_TAGS)) {
            json.optArray("available_tags").ifPresent { tags ->
                for (i in 0 until tags.length()) {
                    createForumTag(channel, tags.getObject(i), i)
                }
            }
        }

        channel
            .setParentCategory(json.getLong("parent_id", 0))
            .setFlags(json.getInt("flags", 0))
            .setDefaultReaction(json.optObject("default_reaction_emoji").orElse(null))
            .setDefaultSortOrder(json.getInt("default_sort_order", -1))
            .setDefaultLayout(json.getInt("default_forum_layout", -1))
            .setName(json.getString("name"))
            .setTopic(json.getString("topic", null))
            .setPosition(json.getInt("position"))
            .setDefaultThreadSlowmode(json.getInt("default_thread_rate_limit_per_user", 0))
            .setSlowmode(json.getInt("rate_limit_per_user", 0))
            .setNSFW(json.getBoolean("nsfw"))
    }

    protected open fun configureMediaChannel(
        json: DataObject,
        channel: MediaChannelMixin<*>,
    ) {
        if (api.isCacheFlagSet(CacheFlag.FORUM_TAGS)) {
            json.optArray("available_tags").ifPresent { tags ->
                for (i in 0 until tags.length()) {
                    createForumTag(channel, tags.getObject(i), i)
                }
            }
        }

        channel
            .setParentCategory(json.getLong("parent_id", 0))
            .setFlags(json.getInt("flags", 0))
            .setDefaultReaction(json.optObject("default_reaction_emoji").orElse(null))
            .setDefaultSortOrder(json.getInt("default_sort_order", -1))
            .setName(json.getString("name"))
            .setTopic(json.getString("topic", null))
            .setPosition(json.getInt("position"))
            .setDefaultThreadSlowmode(json.getInt("default_thread_rate_limit_per_user", 0))
            .setSlowmode(json.getInt("rate_limit_per_user", 0))
            .setNSFW(json.getBoolean("nsfw"))
    }

    open fun createForumTag(
        channel: IPostContainerMixin<*>,
        json: DataObject,
        index: Int,
    ): ForumTagImpl {
        val id = json.getUnsignedLong("id")
        val cache: SortedSnowflakeCacheViewImpl<ForumTag> = channel.availableTagCache
        var tag = cache.get(id) as ForumTagImpl?

        if (tag == null) {
            cache.writeLock().use {
                tag = ForumTagImpl(id)
                cache.getMap().put(id, tag)
            }
        }

        tag!!
            .setName(json.getString("name"))
            .setModerated(json.getBoolean("moderated"))
            .setEmoji(json)
            .setPosition(index)
        return tag
    }

    protected open fun configurePrivateChannel(
        json: DataObject,
        channel: PrivateChannelMixin<*>,
    ) {
        channel.setLatestMessageIdLong(json.getLong("last_message_id", 0))
    }

    protected open fun configureGroupChannel(
        json: DataObject,
        channel: GroupChannelMixin<*>,
    ) {
        channel
            .setLatestMessageIdLong(json.getLong("last_message_id", 0L))
            .setName(json.getString("name", ""))
            .setOwnerId(json.getLong("owner_id"))
            .setIcon(json.getString("icon", null))
    }

    protected open fun configureMember(
        memberJson: DataObject,
        member: MemberMixin<*>,
    ) {
        member.setNickname(memberJson.getString("nick", null))
        member.setAvatarId(memberJson.getString("avatar", null))
        member.setBannerId(memberJson.getString("banner", null))
        if (!memberJson.isNull("flags")) {
            member.setFlags(memberJson.getInt("flags"))
        }

        val boostTimestamp = if (memberJson.isNull("premium_since")) 0 else Helpers.toTimestamp(memberJson.getString("premium_since"))
        member.setBoostDate(boostTimestamp)

        val timeOutTimestamp =
            if (memberJson.isNull("communication_disabled_until")) {
                0
            } else {
                Helpers.toTimestamp(memberJson.getString("communication_disabled_until"))
            }
        member.setTimeOutEnd(timeOutTimestamp)

        if (!memberJson.isNull("pending")) {
            member.setPending(memberJson.getBoolean("pending"))
        }

        if (!memberJson.isNull("joined_at") && !member.hasTimeJoined()) {
            member.setJoinDate(Helpers.toTimestamp(memberJson.getString("joined_at")))
        }
    }

    protected open fun configureRole(
        roleJson: DataObject,
        role: RoleMixin<*>,
        id: Long,
    ) {
        val colors = createRoleColors(roleJson.getObject("colors"))

        role
            .setName(roleJson.getString("name"))
            .setRawPosition(roleJson.getInt("position"))
            .setRawPermissions(roleJson.getLong("permissions"))
            .setManaged(roleJson.getBoolean("managed"))
            .setHoisted(roleJson.getBoolean("hoist"))
            .setPrimaryColor(colors.primaryRaw)
            .setSecondaryColor(colors.secondaryRaw)
            .setTertiaryColor(colors.tertiaryRaw)
            .setMentionable(roleJson.getBoolean("mentionable"))
            .setTags(roleJson.optObject("tags").orElseGet { DataObject.empty() })

        val iconId = roleJson.getString("icon", null)
        val emoji = roleJson.getString("unicode_emoji", null)
        if (iconId == null && emoji == null) {
            role.setIcon(null)
        } else {
            role.setIcon(RoleIcon(iconId, emoji, id))
        }
    }

    companion object {
        @JvmStatic
        fun createRoleColors(colorsJson: DataObject): RoleColors {
            val primaryColor = colorsJson.getInt("primary_color")
            val secondaryColor = colorsJson.getInt("secondary_color", Role.DEFAULT_COLOR_RAW)
            val tertiaryColor = colorsJson.getInt("tertiary_color", Role.DEFAULT_COLOR_RAW)

            return RoleColors(
                if (primaryColor == 0) Role.DEFAULT_COLOR_RAW else primaryColor,
                secondaryColor,
                tertiaryColor,
            )
        }
    }
}
