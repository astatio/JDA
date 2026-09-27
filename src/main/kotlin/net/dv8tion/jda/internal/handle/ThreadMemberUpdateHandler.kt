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

import net.dv8tion.jda.api.entities.ThreadMember
import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl

class ThreadMemberUpdateHandler(
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
                "THREAD_MEMBER_UPDATE attempted to update a thread that does not exist. JSON: {}",
                content,
            )
            return null
        }

        // Based on the docs it is expected that we will only ever receive THREAD_MEMBER_UPDATE when
        // Discord needs to inform us that we are a member of a ThreadChannels that we might not have in memory.
        // Currently this only happens for ThreadChannels that get unarchived.
        // Details available at:
        // https://discord.com/developers/docs/topics/threads#unarchiving-a-thread
        val userId = content.getLong("user_id")
        if (userId != getJDA().getSelfUser().getIdLong()) {
            JDAImpl.LOG.warn(
                "Received a THREAD_MEMBER_UPDATE for a user that isn't the current bot user. " +
                    "This validates assumptions that THREAD_MEMBER_UPDATE would ONLY be for the current bot user. " +
                    "Skipping this dispatch for now. This should be reported as a bug." +
                    "\nDetails: {}",
                content,
            )
            return null
        }

        val view: CacheView.SimpleCacheView<ThreadMember> = thread.getThreadMemberView()
        view.writeLock().use {
            // We might have still had the ThreadChannel in memory, so our ThreadMember might still
            // exist. Do an existence check.
            var threadMember = view.getMap().get(userId)
            if (threadMember == null) {
                threadMember =
                    api
                        .getEntityBuilder()
                        .createThreadMember(thread, thread.getGuild().getSelfMember(), content)
                view.getMap().put(threadMember.getIdLong(), threadMember)
            }
        }

        return null
    }
}
