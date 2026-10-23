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
import net.dv8tion.jda.api.entities.ThreadMember
import net.dv8tion.jda.api.events.thread.member.ThreadMemberJoinEvent
import net.dv8tion.jda.api.events.thread.member.ThreadMemberLeaveEvent
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl

class ThreadMembersUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getLong("guild_id")
        if (api.getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        val threadId = content.getLong("id")
        val thread = getJDA().getThreadChannelById(threadId) as ThreadChannelImpl?
        if (thread == null) {
            getJDA()
                .getEventCache()
                .cache(EventCache.Type.CHANNEL, threadId, responseNumber, allContent, this::handle)
            EventCache.LOG.debug(
                "THREAD_MEMBERS_UPDATE attempted to update a thread that does not exist. JSON: {}",
                content,
            )
            return null
        }

        if (!content.isNull("added_members")) {
            val addedMembersJson = content.getArray("added_members")
            handleAddedThreadMembers(thread, addedMembersJson)
        }

        if (!content.isNull("removed_member_ids")) {
            val removedMemberIds =
                content
                    .getArray("removed_member_ids")
                    .stream(DataArray::getString)
                    .map(MiscUtil::parseSnowflake)
                    .collect(
                        java.util.stream.Collectors
                            .toList(),
                    )
            handleRemovedThreadMembers(thread, removedMemberIds)
        }

        return null
    }

    private fun handleAddedThreadMembers(
        thread: ThreadChannelImpl,
        addedMembersJson: DataArray,
    ) {
        val entityBuilder: EntityBuilder = api.getEntityBuilder()
        val view: CacheView.SimpleCacheView<ThreadMember> = thread.getThreadMemberView()

        val addedThreadMembers = ArrayList<ThreadMember>()
        for (i in 0 until addedMembersJson.length()) {
            val threadMemberJson = addedMembersJson.getObject(i)
            val threadMember = entityBuilder.createThreadMember(thread.getGuild(), thread, threadMemberJson)
            addedThreadMembers.add(threadMember)
        }

        // TODO-Threads: We assume here that we are allowed to cache these, however, we probably
        // need to check the ChunkFilter first as the
        // underlying Member object might have been created when creating the ThreadMember and it
        // might not be being updated. We don't
        // want to cache ThreadMembers if the Members they're based on aren't being cached.
        view.writeLock().use {
            for (threadMember in addedThreadMembers) {
                view.getMap().put(threadMember.getIdLong(), threadMember)
            }
        }

        // Emit the events from outside the writeLock
        for (threadMember in addedThreadMembers) {
            api.handleEvent(
                ThreadMemberJoinEvent(
                    api,
                    responseNumber,
                    thread,
                    threadMember,
                ),
            )
        }
    }

    private fun handleRemovedThreadMembers(
        thread: ThreadChannelImpl,
        removedMemberIds: List<Long>,
    ) {
        val view: CacheView.SimpleCacheView<ThreadMember> = thread.getThreadMemberView()

        // Store the removed threads into a map so that we can provide them in the events later.
        // We don't want to dispatch the events from inside the writeLock
        val removedThreadMembers: TLongObjectMap<ThreadMember> = TLongObjectHashMap()
        view.writeLock().use {
            for (threadMemberId in removedMemberIds) {
                val threadMember = view.getMap().remove(threadMemberId)
                removedThreadMembers.put(threadMemberId, threadMember)
            }
        }

        for (threadMemberId in removedMemberIds) {
            api.handleEvent(
                ThreadMemberLeaveEvent(
                    api,
                    responseNumber,
                    thread,
                    threadMemberId,
                    removedThreadMembers.remove(threadMemberId),
                ),
            )
        }
    }
}
