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

package net.dv8tion.jda.internal.managers

import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.ScheduledEvent
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.managers.ScheduledEventManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.ScheduledEventImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import okhttp3.RequestBody
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val MAX_YEARS_IN_FUTURE = 5L

class ScheduledEventManagerImpl(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var event: ScheduledEventImpl,
) : ManagerBase<ScheduledEventManager>(
        event.getJDA(),
        Route.Guilds.MODIFY_SCHEDULED_EVENT.compile(event.getGuild().getId(), event.getId()),
    ),
    ScheduledEventManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var name: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var description: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var channelId: Long = 0

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var location: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var image: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var startTime: OffsetDateTime? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var endTime: OffsetDateTime? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var entityType: ScheduledEvent.Type? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var status: ScheduledEvent.Status? = null

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    override fun checkPermissions(): Boolean {
        event.checkManagePermissions()
        return super.checkPermissions()
    }

    @Nonnull
    override fun getScheduledEvent(): ScheduledEvent {
        val realEvent = event.getGuild().getScheduledEventById(event.getIdLong())
        if (realEvent != null) {
            event = realEvent as ScheduledEventImpl
        }
        return event
    }

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): ScheduledEventManagerImpl {
        Checks.notBlank(name, "Name")
        Checks.notLonger(name, ScheduledEvent.MAX_NAME_LENGTH, "Name")
        this.name = name
        set = set or ScheduledEventManager.NAME
        return this
    }

    @Nonnull
    override fun setDescription(
        @Nullable description: String?,
    ): ScheduledEventManager {
        Checks.notLonger(description, ScheduledEvent.MAX_DESCRIPTION_LENGTH, "Description")
        this.description = description
        set = set or ScheduledEventManager.DESCRIPTION
        return this
    }

    @Nonnull
    override fun setImage(
        @Nullable icon: Icon?,
    ): ScheduledEventManager {
        image = icon
        set = set or ScheduledEventManager.IMAGE
        return this
    }

    @Nonnull
    override fun setLocation(
        @Nonnull channel: GuildChannel,
    ): ScheduledEventManager {
        Checks.notNull(channel, "Channel")
        if (channel.getGuild() != event.getGuild()) {
            throw IllegalArgumentException(
                "Invalid parameter: Channel has to be from the same guild as the scheduled event!",
            )
        } else if (channel is StageChannel) {
            channelId = channel.getIdLong()
            entityType = ScheduledEvent.Type.STAGE_INSTANCE
        } else if (channel is VoiceChannel) {
            channelId = channel.getIdLong()
            entityType = ScheduledEvent.Type.VOICE
        } else {
            throw IllegalArgumentException("Invalid parameter: Can only set location to Voice and Stage Channels!")
        }

        set = set or ScheduledEventManager.LOCATION
        return this
    }

    @Nonnull
    override fun setLocation(
        @Nonnull location: String,
    ): ScheduledEventManager {
        Checks.notBlank(location, "Location")
        Checks.notLonger(location, ScheduledEvent.MAX_LOCATION_LENGTH, "Location")
        this.location = location
        entityType = ScheduledEvent.Type.EXTERNAL
        set = set or ScheduledEventManager.LOCATION
        return this
    }

    @Nonnull
    override fun setStartTime(
        @Nonnull startTime: TemporalAccessor,
    ): ScheduledEventManager {
        Checks.notNull(startTime, "Start Time")
        val offsetStartTime = Helpers.toOffsetDateTime(startTime)!!
        Checks.check(offsetStartTime.isAfter(OffsetDateTime.now()), "Cannot schedule event in the past!")
        Checks.check(
            offsetStartTime.isBefore(OffsetDateTime.now().plusYears(MAX_YEARS_IN_FUTURE)),
            "Scheduled start and end times must be within five years.",
        )
        this.startTime = offsetStartTime
        set = set or ScheduledEventManager.START_TIME
        return this
    }

    @Nonnull
    override fun setEndTime(
        @Nonnull endTime: TemporalAccessor,
    ): ScheduledEventManager {
        Checks.notNull(endTime, "End Time")
        val offsetEndTime = Helpers.toOffsetDateTime(endTime)!!
        Checks.check(
            offsetEndTime.isBefore(OffsetDateTime.now().plusYears(MAX_YEARS_IN_FUTURE)),
            "Scheduled start and end times must be within five years.",
        )
        this.endTime = offsetEndTime
        set = set or ScheduledEventManager.END_TIME
        return this
    }

    @Nonnull
    override fun setStatus(
        @Nonnull newStatus: ScheduledEvent.Status,
    ): ScheduledEventManager {
        Checks.notNull(newStatus, "Status")
        when (newStatus) {
            ScheduledEvent.Status.ACTIVE,
            ScheduledEvent.Status.CANCELED,
            ScheduledEvent.Status.COMPLETED,
            -> Unit
            ScheduledEvent.Status.SCHEDULED,
            ScheduledEvent.Status.UNKNOWN,
            -> throw IllegalArgumentException("Cannot change scheduled event status to $newStatus")
        }

        // get the current status of the event. multiple-usage
        val currentStatus = getScheduledEvent().getStatus()

        when (currentStatus) {
            ScheduledEvent.Status.SCHEDULED ->
                // event is scheduled -> new status can be only active or cancel
                Checks.check(
                    newStatus == ScheduledEvent.Status.ACTIVE || newStatus == ScheduledEvent.Status.CANCELED,
                    "Cannot perform status update! A scheduled event with status SCHEDULED can only be set to ACTIVE or CANCELED status.",
                )

            ScheduledEvent.Status.ACTIVE ->
                // event is active -> new status can be only completed
                Checks.check(
                    newStatus == ScheduledEvent.Status.COMPLETED,
                    "Cannot perform status updated! A scheduled event with status ACTIVE can only be set to COMPLETED status.",
                )

            ScheduledEvent.Status.COMPLETED,
            ScheduledEvent.Status.CANCELED,
            ScheduledEvent.Status.UNKNOWN,
            ->
                // event is completed or canceled -> can't update status
                throw IllegalArgumentException(
                    "Cannot perform status update! Event is " +
                        currentStatus.name.lowercase(Locale.ROOT) + ".",
                )
        }

        status = newStatus
        set = set or ScheduledEventManager.STATUS
        return this
    }

    override fun finalizeData(): RequestBody {
        preChecks()
        val obj = DataObject.empty()
        if (shouldUpdate(ScheduledEventManager.NAME)) {
            obj.put("name", name)
        }
        if (shouldUpdate(ScheduledEventManager.DESCRIPTION)) {
            obj.put("description", description)
        }
        if (shouldUpdate(ScheduledEventManager.LOCATION)) {
            obj.put("entity_type", entityType!!.getKey())
            when (entityType) {
                ScheduledEvent.Type.STAGE_INSTANCE,
                ScheduledEvent.Type.VOICE,
                -> {
                    obj.putNull("entity_metadata")
                    obj.put("channel_id", channelId)
                }

                ScheduledEvent.Type.EXTERNAL -> {
                    obj.put("entity_metadata", DataObject.empty().put("location", location))
                    obj.put("channel_id", null)
                }

                else -> throw IllegalStateException("ScheduledEventType $entityType is not supported!")
            }
        }
        if (shouldUpdate(ScheduledEventManager.START_TIME)) {
            obj.put("scheduled_start_time", startTime!!.format(DateTimeFormatter.ISO_DATE_TIME))
        }
        if (shouldUpdate(ScheduledEventManager.END_TIME)) {
            obj.put("scheduled_end_time", endTime!!.format(DateTimeFormatter.ISO_DATE_TIME))
        }
        if (shouldUpdate(ScheduledEventManager.IMAGE)) {
            obj.put("image", image?.getEncoding())
        }
        if (shouldUpdate(ScheduledEventManager.STATUS)) {
            obj.put("status", status!!.getKey())
        }

        return getRequestBody(obj)
    }

    private fun preChecks() {
        if (shouldUpdate(ScheduledEventManager.LOCATION)) {
            Checks.check(
                getScheduledEvent().getStatus() == ScheduledEvent.Status.SCHEDULED ||
                    (
                        entityType == ScheduledEvent.Type.EXTERNAL &&
                            getScheduledEvent().getType() == ScheduledEvent.Type.EXTERNAL
                    ),
                "Cannot update location type or location channel of non-scheduled event.",
            )
            if (entityType == ScheduledEvent.Type.EXTERNAL &&
                endTime == null &&
                getScheduledEvent().getEndTime() == null
            ) {
                throw IllegalStateException("Missing required parameter: End Time")
            }
        }

        if (shouldUpdate(ScheduledEventManager.START_TIME)) {
            Checks.check(
                getScheduledEvent().getStatus() == ScheduledEvent.Status.SCHEDULED,
                "Cannot update start time of non-scheduled event!",
            )
            Checks.check(
                (endTime == null && getScheduledEvent().getEndTime() == null) ||
                    (endTime ?: getScheduledEvent().getEndTime())!!.isAfter(startTime),
                "Cannot schedule event to end before starting!",
            )
        }

        if (shouldUpdate(ScheduledEventManager.END_TIME)) {
            Checks.check(
                (startTime ?: getScheduledEvent().getStartTime()).isBefore(endTime),
                "Cannot schedule event to end before starting!",
            )
        }
    }
}
