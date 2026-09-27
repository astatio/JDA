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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.ScheduledEvent
import net.dv8tion.jda.api.entities.ScheduledEvent.Status
import net.dv8tion.jda.api.entities.ScheduledEvent.Type
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.ScheduledEventManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.pagination.ScheduledEventMembersPaginationAction
import net.dv8tion.jda.api.utils.ImageFormat
import net.dv8tion.jda.internal.managers.ScheduledEventManagerImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.requests.restaction.pagination.ScheduledEventMembersPaginationActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import java.time.OffsetDateTime
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ScheduledEventImpl(
    private val id: Long,
    private val guild: Guild,
) : ScheduledEvent {
    private var name: String? = null
    private var description: String? = null
    private var startTime: OffsetDateTime? = null
    private var endTime: OffsetDateTime? = null
    private var coverImage: String? = null
    private var status: Status? = null
    private var type: Type? = null
    private var creator: User? = null
    private var creatorId: Long = 0
    private var interestedUserCount: Int = 0
    private var location: String? = null

    @Nonnull
    override fun getName(): String = name!!

    @Nullable
    override fun getDescription(): String? = description

    @Nullable
    override fun getCoverImageId(): String? = coverImage

    @Nullable
    override fun getImageUrl(): String? =
        coverImage?.let {
            getCoverImageUrl(if (it.startsWith("a_")) ImageFormat.ANIMATED_WEBP else ImageFormat.PNG)
        }

    @Nullable
    override fun getCreator(): User? = creator

    override fun getCreatorIdLong(): Long = creatorId

    @Nonnull
    override fun getStatus(): Status = status!!

    @Nonnull
    override fun getType(): Type = type!!

    @Nonnull
    override fun getStartTime(): OffsetDateTime = startTime!!

    @Nullable
    override fun getEndTime(): OffsetDateTime? = endTime

    @Nullable
    override fun getChannel(): GuildChannelUnion? {
        if (type!!.isChannel) {
            return guild.getGuildChannelById(location!!) as GuildChannelUnion?
        }
        return null
    }

    @Nonnull
    override fun getLocation(): String = location!!

    @Nonnull
    override fun getJumpUrl(): String = Helpers.format(ScheduledEvent.JUMP_URL, getGuild().getId(), id)

    override fun getInterestedUserCount(): Int = interestedUserCount

    @Nonnull
    override fun getGuild(): Guild = guild

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getManager(): ScheduledEventManager = ScheduledEventManagerImpl(this)

    @Nonnull
    override fun delete(): AuditableRestAction<Void> {
        checkManagePermissions()

        val route = Route.Guilds.DELETE_SCHEDULED_EVENT.compile(guild.id, id.toString())
        return AuditableRestActionImpl(getJDA(), route)
    }

    fun checkManagePermissions() {
        val selfMember: SelfMember = guild.selfMember
        if (creatorId == selfMember.idLong) {
            if (!selfMember.hasPermission(Permission.MANAGE_EVENTS) &&
                !selfMember.hasPermission(Permission.CREATE_SCHEDULED_EVENTS)
            ) {
                throw InsufficientPermissionException(
                    guild,
                    Permission.MANAGE_EVENTS,
                    "Deleting your own scheduled events requires the MANAGE_EVENTS or CREATE_SCHEDULED_EVENTS permission",
                )
            }
        } else {
            if (!selfMember.hasPermission(Permission.MANAGE_EVENTS)) {
                throw InsufficientPermissionException(guild, Permission.MANAGE_EVENTS)
            }
        }
    }

    @Nonnull
    override fun retrieveInterestedMembers(): ScheduledEventMembersPaginationAction = ScheduledEventMembersPaginationActionImpl(this)

    fun setName(name: String): ScheduledEventImpl {
        this.name = name
        return this
    }

    fun setType(type: Type): ScheduledEventImpl {
        this.type = type
        return this
    }

    fun setLocation(location: String): ScheduledEventImpl {
        this.location = location
        return this
    }

    fun setDescription(description: String): ScheduledEventImpl {
        this.description = description
        return this
    }

    fun setCoverImage(image: String): ScheduledEventImpl {
        this.coverImage = image
        return this
    }

    fun setCreatorId(creatorId: Long): ScheduledEventImpl {
        this.creatorId = creatorId
        return this
    }

    fun setCreator(creator: User): ScheduledEventImpl {
        this.creator = creator
        return this
    }

    fun setStatus(status: Status): ScheduledEventImpl {
        this.status = status
        return this
    }

    fun setStartTime(startTime: OffsetDateTime): ScheduledEventImpl {
        this.startTime = startTime
        return this
    }

    fun setEndTime(endTime: OffsetDateTime): ScheduledEventImpl {
        this.endTime = endTime
        return this
    }

    fun setInterestedUserCount(interestedUserCount: Int): ScheduledEventImpl {
        this.interestedUserCount = interestedUserCount
        return this
    }

    override fun compareTo(
        @Nonnull scheduledEvent: ScheduledEvent,
    ): Int {
        Checks.notNull(scheduledEvent, "Scheduled Event")
        Checks.check(
            getGuild() == scheduledEvent.guild,
            "Cannot compare two Scheduled Events belonging to seperate guilds!",
        )

        val startTimeComparison =
            OffsetDateTime.timeLineOrder().compare(getStartTime(), scheduledEvent.startTime)
        return if (startTimeComparison == 0) {
            id.compareTo(scheduledEvent.idLong)
        } else {
            startTimeComparison
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ScheduledEventImpl) {
            return false
        }
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ScheduledEvent:" + getName() + '(' + id + ')'
}
