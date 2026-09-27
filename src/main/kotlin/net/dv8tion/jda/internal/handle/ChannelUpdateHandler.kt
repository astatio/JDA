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

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.Region
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.IPermissionHolder
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelFlag
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.IPostContainer
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.forums.ForumTag
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.channel.forum.ForumTagAddEvent
import net.dv8tion.jda.api.events.channel.forum.ForumTagRemoveEvent
import net.dv8tion.jda.api.events.channel.forum.update.ForumTagUpdateEmojiEvent
import net.dv8tion.jda.api.events.channel.forum.update.ForumTagUpdateModeratedEvent
import net.dv8tion.jda.api.events.channel.forum.update.ForumTagUpdateNameEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateBitrateEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateDefaultLayoutEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateDefaultReactionEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateDefaultSortOrderEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateDefaultThreadSlowmodeEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateFlagsEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNSFWEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdatePositionEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateRegionEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateSlowmodeEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateTopicEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateTypeEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateUserLimitEvent
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent
import net.dv8tion.jda.api.events.thread.ThreadHiddenEvent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.ForumTagImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.PermissionOverrideImpl
import net.dv8tion.jda.internal.entities.channel.concrete.ForumChannelImpl
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IAgeRestrictedChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.ICategorizableChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPermissionContainerMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPositionableChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPostContainerMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.ISlowmodeChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IThreadContainerMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.ITopicChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.AudioChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.MessageChannelMixin
import net.dv8tion.jda.internal.requests.WebSocketClient
import net.dv8tion.jda.internal.utils.cache.ChannelCacheViewImpl
import net.dv8tion.jda.internal.utils.cache.SortedSnowflakeCacheViewImpl
import java.util.EnumSet

class ChannelUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount", "NestedBlockDepth") // faithfully ported branch-and-guard control flow from Java
    protected override fun handleInternally(content: DataObject): Long? {
        val type = ChannelType.fromId(content.getInt("type"))
        if (type == ChannelType.GROUP) {
            WebSocketClient.LOG.warn("Ignoring CHANNEL_UPDATE for a group which we don't support")
            return null
        }
        if (!content.isNull("guild_id")) {
            val guildId = content.getUnsignedLong("guild_id")
            if (getJDA().getGuildSetupController().isLocked(guildId)) {
                return guildId
            }
        }

        val channelId = content.getUnsignedLong("id")

        // We assume the CHANNEL_UPDATE was for a GuildChannel because PrivateChannels don't emit
        // CHANNEL_UPDATE for 1:1 DMs, only Groups.
        var channel = getJDA().getGuildChannelById(channelId) as AbstractGuildChannelImpl<*>?
        if (channel == null) {
            getJDA()
                .getEventCache()
                .cache(EventCache.Type.CHANNEL, channelId, responseNumber, allContent, this::handle)
            EventCache.LOG.debug(
                "CHANNEL_UPDATE attempted to update a channel that does not exist. JSON: {}",
                content,
            )
            return null
        }

        // Detect if we changed the channel type at all and reconstruct the channel entity if needed
        channel = handleChannelTypeChange(channel, content, type)
        if (channel == null) {
            return null
        }

        val wasObfuscated = channel.isObfuscated()
        val becameObfuscated = ChannelFlag.fromRaw(content.getInt("flags", 0)).contains(ChannelFlag.OBFUSCATED)
        val obfuscationChange = wasObfuscated != becameObfuscated
        val dispatch = ObfuscationAwareUpdater(obfuscationChange)

        // Handle shared properties

        val oldName = channel.getName()
        val name = content.getString("name", oldName)
        if (oldName != name) {
            channel.setName(name)
            dispatch.handleUpdate(ChannelUpdateNameEvent(getJDA(), responseNumber, channel, oldName, name))
        }

        dispatch.handleFlagsUpdate(channel, content)

        if (channel is ITopicChannelMixin<*>) {
            dispatch.handleTopic(channel, content.getString("topic", null))
        }

        if (channel is ISlowmodeChannelMixin<*>) {
            dispatch.handleSlowmode(channel, content.getInt("rate_limit_per_user", 0))
        }

        if (channel is IAgeRestrictedChannelMixin<*>) {
            dispatch.handleNsfw(channel, content.getBoolean("nsfw"))
        }

        if (channel is ICategorizableChannelMixin<*>) {
            dispatch.handleParentCategory(channel, content.getUnsignedLong("parent_id", 0))
        }

        if (channel is IPositionableChannelMixin<*>) {
            dispatch.handlePosition(channel, content.getInt("position", 0))
        }

        if (channel is IThreadContainerMixin<*>) {
            dispatch.handleThreadContainer(channel, content)
        }

        if (channel is AudioChannelMixin<*>) {
            dispatch.handleAudioChannel(channel, content)
        }

        if (channel is IPostContainerMixin<*>) {
            dispatch.handlePostContainer(channel, content)
        }

        // Handle concrete type specific properties

        when (type) {
            ChannelType.FORUM -> {
                val forumChannel = channel as ForumChannelImpl

                val layout = content.getInt("default_forum_layout", forumChannel.getRawLayout())
                val oldLayout = forumChannel.getRawLayout()

                if (oldLayout != layout) {
                    forumChannel.setDefaultLayout(layout)
                    dispatch.handleUpdate(
                        ChannelUpdateDefaultLayoutEvent(
                            getJDA(),
                            responseNumber,
                            forumChannel,
                            ForumChannel.Layout.fromKey(oldLayout),
                            ForumChannel.Layout.fromKey(layout),
                        ),
                    )
                }
            }
            ChannelType.VOICE, ChannelType.TEXT, ChannelType.NEWS, ChannelType.STAGE, ChannelType.CATEGORY -> {}
            else ->
                WebSocketClient.LOG.debug(
                    "CHANNEL_UPDATE provided an unrecognized channel type JSON: {}",
                    content,
                )
        }

        val permOverwrites = content.optArray("permission_overwrites").orElseGet(DataArray::empty)
        dispatch.applyPermissions(channel as IPermissionContainerMixin<*>, permOverwrites)

        val hasAccessToChannel =
            channel.getGuild().getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL)
        if (channel is IThreadContainer && !hasAccessToChannel) {
            dispatch.handleHideChildThreads(channel)
        }

        return null
    }

    @Suppress("ReturnCount") // faithfully ported branch-and-return control flow from Java
    private fun handleChannelTypeChange(
        channel: AbstractGuildChannelImpl<*>,
        content: DataObject,
        newChannelType: ChannelType,
    ): AbstractGuildChannelImpl<*>? {
        if (channel.getType() == newChannelType) {
            return channel
        }

        val builder: EntityBuilder = getJDA().getEntityBuilder()
        val guild = channel.getGuild() as GuildImpl

        val oldType = channel.getType()

        val expectedTypes =
            EnumSet.complementOf(
                EnumSet.of(
                    ChannelType.PRIVATE,
                    ChannelType.GROUP,
                    ChannelType.GUILD_NEWS_THREAD,
                    ChannelType.GUILD_PRIVATE_THREAD,
                    ChannelType.GUILD_PUBLIC_THREAD,
                    ChannelType.UNKNOWN,
                ),
            )

        if (!expectedTypes.contains(oldType) || !expectedTypes.contains(newChannelType)) {
            WebSocketClient.LOG.warn(
                "Unexpected channel type change {}->{}, discarding from cache.",
                channel.getType().getId(),
                content.getInt("type"),
            )
            guild.uncacheChannel(channel, false)
            return null
        }

        guild.uncacheChannel(channel, true)
        val newChannel: Channel = builder.createGuildChannel(guild, content)

        if (channel is IThreadContainer) {
            if (newChannel is IThreadContainer) {
                // Refresh thread parents to avoid keeping strong references to parent channel
                guild.getThreadChannelCache().forEachUnordered(ThreadChannel::getParentChannel)
            } else {
                // Change introduced dangling thread channels (with no parent)
                WebSocketClient.LOG.error(
                    "ThreadContainer channel transitioned into type that is not ThreadContainer? {} -> {}",
                    channel.getType(),
                    newChannel.getType(),
                )
            }
        }

        if (newChannel is MessageChannelMixin<*> && channel is MessageChannel) {
            val latestMessageIdLong = channel.latestMessageIdLong
            (channel as MessageChannelMixin<*>).setLatestMessageIdLong(latestMessageIdLong)
        }

        getJDA().handleEvent(
            ChannelUpdateTypeEvent(getJDA(), responseNumber, newChannel, oldType, newChannelType),
        )

        return channel
    }

    private inner class ObfuscationAwareUpdater(
        private val suppressEvents: Boolean,
    ) {
        fun handleUpdate(event: GenericEvent) {
            if (!suppressEvents) {
                api.handleEvent(event)
            }
        }

        fun applyPermissions(
            channel: IPermissionContainerMixin<*>,
            permOverwrites: DataArray,
        ) {
            val currentOverrides: TLongObjectMap<PermissionOverride> =
                TLongObjectHashMap(channel.permissionOverrideMap)
            val changed: MutableList<IPermissionHolder> = ArrayList(currentOverrides.size())
            val guild: Guild = channel.getGuild()
            for (i in 0 until permOverwrites.length()) {
                val overrideJson = permOverwrites.getObject(i)
                val id = overrideJson.getUnsignedLong("id", 0)
                if (handlePermissionOverride(currentOverrides.remove(id), overrideJson, id, channel)) {
                    addPermissionHolder(changed, guild, id)
                }
            }

            val values = currentOverrides.valueCollection().toMutableList()
            for (override in values) {
                channel.permissionOverrideMap.remove(override.idLong)
                addPermissionHolder(changed, guild, override.idLong)
                handleUpdate(PermissionOverrideDeleteEvent(api, responseNumber, channel, override))
            }
        }

        private fun addPermissionHolder(
            changed: MutableList<IPermissionHolder>,
            guild: Guild,
            id: Long,
        ) {
            var holder: IPermissionHolder? = guild.getRoleById(id)
            if (holder == null) {
                holder = guild.getMemberById(id)
            }
            if (holder != null) { // Members might not be cached
                changed.add(holder)
            }
        }

        // True => override status changed (created/deleted/updated)
        // False => nothing changed, ignore
        @Suppress("ReturnCount") // faithfully ported branch-and-return control flow from Java
        private fun handlePermissionOverride(
            currentOverrideArg: PermissionOverride?,
            override: DataObject,
            overrideId: Long,
            channel: IPermissionContainerMixin<*>,
        ): Boolean {
            var currentOverride = currentOverrideArg
            val allow = override.getLong("allow")
            val deny = override.getLong("deny")
            val type = override.getInt("type")
            val isRole = type == 0
            if (!isRole) {
                if (type != 1) {
                    EntityBuilder.LOG.debug("Ignoring unknown invite of type '{}'. JSON: {}", type, override)
                    return false
                } else if (!api.isCacheFlagSet(CacheFlag.MEMBER_OVERRIDES) &&
                    overrideId != api.getSelfUser().getIdLong()
                ) {
                    return false
                }
            }

            if (currentOverride != null) { // Permissions were updated?
                val oldAllow = currentOverride.getAllowedRaw()
                val oldDeny = currentOverride.getDeniedRaw()
                val impl = currentOverride as PermissionOverrideImpl
                if (oldAllow == allow && oldDeny == deny) {
                    return false
                }

                if (overrideId == channel.getGuild().getIdLong() && (allow or deny) == 0L) {
                    // We delete empty overrides for the @everyone role because that's what the client
                    // also does, otherwise our sync checks don't work!
                    channel.permissionOverrideMap.remove(overrideId)
                    handleUpdate(PermissionOverrideDeleteEvent(api, responseNumber, channel, currentOverride))
                    return true
                }

                impl.setAllow(allow)
                impl.setDeny(deny)
                handleUpdate(
                    PermissionOverrideUpdateEvent(
                        api,
                        responseNumber,
                        channel,
                        currentOverride,
                        oldAllow,
                        oldDeny,
                    ),
                )
            } else { // New override?
                // Empty @everyone overrides should be treated as not existing at all
                if (overrideId == channel.getGuild().getIdLong() && (allow or deny) == 0L) {
                    return false
                }
                val impl = PermissionOverrideImpl(channel, overrideId, isRole)
                currentOverride = impl
                impl.setAllow(allow)
                impl.setDeny(deny)
                channel.permissionOverrideMap.put(overrideId, currentOverride)
                handleUpdate(PermissionOverrideCreateEvent(api, responseNumber, channel, currentOverride))
            }

            return true
        }

        internal fun handleHideChildThreads(channel: IThreadContainer) {
            val threads: List<ThreadChannel> = channel.getThreadChannels()
            if (threads.isEmpty()) {
                return
            }

            for (thread in threads) {
                val guild = channel.getGuild() as GuildImpl
                val guildThreadView: ChannelCacheViewImpl<GuildChannel> = guild.getChannelView()
                val threadView: ChannelCacheViewImpl<Channel> = getJDA().getChannelsView()
                guildThreadView.writeLock().use { _ ->
                    threadView.writeLock().use { _ ->
                        threadView.remove<Channel>(thread.getType(), thread.getIdLong())
                        guildThreadView.remove(thread)
                    }
                }
            }

            // Fire these events outside the write locks
            for (thread in threads) {
                handleUpdate(ThreadHiddenEvent(api, responseNumber, thread))
            }
        }

        @Suppress("ReturnCount", "NestedBlockDepth") // faithfully ported iterative update loop from Java
        private fun handleTagsUpdate(channel: IPostContainerMixin<*>, tags: DataArray) {
            if (!api.isCacheFlagSet(CacheFlag.FORUM_TAGS)) {
                return
            }
            val builder: EntityBuilder = api.getEntityBuilder()

            val view: SortedSnowflakeCacheViewImpl<ForumTag> = channel.getAvailableTagCache()

            view.writeLock().use { _ ->
                val cache: TLongObjectMap<ForumTag> = view.getMap()
                val removedTags: TLongSet = TLongHashSet(cache.keySet())

                for (i in 0 until tags.length()) {
                    val tagJson = tags.getObject(i)
                    val id = tagJson.getUnsignedLong("id")
                    if (removedTags.remove(id)) {
                        val impl = cache.get(id) as ForumTagImpl?
                        if (impl == null) {
                            continue
                        }

                        val name = tagJson.getString("name")
                        val moderated = tagJson.getBoolean("moderated")

                        val oldName = impl.getName()
                        val oldEmoji = impl.getEmoji()

                        impl.setEmoji(tagJson)

                        impl.setPosition(i)
                        if (oldEmoji != impl.getEmoji()) {
                            handleUpdate(
                                ForumTagUpdateEmojiEvent(api, responseNumber, channel, impl, oldEmoji),
                            )
                        }
                        if (name != oldName) {
                            impl.setName(name)
                            handleUpdate(ForumTagUpdateNameEvent(api, responseNumber, channel, impl, oldName))
                        }
                        if (moderated != impl.isModerated()) {
                            impl.setModerated(moderated)
                            handleUpdate(
                                ForumTagUpdateModeratedEvent(api, responseNumber, channel, impl, moderated),
                            )
                        }
                    } else {
                        val tag = builder.createForumTag(channel, tagJson, i)
                        cache.put(id, tag)
                        handleUpdate(ForumTagAddEvent(api, responseNumber, channel, tag))
                    }
                }

                removedTags.forEach { id ->
                    val tag = cache.remove(id)
                    if (tag != null) {
                        handleUpdate(ForumTagRemoveEvent(api, responseNumber, channel, tag))
                    }
                    true
                }
            }
        }

        internal fun handleTopic(
            channel: ITopicChannelMixin<*>,
            topic: String?,
        ) {
            val oldTopic = channel.getTopic()
            if (oldTopic == topic) {
                return
            }

            channel.setTopic(topic)
            handleUpdate(ChannelUpdateTopicEvent(api, responseNumber, channel, oldTopic, topic))
        }

        internal fun handleSlowmode(
            channel: ISlowmodeChannelMixin<*>,
            slowmode: Int,
        ) {
            val oldSlowmode = channel.getSlowmode()
            if (oldSlowmode == slowmode) {
                return
            }

            channel.setSlowmode(slowmode)
            handleUpdate(ChannelUpdateSlowmodeEvent(api, responseNumber, channel, oldSlowmode, slowmode))
        }

        internal fun handleNsfw(
            channel: IAgeRestrictedChannelMixin<*>,
            nsfw: Boolean,
        ) {
            val oldNsfw = channel.isNSFW()
            if (oldNsfw == nsfw) {
                return
            }

            channel.setNSFW(nsfw)
            handleUpdate(ChannelUpdateNSFWEvent(api, responseNumber, channel, oldNsfw, nsfw))
        }

        internal fun handleParentCategory(
            channel: ICategorizableChannelMixin<*>,
            parentId: Long,
        ) {
            val oldParentId = channel.getParentCategoryIdLong()
            if (oldParentId == parentId) {
                return
            }

            val oldParent: Category? = channel.getParentCategory()
            channel.setParentCategory(parentId)
            val newParent: Category? = channel.getParentCategory()

            handleUpdate(ChannelUpdateParentEvent(api, responseNumber, channel, oldParent, newParent))
        }

        internal fun handlePosition(
            channel: IPositionableChannelMixin<*>,
            position: Int,
        ) {
            val oldPosition = channel.getPositionRaw()
            if (oldPosition == position) {
                return
            }

            channel.setPosition(position)
            handleUpdate(ChannelUpdatePositionEvent(api, responseNumber, channel, oldPosition, position))
        }

        internal fun handleThreadContainer(
            channel: IThreadContainerMixin<*>,
            content: DataObject,
        ) {
            val oldDefaultThreadSlowmode = channel.getDefaultThreadSlowmode()
            val defaultThreadSlowmode = content.getInt("default_thread_rate_limit_per_user", 0)
            if (oldDefaultThreadSlowmode != defaultThreadSlowmode) {
                channel.setDefaultThreadSlowmode(defaultThreadSlowmode)
                handleUpdate(
                    ChannelUpdateDefaultThreadSlowmodeEvent(
                        api,
                        responseNumber,
                        channel,
                        oldDefaultThreadSlowmode,
                        defaultThreadSlowmode,
                    ),
                )
            }
        }

        internal fun handleAudioChannel(
            channel: AudioChannelMixin<*>,
            content: DataObject,
        ) {
            val oldBitrate = channel.getBitrate()
            val bitrate = content.getInt("bitrate")

            if (oldBitrate != bitrate) {
                channel.setBitrate(bitrate)
                handleUpdate(ChannelUpdateBitrateEvent(api, responseNumber, channel, oldBitrate, bitrate))
            }

            val userLimit = content.getInt("user_limit")
            val oldLimit = channel.getUserLimit()

            if (oldLimit != userLimit) {
                channel.setUserLimit(userLimit)
                handleUpdate(
                    ChannelUpdateUserLimitEvent(getJDA(), responseNumber, channel, oldLimit, userLimit),
                )
            }

            val oldRegion = channel.getRegionRaw()
            val regionRaw = content.getString("rtc_region", null)

            if (oldRegion != regionRaw) {
                channel.setRegion(regionRaw)
                handleUpdate(
                    ChannelUpdateRegionEvent(
                        api,
                        responseNumber,
                        channel,
                        Region.fromKey(oldRegion),
                        Region.fromKey(regionRaw),
                    ),
                )
            }
        }

        internal fun handlePostContainer(
            channel: IPostContainerMixin<*>,
            content: DataObject,
        ) {
            content.optArray("available_tags").ifPresent { array -> handleTagsUpdate(channel, array) }

            val defaultReaction: EmojiUnion? =
                content
                    .optObject("default_reaction_emoji")
                    .map { json: DataObject -> EntityBuilder.createEmoji(json, "emoji_name", "emoji_id") }
                    .orElse(null)
            val oldDefaultReaction = channel.getDefaultReaction()

            if (oldDefaultReaction != defaultReaction) {
                channel.setDefaultReaction(content.optObject("default_reaction_emoji").orElse(null))
                handleUpdate(
                    ChannelUpdateDefaultReactionEvent(
                        getJDA(),
                        responseNumber,
                        channel,
                        oldDefaultReaction,
                        defaultReaction,
                    ),
                )
            }

            val sortOrder = content.getInt("default_sort_order", channel.rawSortOrder)
            val oldSortOrder = channel.rawSortOrder

            if (oldSortOrder != sortOrder) {
                channel.setDefaultSortOrder(sortOrder)
                handleUpdate(
                    ChannelUpdateDefaultSortOrderEvent(
                        getJDA(),
                        responseNumber,
                        channel,
                        IPostContainer.SortOrder.fromKey(oldSortOrder),
                    ),
                )
            }
        }

        internal fun handleFlagsUpdate(
            channel: AbstractGuildChannelImpl<*>,
            content: DataObject,
        ) {
            val newFlags = content.getInt("flags", 0)
            val oldFlags = channel.getFlagsRaw().toInt()

            if (oldFlags != newFlags) {
                channel.setFlags(newFlags)
                // Intentionally dispatch flag updates even when channel is obfuscated
                api.handleEvent(
                    ChannelUpdateFlagsEvent(
                        getJDA(),
                        responseNumber,
                        channel,
                        ChannelFlag.fromRaw(oldFlags),
                        ChannelFlag.fromRaw(newFlags),
                    ),
                )
            }
        }
    }
}
