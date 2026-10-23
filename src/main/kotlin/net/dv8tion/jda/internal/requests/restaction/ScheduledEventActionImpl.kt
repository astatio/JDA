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

package net.dv8tion.jda.internal.requests.restaction

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.ScheduledEvent
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.ScheduledEventAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import okhttp3.RequestBody
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

private const val PRIVACY_LEVEL = 2
private const val MAX_SCHEDULE_YEARS = 5L

open class ScheduledEventActionImpl private constructor(
    @JvmField protected val guild: Guild,
    @JvmField protected val entityType: ScheduledEvent.Type,
    name: String,
) : AuditableRestActionImpl<ScheduledEvent>(guild.jda, Route.Guilds.CREATE_SCHEDULED_EVENT.compile(guild.id)),
    ScheduledEventAction {
    @JvmField
    protected var name: String = name

    @JvmField
    protected var description: String? = null

    @JvmField
    protected var image: Icon? = null

    @JvmField
    protected var channelId: Long = 0

    @JvmField
    protected var location: String? = null

    @JvmField
    protected var startTime: OffsetDateTime? = null

    @JvmField
    protected var endTime: OffsetDateTime? = null

    init {
        setName(name)
    }

    constructor(
        name: String,
        location: String,
        startTime: TemporalAccessor,
        endTime: TemporalAccessor?,
        guild: Guild,
    ) : this(guild, ScheduledEvent.Type.EXTERNAL, name) {
        setStartTime(startTime)
        setEndTime(endTime)
        Checks.notNull(location, "Location")
        Checks.notBlank(location, "Location")
        Checks.notEmpty(location, "Location")
        Checks.notLonger(location, ScheduledEvent.MAX_LOCATION_LENGTH, "Location")
        this.location = location
    }

    constructor(name: String, channel: GuildChannel, startTime: TemporalAccessor, guild: Guild) : this(
        guild,
        when (channel) {
            is StageChannel -> ScheduledEvent.Type.STAGE_INSTANCE
            is VoiceChannel -> ScheduledEvent.Type.VOICE
            else -> throw IllegalArgumentException(
                "Invalid parameter: Can only set location to Voice and Stage Channels!",
            )
        },
        name,
    ) {
        setStartTime(startTime)
        Checks.notNull(channel, "Channel")
        if (channel.guild != guild) {
            throw IllegalArgumentException(
                "Invalid parameter: Channel has to be from the same guild as the scheduled event!",
            )
        }
        this.channelId = channel.idLong
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): ScheduledEventActionImpl = super.setCheck(checks) as ScheduledEventActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): ScheduledEventActionImpl = super<AuditableRestActionImpl>.timeout(timeout, unit) as ScheduledEventActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): ScheduledEventActionImpl =
        super<AuditableRestActionImpl>.deadline(timestamp) as ScheduledEventActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun reason(reason: String?): ScheduledEventActionImpl = super.reason(reason) as ScheduledEventActionImpl

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nonnull
    override fun setName(name: String): ScheduledEventActionImpl {
        Checks.notBlank(name, "Name")
        Checks.notLonger(name, ScheduledEvent.MAX_NAME_LENGTH, "Name")
        this.name = name
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setDescription(description: String?): ScheduledEventActionImpl {
        if (description != null) {
            Checks.notLonger(description, ScheduledEvent.MAX_DESCRIPTION_LENGTH, "Description")
        }
        this.description = description
        return this
    }

    @Nonnull
    override fun setStartTime(
        @Nonnull startTime: TemporalAccessor,
    ): ScheduledEventAction {
        Checks.notNull(startTime, "Start Time")
        val offsetStartTime = Helpers.toOffsetDateTime(startTime)!!
        Checks.check(offsetStartTime.isAfter(OffsetDateTime.now()), "Cannot schedule event in the past!")
        Checks.check(
            offsetStartTime.isBefore(OffsetDateTime.now().plusYears(MAX_SCHEDULE_YEARS)),
            "Scheduled start and end times must be within five years.",
        )
        this.startTime = offsetStartTime
        return this
    }

    @Nonnull
    override fun setEndTime(endTime: TemporalAccessor?): ScheduledEventAction {
        Checks.notNull(endTime, "End Time")
        val offsetEndTime = Helpers.toOffsetDateTime(endTime)!!
        Checks.check(offsetEndTime.isAfter(startTime!!), "Cannot schedule event to end before its starting!")
        Checks.check(
            offsetEndTime.isBefore(OffsetDateTime.now().plusYears(MAX_SCHEDULE_YEARS)),
            "Scheduled start and end times must be within five years.",
        )
        this.endTime = offsetEndTime
        return this
    }

    @Nonnull
    override fun setImage(icon: Icon?): ScheduledEventAction {
        this.image = icon
        return this
    }

    override fun finalizeData(): RequestBody? {
        val json = DataObject.empty()
        json.put("entity_type", entityType.key)
        json.put("privacy_level", PRIVACY_LEVEL)
        json.put("name", name)
        json.put("scheduled_start_time", startTime!!.format(DateTimeFormatter.ISO_DATE_TIME))

        when (entityType) {
            ScheduledEvent.Type.STAGE_INSTANCE, ScheduledEvent.Type.VOICE -> json.put("channel_id", channelId)
            ScheduledEvent.Type.EXTERNAL ->
                json.put("entity_metadata", DataObject.empty().put("location", location))
            else -> throw IllegalStateException("ScheduledEventType $entityType is not supported!")
        }

        if (description != null) {
            json.put("description", description)
        }
        if (image != null) {
            json.put("image", image!!.encoding)
        }
        if (endTime != null) {
            json.put("scheduled_end_time", endTime!!.format(DateTimeFormatter.ISO_DATE_TIME))
        }

        return getRequestBody(json)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<ScheduledEvent>,
    ) {
        request.onSuccess(api.entityBuilder.createScheduledEvent(guild as GuildImpl, response.getObject()))
    }
}
