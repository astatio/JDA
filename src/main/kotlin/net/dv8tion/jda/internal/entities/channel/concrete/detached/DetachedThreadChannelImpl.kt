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

package net.dv8tion.jda.internal.entities.channel.concrete.detached

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.ThreadMember
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration
import net.dv8tion.jda.api.entities.channel.forums.ForumTag
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadMemberPaginationAction
import net.dv8tion.jda.api.utils.TimeUtil
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IInteractionPermissionMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.ThreadChannelMixin
import net.dv8tion.jda.internal.interactions.ChannelInteractionPermissions
import net.dv8tion.jda.internal.utils.Helpers
import java.time.OffsetDateTime
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DetachedThreadChannelImpl(
    id: Long,
    guild: Guild,
    private val type: ChannelType,
) : AbstractGuildChannelImpl<DetachedThreadChannelImpl>(id, guild),
    ThreadChannel,
    ThreadChannelMixin<DetachedThreadChannelImpl>,
    IInteractionPermissionMixin<DetachedThreadChannelImpl> {
    private var interactionPermissionsValue: ChannelInteractionPermissions? = null

    private var autoArchiveDuration: AutoArchiveDuration? = null
    private var locked: Boolean = false
    private var archived: Boolean = false
    private var invitable: Boolean = false
    private var archiveTimestamp: Long = 0
    private var creationTimestamp: Long = 0
    private var ownerId: Long = 0
    private var latestMessageId: Long = 0
    private var messageCount: Int = 0
    private var totalMessageCount: Int = 0
    private var memberCount: Int = 0
    private var slowmode: Int = 0

    override fun isDetached(): Boolean = true

    @Nonnull
    override fun getType(): ChannelType = type

    override fun getLatestMessageIdLong(): Long = latestMessageId

    override fun getMessageCount(): Int = messageCount

    override fun getTotalMessageCount(): Int = totalMessageCount

    override fun getMemberCount(): Int = memberCount

    override fun isLocked(): Boolean = locked

    override fun canTalk(
        @Nonnull member: Member,
    ): Boolean = throw detachedException()

    @Nonnull
    override fun getMembers(): List<Member> = throw detachedException()

    @Nonnull
    override fun getParentChannel(): IThreadContainerUnion = throw detachedException()

    @Nonnull
    override fun getAppliedTags(): List<ForumTag> = throw detachedException()

    @Nonnull
    override fun retrieveParentMessage(): RestAction<Message> = throw detachedException()

    @Nonnull
    override fun retrieveStartMessage(): RestAction<Message> = throw detachedException()

    @Nonnull
    override fun getPermissionContainer(): IPermissionContainer = throw detachedException()

    @Nonnull
    override fun getThreadMembers(): List<ThreadMember> = throw detachedException()

    @Nullable
    override fun getThreadMemberById(id: Long): ThreadMember? = throw detachedException()

    @Nonnull
    override fun retrieveThreadMemberById(id: Long): CacheRestAction<ThreadMember> = throw detachedException()

    @Nonnull
    override fun retrieveThreadMembers(): ThreadMemberPaginationAction = throw detachedException()

    override fun getOwnerIdLong(): Long = ownerId

    override fun isArchived(): Boolean = archived

    override fun isInvitable(): Boolean {
        if (type != ChannelType.GUILD_PRIVATE_THREAD) {
            throw UnsupportedOperationException("Only private threads support the concept of invitable.")
        }

        return invitable
    }

    @Nonnull
    override fun getTimeArchiveInfoLastModified(): OffsetDateTime = Helpers.toOffset(archiveTimestamp)

    @Nonnull
    override fun getAutoArchiveDuration(): AutoArchiveDuration = autoArchiveDuration!!

    @Nonnull
    override fun getTimeCreated(): OffsetDateTime =
        if (creationTimestamp == 0L) TimeUtil.getTimeCreated(getIdLong()) else Helpers.toOffset(creationTimestamp)

    override fun getSlowmode(): Int = slowmode

    @Nonnull
    override fun join(): RestAction<Void> = throw detachedException()

    @Nonnull
    override fun leave(): RestAction<Void> = throw detachedException()

    @Nonnull
    override fun addThreadMemberById(id: Long): RestAction<Void> = throw detachedException()

    @Nonnull
    override fun removeThreadMemberById(id: Long): RestAction<Void> = throw detachedException()

    @Nonnull
    override fun getManager(): ThreadChannelManager = throw detachedException()

    override fun checkCanManage(): Unit = throw detachedException()

    @Nonnull
    override val interactionPermissions: ChannelInteractionPermissions
        get() = interactionPermissionsValue!!

    override fun setLatestMessageIdLong(latestMessageId: Long): DetachedThreadChannelImpl {
        this.latestMessageId = latestMessageId
        return this
    }

    override fun setAutoArchiveDuration(autoArchiveDuration: AutoArchiveDuration): DetachedThreadChannelImpl {
        this.autoArchiveDuration = autoArchiveDuration
        return this
    }

    override fun setLocked(locked: Boolean): DetachedThreadChannelImpl {
        this.locked = locked
        return this
    }

    override fun setArchived(archived: Boolean): DetachedThreadChannelImpl {
        this.archived = archived
        return this
    }

    override fun setInvitable(invitable: Boolean): DetachedThreadChannelImpl {
        this.invitable = invitable
        return this
    }

    override fun setArchiveTimestamp(archiveTimestamp: Long): DetachedThreadChannelImpl {
        this.archiveTimestamp = archiveTimestamp
        return this
    }

    override fun setCreationTimestamp(creationTimestamp: Long): DetachedThreadChannelImpl {
        this.creationTimestamp = creationTimestamp
        return this
    }

    override fun setOwnerId(ownerId: Long): DetachedThreadChannelImpl {
        this.ownerId = ownerId
        return this
    }

    override fun setMessageCount(messageCount: Int): DetachedThreadChannelImpl {
        this.messageCount = messageCount
        return this
    }

    override fun setTotalMessageCount(messageCount: Int): DetachedThreadChannelImpl {
        totalMessageCount = maxOf(messageCount, this.messageCount) // If this is 0 we use the older count
        return this
    }

    override fun setMemberCount(memberCount: Int): DetachedThreadChannelImpl {
        this.memberCount = memberCount
        return this
    }

    override fun setSlowmode(slowmode: Int): DetachedThreadChannelImpl {
        this.slowmode = slowmode
        return this
    }

    @Nonnull
    override fun setInteractionPermissions(
        @Nonnull interactionPermissions: ChannelInteractionPermissions,
    ): DetachedThreadChannelImpl {
        this.interactionPermissionsValue = interactionPermissions
        return this
    }
}
