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

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.user.UserTypingEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.MemberImpl
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TypingStartHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        var guild: GuildImpl? = null
        if (!content.isNull("guild_id")) {
            val guildId = content.getUnsignedLong("guild_id")
            guild = getJDA().getGuildById(guildId) as GuildImpl?
            if (getJDA().getGuildSetupController().isLocked(guildId)) {
                return guildId // Don't cache typing events
            } else if (guild == null) {
                return null // Don't cache typing events
            }
        }

        val channelId = content.getLong("channel_id")

        val channel = getJDA().getChannelById(MessageChannel::class.java, channelId)

        // We don't have the channel cached yet. We chose not to cache this event
        // because that happens very often and could easily fill up the EventCache if
        // we, for some reason, never get the channel. Especially in an active channel.
        if (channel == null) {
            return null
        }

        val userId = content.getLong("user_id")
        var user: User?
        var member: MemberImpl? = null
        if (channel is PrivateChannel) {
            user = channel.getUser()
        } else {
            user = getJDA().getUsersView().get(userId)
        }
        if (!content.isNull("member")) {
            // Try to load member for the typing event
            val entityBuilder: EntityBuilder = getJDA().getEntityBuilder()
            member = entityBuilder.createMember(guild, content.getObject("member"))
            entityBuilder.updateMemberCache(member)
            user = member.getUser()
        }

        if (user == null) {
            // Just like in the comment above,
            // if for some reason we don't have the user
            // then we will just throw the event away.
            return null
        }
        val timestamp: OffsetDateTime =
            Instant.ofEpochSecond(content.getInt("timestamp").toLong()).atOffset(ZoneOffset.UTC)
        getJDA().handleEvent(UserTypingEvent(getJDA(), responseNumber, user, channel, timestamp, member))
        return null
    }
}
