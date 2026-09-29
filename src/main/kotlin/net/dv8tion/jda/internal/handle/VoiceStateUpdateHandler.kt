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

import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceDeafenEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMuteEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceRequestToSpeakEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSelfDeafenEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSelfMuteEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceStreamEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSuppressEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceVideoEvent
import net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.GuildVoiceStateImpl
import net.dv8tion.jda.internal.managers.AudioManagerImpl
import net.dv8tion.jda.internal.requests.WebSocketClient
import java.time.OffsetDateTime

class VoiceStateUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId: Long? = if (content.isNull("guild_id")) null else content.getLong("guild_id")
        if (guildId == null) {
            return null // unhandled for calls
        }
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        if (content.isNull("member")) {
            WebSocketClient.LOG.debug("Discarding VOICE_STATE_UPDATE with missing member. JSON: {}", content)
            return null
        }

        handleGuildVoiceState(content)
        return null
    }

    @Suppress("ReturnCount", "LongMethod", "NestedBlockDepth") // faithfully ported voice-state update from Java
    private fun handleGuildVoiceState(content: DataObject) {
        val userId = content.getLong("user_id")
        val guildId = content.getLong("guild_id")
        val channelId: Long? = if (!content.isNull("channel_id")) content.getLong("channel_id") else null
        val sessionId: String? = if (!content.isNull("session_id")) content.getString("session_id") else null
        val selfMuted = content.getBoolean("self_mute")
        val selfDeafened = content.getBoolean("self_deaf")
        val guildMuted = content.getBoolean("mute")
        val guildDeafened = content.getBoolean("deaf")
        val suppressed = content.getBoolean("suppress")
        val stream = content.getBoolean("self_stream")
        val video = content.getBoolean("self_video", false)
        val requestToSpeak = content.getString("request_to_speak_timestamp", null)
        var requestToSpeakTime: OffsetDateTime? = null
        var requestToSpeakTimestamp = 0L
        if (requestToSpeak != null) {
            requestToSpeakTime = OffsetDateTime.parse(requestToSpeak)
            requestToSpeakTimestamp = requestToSpeakTime.toInstant().toEpochMilli()
        }

        val guild = getJDA().getGuildById(guildId) as GuildImpl?
        if (guild == null) {
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            EventCache.LOG.debug(
                "Received a VOICE_STATE_UPDATE for a Guild that has yet to be cached. JSON: {}",
                content,
            )
            return
        }

        var channel: AudioChannel? = null
        if (channelId != null) {
            channel = guild.getGuildChannelById(channelId) as AudioChannel?
        }

        if (channel == null && (channelId != null)) {
            getJDA()
                .getEventCache()
                .cache(EventCache.Type.CHANNEL, channelId, responseNumber, allContent, this::handle)
            EventCache.LOG.debug(
                "Received VOICE_STATE_UPDATE for an AudioChannel that has yet to be cached. JSON: {}",
                content,
            )
            return
        }

        val memberJson = content.getObject("member")
        val member = getJDA().getEntityBuilder().createMember(guild, memberJson)

        var vState = member.getVoiceState()
        if (vState == null) {
            if (guild.shouldCacheVoiceState(userId)) {
                vState = GuildVoiceStateImpl(member)
            } else {
                return
            }
        }

        if (sessionId != null) {
            vState.setSessionId(sessionId) // Cant really see a reason for an event for this
        }
        val voiceInterceptor = getJDA().getVoiceInterceptor()
        val isSelf = guild.getSelfMember() == member

        val wasMute = vState.isMuted()
        val wasDeaf = vState.isDeafened()

        if (selfMuted != vState.isSelfMuted()) {
            vState.setSelfMuted(selfMuted)
            getJDA().getEntityBuilder().updateMemberCache(member)
            getJDA().handleEvent(GuildVoiceSelfMuteEvent(getJDA(), responseNumber, member, selfMuted))
        }
        if (selfDeafened != vState.isSelfDeafened()) {
            vState.setSelfDeafened(selfDeafened)
            getJDA().getEntityBuilder().updateMemberCache(member)
            getJDA().handleEvent(GuildVoiceSelfDeafenEvent(getJDA(), responseNumber, member, selfDeafened))
        }
        if (guildMuted != vState.isGuildMuted()) {
            vState.setGuildMuted(guildMuted)
            getJDA().getEntityBuilder().updateMemberCache(member)
            getJDA().handleEvent(GuildVoiceGuildMuteEvent(getJDA(), responseNumber, member, guildMuted))
        }
        if (guildDeafened != vState.isGuildDeafened()) {
            vState.setGuildDeafened(guildDeafened)
            getJDA().getEntityBuilder().updateMemberCache(member)
            getJDA().handleEvent(GuildVoiceGuildDeafenEvent(getJDA(), responseNumber, member, guildDeafened))
        }
        if (suppressed != vState.isSuppressed()) {
            vState.setSuppressed(suppressed)
            getJDA().getEntityBuilder().updateMemberCache(member)
            getJDA().handleEvent(GuildVoiceSuppressEvent(getJDA(), responseNumber, member, suppressed))
        }
        if (stream != vState.isStream()) {
            vState.setStream(stream)
            getJDA().getEntityBuilder().updateMemberCache(member)
            getJDA().handleEvent(GuildVoiceStreamEvent(getJDA(), responseNumber, member, stream))
        }
        if (video != vState.isSendingVideo()) {
            vState.setVideo(video)
            getJDA().getEntityBuilder().updateMemberCache(member)
            getJDA().handleEvent(GuildVoiceVideoEvent(getJDA(), responseNumber, member, video))
        }
        if (wasMute != vState.isMuted()) {
            getJDA().handleEvent(GuildVoiceMuteEvent(getJDA(), responseNumber, member, vState.isMuted()))
        }
        if (wasDeaf != vState.isDeafened()) {
            getJDA().handleEvent(GuildVoiceDeafenEvent(getJDA(), responseNumber, member, vState.isDeafened()))
        }
        if (requestToSpeakTimestamp != vState.getRequestToSpeak()) {
            val oldRequestToSpeak = vState.getRequestToSpeakTimestamp()
            vState.setRequestToSpeak(requestToSpeakTime)
            getJDA().handleEvent(
                GuildVoiceRequestToSpeakEvent(
                    getJDA(),
                    responseNumber,
                    member,
                    oldRequestToSpeak,
                    requestToSpeakTime,
                ),
            )
        }

        if (channel != vState.getChannel()) {
            val oldChannel = vState.getChannel()
            vState.updateConnectedChannel(channel)

            if (oldChannel == null) {
                getJDA().getEntityBuilder().updateMemberCache(member)
            } else if (channel == null) {
                if (isSelf) {
                    getJDA().getDirectAudioController().update(guild, null)
                }
                getJDA().getEntityBuilder().updateMemberCache(member, memberJson.isNull("joined_at"))
            } else {
                val mng = getJDA().getAudioManagersView().get(guildId) as AudioManagerImpl?
                // If the currently connected account is the one that is being moved
                if (isSelf && mng != null && voiceInterceptor == null) {
                    // And this instance of JDA is connected or attempting to connect,
                    // then change the channel we expect to be connected to.
                    if (mng.isConnected()) {
                        mng.setConnectedChannel(channel)
                    }

                    // If we have connected (VOICE_SERVER_UPDATE received and AudioConnection
                    // created (actual connection might still be setting up)),
                    // then we need to stop sending audioOpen/Move requests through the MainWS
                    // if the channel we have just joined / moved to
                    // is the same as the currently queued audioRequest (handled by updateAudioConnection)
                    if (mng.isConnected()) {
                        getJDA().getDirectAudioController().update(guild, channel)
                    }
                    // If we are not already connected this will be removed by VOICE_SERVER_UPDATE
                }

                getJDA().getEntityBuilder().updateMemberCache(member)
            }

            getJDA().handleEvent(GuildVoiceUpdateEvent(getJDA(), responseNumber, member, oldChannel))
        }

        if (isSelf && voiceInterceptor != null) {
            if (voiceInterceptor.onVoiceStateUpdate(
                    VoiceDispatchInterceptor.VoiceStateUpdate(channel, vState, allContent),
                )
            ) {
                getJDA().getDirectAudioController().update(guild, channel)
            }
        }

        guild.updateRequestToSpeak()
    }
}
