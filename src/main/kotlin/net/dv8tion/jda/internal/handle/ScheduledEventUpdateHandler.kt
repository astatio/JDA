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

import net.dv8tion.jda.api.entities.ScheduledEvent
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateCoverImageEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateDescriptionEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateEndTimeEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateImageEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateLocationEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateNameEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateStartTimeEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateStatusEvent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.ScheduledEventImpl
import java.util.Objects

class ScheduledEventUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("DEPRECATION", "ReturnCount") // legacy event + faithfully ported guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        if (!getJDA().isCacheFlagSet(CacheFlag.SCHEDULED_EVENTS)) {
            return null
        }
        val guildId = content.getUnsignedLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        val guild = getJDA().getGuildById(guildId) as GuildImpl?
        if (guild == null) {
            EventCache.LOG.debug("Caching SCHEDULED_EVENT_UPDATE for uncached guild with id {}", guildId)
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            return null
        }

        val event = guild.getScheduledEventById(content.getUnsignedLong("id")) as ScheduledEventImpl?
        if (event == null) {
            api.getEntityBuilder().createScheduledEvent(guild, content)
            return null
        }

        val name = content.getString("name")
        val description = content.getString("description", null)
        val startTime = content.getOffsetDateTime("scheduled_start_time")
        val endTime = content.getOffsetDateTime("scheduled_end_time", null)
        val status = ScheduledEvent.Status.fromKey(content.getInt("status", -1))
        val imageId = content.getString("image", null)
        val rawLocation = content.getString("channel_id", null)
        val location: String
        var channel: GuildChannel? = null
        val oldLocation = event.getLocation()

        if (rawLocation != null) {
            location = rawLocation
            channel = guild.getGuildChannelById(rawLocation)
        } else { // null in some cases due to discord validation bugs
            location =
                content
                    .optObject("entity_metadata")
                    .map { o: DataObject -> o.getString("location", "") }
                    .orElse("")
        }

        if (!Objects.equals(name, event.getName())) {
            val oldName = event.getName()
            event.setName(name)
            getJDA().handleEvent(ScheduledEventUpdateNameEvent(getJDA(), responseNumber, event, oldName))
        }
        if (!Objects.equals(description, event.getDescription())) {
            val oldDescription = event.getDescription()
            event.setDescription(description)
            getJDA().handleEvent(
                ScheduledEventUpdateDescriptionEvent(getJDA(), responseNumber, event, oldDescription),
            )
        }
        if (!Objects.equals(startTime, event.getStartTime())) {
            val oldStartTime = event.getStartTime()
            event.setStartTime(startTime)
            getJDA().handleEvent(ScheduledEventUpdateStartTimeEvent(getJDA(), responseNumber, event, oldStartTime))
        }
        if (!Objects.equals(endTime, event.getEndTime())) {
            val oldEndTime = event.getEndTime()
            event.setEndTime(endTime)
            getJDA().handleEvent(ScheduledEventUpdateEndTimeEvent(getJDA(), responseNumber, event, oldEndTime))
        }
        if (!Objects.equals(status, event.getStatus())) {
            val oldStatus = event.getStatus()
            event.setStatus(status)
            getJDA().handleEvent(ScheduledEventUpdateStatusEvent(getJDA(), responseNumber, event, oldStatus))
        }
        if (channel == null && location != event.getLocation()) {
            event.setLocation(location)
            event.setType(ScheduledEvent.Type.EXTERNAL)
            getJDA().handleEvent(ScheduledEventUpdateLocationEvent(getJDA(), responseNumber, event, oldLocation))
        }
        if (channel is StageChannel && location != event.getLocation()) {
            event.setLocation(channel.getId())
            event.setType(ScheduledEvent.Type.STAGE_INSTANCE)
            getJDA().handleEvent(ScheduledEventUpdateLocationEvent(getJDA(), responseNumber, event, oldLocation))
        }
        if (channel is VoiceChannel && location != event.getLocation()) {
            event.setLocation(channel.getId())
            event.setType(ScheduledEvent.Type.VOICE)
            getJDA().handleEvent(ScheduledEventUpdateLocationEvent(getJDA(), responseNumber, event, oldLocation))
        }
        if (!Objects.equals(imageId, event.getCoverImageId())) {
            val oldCoverImageId = event.getCoverImageId()
            val oldImageUrl = event.getImageUrl() as String

            event.setCoverImage(imageId)
            getJDA().handleEvent(
                ScheduledEventUpdateCoverImageEvent(getJDA(), responseNumber, event, oldCoverImageId),
            )

            // Legacy
            getJDA().handleEvent(ScheduledEventUpdateImageEvent(getJDA(), responseNumber, event, oldImageUrl))
        }
        return null
    }
}
