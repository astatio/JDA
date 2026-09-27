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

package net.dv8tion.jda.internal.entities.channel.concrete

import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.ThreadMember
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration
import net.dv8tion.jda.api.entities.channel.forums.ForumTag
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadMemberPaginationAction
import net.dv8tion.jda.api.utils.TimeUtil
import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.ThreadChannelMixin
import net.dv8tion.jda.internal.managers.channel.concrete.ThreadChannelManagerImpl
import net.dv8tion.jda.internal.requests.DeferredRestAction
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.pagination.ThreadMemberPaginationActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import java.time.OffsetDateTime
import java.util.Collections
import java.util.stream.LongStream
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ThreadChannelImpl(
    id: Long,
    guild: GuildImpl,
    private val type: ChannelType,
) : AbstractGuildChannelImpl<ThreadChannelImpl>(id, guild),
    ThreadChannel,
    ThreadChannelMixin<ThreadChannelImpl> {
    private val threadMembers: CacheView.SimpleCacheView<ThreadMember> =
        CacheView.SimpleCacheView(ThreadMember::class.java, null)

    private var appliedTags: TLongSet = TLongHashSet(ForumChannel.MAX_POST_TAGS)
    private var autoArchiveDuration: AutoArchiveDuration? = null
    private var parentChannel: IThreadContainerUnion? = null
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

    override fun isDetached(): Boolean = false

    @Nonnull
    override fun getGuild(): GuildImpl = super.getGuild() as GuildImpl

    @Nonnull
    override fun getType(): ChannelType = type

    override fun getLatestMessageIdLong(): Long = latestMessageId

    override fun getMessageCount(): Int = messageCount

    override fun getTotalMessageCount(): Int = totalMessageCount

    override fun getMemberCount(): Int = memberCount

    override fun isLocked(): Boolean = locked

    override fun canTalk(
        @Nonnull member: Member,
    ): Boolean {
        Checks.notNull(member, "Member")
        if (type == ChannelType.GUILD_PRIVATE_THREAD && threadMembers.get(member.idLong) == null) {
            return member.hasPermission(
                getParentChannel(),
                Permission.MANAGE_THREADS,
                Permission.MESSAGE_SEND_IN_THREADS,
            )
        }
        return member.hasPermission(getParentChannel(), Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND_IN_THREADS)
    }

    @Nonnull
    override fun getMembers(): List<Member> = Collections.emptyList()

    @Nonnull
    override fun getParentChannel(): IThreadContainerUnion {
        val realChannel: IThreadContainer? = getGuild().getChannelById(IThreadContainer::class.java, parentChannel!!.idLong)
        if (realChannel != null) {
            parentChannel = realChannel as IThreadContainerUnion
        }
        return parentChannel!!
    }

    @Nonnull
    override fun getAppliedTags(): List<ForumTag> {
        val parent = getParentChannel()
        if (parent.type != ChannelType.FORUM) {
            return Collections.emptyList()
        }
        return parent
            .asForumChannel()
            .availableTagCache
            .stream()
            .filter { tag -> appliedTags.contains(tag.idLong) }
            .collect(Helpers.toUnmodifiableList())
    }

    @Nonnull
    override fun retrieveParentMessage(): RestAction<Message> = getParentMessageChannel().retrieveMessageById(getIdLong())

    @Nonnull
    override fun retrieveStartMessage(): RestAction<Message> = retrieveMessageById(getId())

    @Nonnull
    override fun getPermissionContainer(): IPermissionContainer = getParentChannel()

    @Nonnull
    override fun getThreadMembers(): List<ThreadMember> = threadMembers.asList()

    @Nullable
    override fun getThreadMemberById(id: Long): ThreadMember? = threadMembers.get(id)

    @Nonnull
    override fun retrieveThreadMemberById(id: Long): CacheRestAction<ThreadMember> {
        val jda = getJDA() as JDAImpl
        return DeferredRestAction(
            jda,
            ThreadMember::class.java,
            { getThreadMemberById(id) },
        ) {
            val route =
                Route.Channels.GET_THREAD_MEMBER
                    .compile(getId(), java.lang.Long.toUnsignedString(id))
                    .withQueryParams("with_member", "true")
            RestActionImpl(jda, route) { resp, _ ->
                jda.entityBuilder.createThreadMember(getGuild(), this, resp.getObject())
            }
        }
    }

    @Nonnull
    override fun retrieveThreadMembers(): ThreadMemberPaginationAction = ThreadMemberPaginationActionImpl(this)

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
    override fun join(): RestAction<Void> {
        checkUnarchived()

        val route = Route.Channels.JOIN_THREAD.compile(getId())
        return RestActionImpl(api, route)
    }

    @Nonnull
    override fun leave(): RestAction<Void> {
        checkUnarchived()

        val route = Route.Channels.LEAVE_THREAD.compile(getId())
        return RestActionImpl(api, route)
    }

    @Nonnull
    override fun addThreadMemberById(id: Long): RestAction<Void> {
        checkUnarchived()
        checkInvitable()
        checkPermission(Permission.MESSAGE_SEND_IN_THREADS)

        val route = Route.Channels.ADD_THREAD_MEMBER.compile(getId(), java.lang.Long.toUnsignedString(id))
        return RestActionImpl(api, route)
    }

    @Nonnull
    override fun removeThreadMemberById(id: Long): RestAction<Void> {
        checkUnarchived()

        val privateThreadOwner =
            type == ChannelType.GUILD_PRIVATE_THREAD &&
                ownerId == api.selfUser.idLong
        if (!privateThreadOwner) {
            checkPermission(Permission.MANAGE_THREADS)
        }

        val route = Route.Channels.REMOVE_THREAD_MEMBER.compile(getId(), java.lang.Long.toUnsignedString(id))
        return RestActionImpl(api, route)
    }

    @Nonnull
    override fun getManager(): ThreadChannelManager = ThreadChannelManagerImpl(this)

    override fun checkCanManage() {
        if (isOwner) {
            return
        }

        checkPermission(Permission.MANAGE_THREADS)
    }

    fun getThreadMemberView(): CacheView.SimpleCacheView<ThreadMember> = threadMembers

    override fun setLatestMessageIdLong(latestMessageId: Long): ThreadChannelImpl {
        this.latestMessageId = latestMessageId
        return this
    }

    override fun setAutoArchiveDuration(autoArchiveDuration: AutoArchiveDuration): ThreadChannelImpl {
        this.autoArchiveDuration = autoArchiveDuration
        return this
    }

    fun setParentChannel(channel: IThreadContainer): ThreadChannelImpl {
        parentChannel = channel as IThreadContainerUnion
        return this
    }

    override fun setLocked(locked: Boolean): ThreadChannelImpl {
        this.locked = locked
        return this
    }

    override fun setArchived(archived: Boolean): ThreadChannelImpl {
        this.archived = archived
        return this
    }

    override fun setInvitable(invitable: Boolean): ThreadChannelImpl {
        this.invitable = invitable
        return this
    }

    override fun setArchiveTimestamp(archiveTimestamp: Long): ThreadChannelImpl {
        this.archiveTimestamp = archiveTimestamp
        return this
    }

    override fun setCreationTimestamp(creationTimestamp: Long): ThreadChannelImpl {
        this.creationTimestamp = creationTimestamp
        return this
    }

    override fun setOwnerId(ownerId: Long): ThreadChannelImpl {
        this.ownerId = ownerId
        return this
    }

    override fun setMessageCount(messageCount: Int): ThreadChannelImpl {
        this.messageCount = messageCount
        return this
    }

    override fun setTotalMessageCount(messageCount: Int): ThreadChannelImpl {
        totalMessageCount = maxOf(messageCount, this.messageCount) // If this is 0 we use the older count
        return this
    }

    override fun setMemberCount(memberCount: Int): ThreadChannelImpl {
        this.memberCount = memberCount
        return this
    }

    override fun setSlowmode(slowmode: Int): ThreadChannelImpl {
        this.slowmode = slowmode
        return this
    }

    fun setAppliedTags(tags: LongStream): ThreadChannelImpl {
        val set = TLongHashSet(ForumChannel.MAX_POST_TAGS)
        tags.forEach { set.add(it) }
        appliedTags = set
        return this
    }

    fun getArchiveTimestamp(): Long = archiveTimestamp

    fun getAppliedTagsSet(): TLongSet = appliedTags

    fun getRawFlags(): Int = flags

    private fun checkUnarchived() {
        if (archived) {
            throw IllegalStateException("Cannot modify a ThreadChannel while it is archived!")
        }
    }

    private fun checkInvitable() {
        if (ownerId == api.selfUser.idLong) {
            return
        }

        if (!isPublic && !isInvitable) {
            checkPermission(Permission.MANAGE_THREADS)
        }
    }
}
