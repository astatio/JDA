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

import gnu.trove.set.TLongSet
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelFlag
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchiveTimestampEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAutoArchiveDurationEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateFlagsEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateInvitableEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateLockedEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateSlowmodeEvent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl
import net.dv8tion.jda.internal.utils.Helpers
import net.dv8tion.jda.internal.utils.cache.ChannelCacheViewImpl
import java.util.Objects
import java.util.stream.LongStream

class ThreadUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getLong("guild_id")
        if (api.getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        val threadId = content.getLong("id")
        var thread = getJDA().getThreadChannelById(threadId) as ThreadChannelImpl?

        // If the thread is missing then that means that the bot started up while the thread was
        // archived thus it didn't get the thread.
        // Now that it's been unarchived we've been given the entire thread and need to build it.
        // Refer to the documentation for more info:
        // https://discord.com/developers/docs/topics/threads#unarchiving-a-thread
        if (thread == null) {
            // This seems to never be true but its better to check
            if (content.getObject("thread_metadata").getBoolean("archived")) {
                return null
            }

            // Technically, when the ThreadChannel is unarchived
            // the archive_timestamp (getTimeArchiveInfoLastModified) changes as well,
            // but we don't have the original value because we didn't have the thread in memory,
            // so we can't provide an entirely accurate ChannelUpdateArchiveTimestampEvent.
            // Not sure how that'll matter.
            try {
                thread = api.getEntityBuilder().createThreadChannel(content, guildId) as ThreadChannelImpl
                api.handleEvent(ChannelUpdateArchivedEvent(api, responseNumber, thread, true, false))
            } catch (ex: IllegalArgumentException) {
                if (EntityBuilder.MISSING_CHANNEL == ex.message) {
                    val parentId = content.getUnsignedLong("parent_id", 0L)
                    EventCache.LOG.debug(
                        "Caching THREAD_UPDATE for a thread with uncached parent. Parent ID: {} JSON: {}",
                        parentId,
                        content,
                    )
                    api
                        .getEventCache()
                        .cache(EventCache.Type.CHANNEL, parentId, responseNumber, allContent, this::handle)
                    return null
                }

                throw ex
            }

            return null
        }

        val threadMetadata = content.getObject("thread_metadata")
        val name = content.getString("name")
        val flags = content.getInt("flags", 0)
        val autoArchiveDuration =
            ThreadChannel.AutoArchiveDuration.fromKey(threadMetadata.getInt("auto_archive_duration"))
        val locked = threadMetadata.getBoolean("locked")
        val archived = threadMetadata.getBoolean("archived")
        val invitable = threadMetadata.getBoolean("invitable")
        val archiveTimestamp = Helpers.toTimestamp(threadMetadata.getString("archive_timestamp"))
        val slowmode = content.getInt("rate_limit_per_user", 0)

        val oldName = thread.getName()
        val oldAutoArchiveDuration = thread.getAutoArchiveDuration()
        val oldLocked = thread.isLocked()
        val oldArchived = thread.isArchived()
        val oldInvitable = !thread.isPublic() && thread.isInvitable()
        val oldArchiveTimestamp = thread.getArchiveTimestamp()
        val oldSlowmode = thread.getSlowmode()
        val oldFlags = thread.getRawFlags()

        val wasObfuscated = thread.isObfuscated()
        val becameObfuscated = (flags and ChannelFlag.OBFUSCATED.getRaw()) != 0
        val obfuscationChanged = wasObfuscated != becameObfuscated

        if (!Objects.equals(oldName, name)) {
            thread.setName(name)
            handleUpdate(
                obfuscationChanged,
                ChannelUpdateNameEvent(getJDA(), responseNumber, thread, oldName, name),
            )
        }
        if (oldFlags != flags) {
            thread.setFlags(flags)
            api.handleEvent(
                ChannelUpdateFlagsEvent(
                    getJDA(),
                    responseNumber,
                    thread,
                    ChannelFlag.fromRaw(oldFlags),
                    ChannelFlag.fromRaw(flags),
                ),
            )
        }
        if (oldSlowmode != slowmode) {
            thread.setSlowmode(slowmode)
            handleUpdate(
                obfuscationChanged,
                ChannelUpdateSlowmodeEvent(api, responseNumber, thread, oldSlowmode, slowmode),
            )
        }
        if (oldAutoArchiveDuration != autoArchiveDuration) {
            thread.setAutoArchiveDuration(autoArchiveDuration)
            handleUpdate(
                obfuscationChanged,
                ChannelUpdateAutoArchiveDurationEvent(
                    api,
                    responseNumber,
                    thread,
                    oldAutoArchiveDuration,
                    autoArchiveDuration,
                ),
            )
        }
        if (oldLocked != locked) {
            thread.setLocked(locked)
            handleUpdate(
                obfuscationChanged,
                ChannelUpdateLockedEvent(api, responseNumber, thread, oldLocked, locked),
            )
        }
        if (oldArchived != archived) {
            thread.setArchived(archived)
            handleUpdate(
                obfuscationChanged,
                ChannelUpdateArchivedEvent(api, responseNumber, thread, oldArchived, archived),
            )
        }
        if (oldArchiveTimestamp != archiveTimestamp) {
            thread.setArchiveTimestamp(archiveTimestamp)
            handleUpdate(
                obfuscationChanged,
                ChannelUpdateArchiveTimestampEvent(
                    api,
                    responseNumber,
                    thread,
                    oldArchiveTimestamp,
                    archiveTimestamp,
                ),
            )
        }
        if (oldInvitable != invitable) {
            thread.setInvitable(invitable)
            handleUpdate(
                obfuscationChanged,
                ChannelUpdateInvitableEvent(api, responseNumber, thread, oldInvitable, invitable),
            )
        }

        if (api.isCacheFlagSet(CacheFlag.FORUM_TAGS) && !content.isNull("applied_tags")) {
            val oldTags: TLongSet = thread.getAppliedTagsSet()
            thread.setAppliedTags(
                content.getArray("applied_tags").stream<Long>(DataArray::getUnsignedLong).mapToLong { it },
            )
            val tags: TLongSet = thread.getAppliedTagsSet()

            if (oldTags != tags) {
                val oldTagList: List<Long> =
                    LongStream.of(*oldTags.toArray()).boxed().collect(Helpers.toUnmodifiableList())
                val newTagList: List<Long> =
                    LongStream.of(*tags.toArray()).boxed().collect(Helpers.toUnmodifiableList())
                handleUpdate(
                    obfuscationChanged,
                    ChannelUpdateAppliedTagsEvent(api, responseNumber, thread, oldTagList, newTagList),
                )
            }
        }

        if (thread.isArchived()) {
            val guildView: ChannelCacheViewImpl<GuildChannel> = thread.getGuild().getChannelView()
            val globalView: ChannelCacheViewImpl<Channel> = api.getChannelsView()
            guildView.remove(thread)
            globalView.remove(thread)
        }

        return null
    }

    private fun handleUpdate(
        obfuscationChanged: Boolean,
        event: GenericEvent,
    ) {
        if (!obfuscationChanged) {
            api.handleEvent(event)
        }
    }
}
