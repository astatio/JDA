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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.audio.AudioConnection
import net.dv8tion.jda.internal.managers.AudioManagerImpl
import net.dv8tion.jda.internal.requests.WebSocketClient

class VoiceServerUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }
        val guild: Guild =
            getJDA().getGuildById(guildId)
                ?: throw IllegalArgumentException("Attempted to start audio connection with Guild that doesn't exist!")

        getJDA()
            .getDirectAudioController()
            .update(guild, guild.getSelfMember().getVoiceState()!!.getChannel())

        if (content.isNull("endpoint")) {
            // Discord did not provide an endpoint yet,
            // we are to wait until discord has resources to provide an endpoint,
            // which will result in them sending another VOICE_SERVER_UPDATE which we will handle
            // to actually connect to the audio server.
            return null
        }

        val endpoint = content.getString("endpoint")
        val token = content.getString("token")
        val sessionId: String =
            guild.getSelfMember().getVoiceState()!!.getSessionId()
                ?: throw IllegalArgumentException(
                    "Attempted to create audio connection without having a session ID. Did VOICE_STATE_UPDATED fail?",
                )

        val voiceInterceptor = getJDA().getVoiceInterceptor()
        if (voiceInterceptor != null) {
            voiceInterceptor.onVoiceServerUpdate(
                VoiceDispatchInterceptor.VoiceServerUpdate(guild, endpoint, token, sessionId, allContent),
            )
            return null
        }

        val audioManager = getJDA().getAudioManagersView().get(guildId) as AudioManagerImpl?
        if (audioManager == null) {
            WebSocketClient.LOG.debug(
                "Received a VOICE_SERVER_UPDATE but JDA is not currently connected nor attempted to connect " +
                    "to a VoiceChannel. Assuming that this is caused by another client running on this account. " +
                    "Ignoring the event.",
            )
            return null
        }

        MiscUtil.locked(audioManager.CONNECTION_LOCK) {
            // Synchronized to prevent attempts to close while setting up initial objects.
            val target: AudioChannel? = guild.getSelfMember().getVoiceState()!!.getChannel()
            if (target == null) {
                WebSocketClient.LOG.warn("Ignoring VOICE_SERVER_UPDATE for unknown channel")
                return@locked
            }

            val connection = AudioConnection(audioManager, endpoint, sessionId, token, target)
            audioManager.setAudioConnection(connection)
            connection.startConnection()
        }
        return null
    }
}
