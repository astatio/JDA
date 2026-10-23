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

package net.dv8tion.jda.internal.entities.channel.mixin.concrete

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.ISlowmodeChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.GuildMessageChannelMixin

interface ThreadChannelMixin<T : ThreadChannelMixin<T>> :
    ThreadChannel,
    GuildMessageChannelMixin<T>,
    ISlowmodeChannelMixin<T> {
    fun setAutoArchiveDuration(autoArchiveDuration: ThreadChannel.AutoArchiveDuration): T

    fun setLocked(locked: Boolean): T

    fun setArchived(archived: Boolean): T

    fun setInvitable(invitable: Boolean): T

    fun setArchiveTimestamp(archiveTimestamp: Long): T

    fun setCreationTimestamp(creationTimestamp: Long): T

    fun setOwnerId(ownerId: Long): T

    fun setMessageCount(messageCount: Int): T

    fun setTotalMessageCount(messageCount: Int): T

    fun setMemberCount(memberCount: Int): T
}
