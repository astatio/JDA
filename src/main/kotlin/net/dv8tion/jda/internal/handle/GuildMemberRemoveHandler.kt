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
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.GuildVoiceStateImpl
import net.dv8tion.jda.internal.entities.MemberImpl
import net.dv8tion.jda.internal.utils.cache.SnowflakeCacheViewImpl

class GuildMemberRemoveHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val id = content.getLong("guild_id")
        val setup = getJDA().getGuildSetupController().onRemoveMember(id, content)
        if (setup) {
            return null
        }

        val guild = getJDA().getGuildsView().get(id) as GuildImpl?
        if (guild == null) {
            // We probably just left the guild and this event is trying to remove us from the guild,
            // therefore ignore
            return null
        }

        val userId = content.getObject("user").getUnsignedLong("id")
        if (userId == getJDA().getSelfUser().getIdLong()) {
            // We probably just left the guild and this event is trying to remove us from the guild,
            // therefore ignore
            return null
        }

        try {
            val user: User = api.getEntityBuilder().createUser(content.getObject("user"))

            val voiceState: GuildVoiceStateImpl? = guild.getVoiceStateView().getElementById(userId)
            if (voiceState != null && voiceState.inAudioChannel()) {
                // If this user was in an AudioChannel, fire VoiceLeaveEvent.
                val channel: AudioChannelUnion? = voiceState.getChannel()
                voiceState.updateConnectedChannel(null)

                getJDA().handleEvent(
                    GuildVoiceUpdateEvent(getJDA(), responseNumber, voiceState.getMember(), channel),
                )
            }

            val member = guild.getMembersView().remove(userId) as MemberImpl?

            val userView: SnowflakeCacheViewImpl<User> = getJDA().getUsersView()
            userView.writeLock().use {
                if (user.getMutualGuilds().isEmpty()) {
                    userView.remove(userId)
                    getJDA().getEventCache().clear(EventCache.Type.USER, userId)
                }
            }

            // Cache independent event
            getJDA().handleEvent(GuildMemberRemoveEvent(getJDA(), responseNumber, guild, user, member))
            return null
        } finally {
            // Reduce member count and remove dependent caches
            guild.onMemberRemove(userId)
        }
    }
}
