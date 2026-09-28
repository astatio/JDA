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

package net.dv8tion.jda.internal.requests

import gnu.trove.map.TLongObjectMap
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.audio.ConnectionRequest
import net.dv8tion.jda.internal.audio.ConnectionStage
import org.slf4j.Logger
import java.util.Queue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

// Helper class delegated to WebSocketClient
class WebSocketSendingThread(
    private val client: WebSocketClient,
) : Runnable {
    private val api: JDAImpl = client.api
    private val queueLock: ReentrantLock = client.queueLock
    private val chunkQueue: Queue<DataObject> = client.chunkSyncQueue
    private val ratelimitQueue: Queue<DataObject> = client.ratelimitQueue
    private val queuedAudioConnections: TLongObjectMap<ConnectionRequest> = client.queuedAudioConnections
    private val executor: ScheduledExecutorService = client.executor
    private var handle: Future<*>? = null

    private var needRateLimit = false
    private var attemptedToSend = false
    private var shutdown = false

    fun shutdown() {
        shutdown = true
        handle?.cancel(false)
    }

    fun start() {
        shutdown = false
        handle = executor.submit(this)
    }

    private fun scheduleIdle() {
        if (shutdown) {
            return
        }
        handle = executor.schedule(this, IDLE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun scheduleSentMessage() {
        if (shutdown) {
            return
        }
        handle = executor.schedule(this, SENT_MESSAGE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun scheduleRateLimit() {
        if (shutdown) {
            return
        }
        handle = executor.schedule(this, 1, TimeUnit.MINUTES)
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught") // mirrors the Java control flow
    override fun run() {
        // Make sure that we don't send any packets before sending auth info.
        if (!client.sentAuthInfo) {
            scheduleIdle()
            return
        }

        var audioRequest: ConnectionRequest? = null
        var chunkRequest: DataObject? = null

        var hasLock = false

        try {
            api.setContext()
            attemptedToSend = false
            needRateLimit = false
            // We do this outside of the lock because otherwise we could potentially deadlock here
            audioRequest = client.getNextAudioConnectRequest()

            hasLock = queueLock.tryLock() || queueLock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS)
            if (!hasLock) {
                scheduleNext()
                return
            }

            chunkRequest = chunkQueue.peek()
            if (chunkRequest != null) {
                handleChunkSync(chunkRequest)
            } else if (audioRequest != null) {
                handleAudioRequest(audioRequest)
            } else {
                handleNormalRequest()
            }
        } catch (ignored: InterruptedException) {
            LOG.debug("Main WS send thread interrupted. Most likely JDA is disconnecting the websocket.")
            return
        } catch (ex: Throwable) {
            // Log error
            LOG.error("Encountered error in gateway worker", ex)

            if (!attemptedToSend) {
                // Try to remove the failed request
                if (chunkRequest != null) {
                    client.chunkSyncQueue.remove(chunkRequest)
                } else if (audioRequest != null) {
                    client.removeAudioConnection(audioRequest.getGuildIdLong())
                }
            }

            // Rethrow if error to kill thread
            if (ex is Error) {
                throw ex
            }
        } finally {
            if (hasLock) {
                queueLock.unlock()
            }
        }

        scheduleNext()
    }

    private fun scheduleNext() {
        try {
            if (needRateLimit) {
                scheduleRateLimit()
            } else if (!attemptedToSend) {
                scheduleIdle()
            } else {
                scheduleSentMessage()
            }
        } catch (ex: RejectedExecutionException) {
            if (api.getStatus() == JDA.Status.SHUTTING_DOWN || api.getStatus() == JDA.Status.SHUTDOWN) {
                LOG.debug("Rejected task after shutdown", ex)
            } else {
                LOG.error("Was unable to schedule next packet due to rejected execution by threadpool", ex)
            }
        }
    }

    private fun handleChunkSync(chunkOrSyncRequest: DataObject) {
        LOG.debug("Sending chunk/sync request {}", chunkOrSyncRequest)
        val success =
            send(
                DataObject.empty().put("op", WebSocketCode.MEMBER_CHUNK_REQUEST).put("d", chunkOrSyncRequest),
            )

        if (success) {
            chunkQueue.remove()
        }
    }

    private fun handleAudioRequest(audioRequest: ConnectionRequest) {
        val channelId = audioRequest.getChannelId()
        val guildId = audioRequest.getGuildIdLong()
        val guild: Guild =
            api.getGuildById(guildId) ?: run {
                LOG.debug("Discarding voice request due to null guild {}", guildId)
                // race condition on guild delete, avoid NPE on DISCONNECT requests
                queuedAudioConnections.remove(guildId)
                return
            }
        val stage: ConnectionStage = audioRequest.getStage()
        val audioManager = guild.audioManager
        val packet: DataObject =
            when (stage) {
                ConnectionStage.RECONNECT,
                ConnectionStage.DISCONNECT,
                -> newVoiceClose(guildId)
                ConnectionStage.CONNECT -> newVoiceOpen(audioManager, channelId, guild.getIdLong())
            }
        LOG.debug("Sending voice request {}", packet)
        if (send(packet)) {
            // If we didn't get RateLimited, Next request attempt will be 10 seconds from now
            // we remove it in VoiceStateUpdateHandler once we hear that it has updated our status
            // in 10 seconds we will attempt again in case we did not receive an update
            audioRequest.setNextAttemptEpoch(System.currentTimeMillis() + RETRY_ATTEMPT_DELAY_MS)
            // If we are already in the correct state according to voice state
            // we will not receive a VOICE_STATE_UPDATE that would remove it
            // thus we update it here
            val voiceState: GuildVoiceState = guild.selfMember.voiceState!!
            client.updateAudioConnection0(guild.getIdLong(), voiceState.channel)
        }
    }

    private fun handleNormalRequest() {
        val message = ratelimitQueue.peek()
        if (message != null) {
            LOG.debug("Sending normal message {}", message)
            if (send(message)) {
                ratelimitQueue.remove()
            }
        }
    }

    // returns true if send was successful
    private fun send(request: DataObject): Boolean {
        needRateLimit = !client.send(request, false)
        attemptedToSend = true
        return !needRateLimit
    }

    private fun newVoiceClose(guildId: Long): DataObject =
        DataObject
            .empty()
            .put("op", WebSocketCode.VOICE_STATE)
            .put(
                "d",
                DataObject
                    .empty()
                    .put("guild_id", java.lang.Long.toUnsignedString(guildId))
                    .putNull("channel_id")
                    .put("self_mute", false)
                    .put("self_deaf", false),
            )

    private fun newVoiceOpen(
        manager: AudioManager,
        channel: Long,
        guild: Long,
    ): DataObject =
        DataObject
            .empty()
            .put("op", WebSocketCode.VOICE_STATE)
            .put(
                "d",
                DataObject
                    .empty()
                    .put("guild_id", guild)
                    .put("channel_id", channel)
                    .put("self_mute", manager.isSelfMuted)
                    .put("self_deaf", manager.isSelfDeafened),
            )

    companion object {
        private val LOG: Logger = WebSocketClient.LOG
        private const val IDLE_DELAY_MS = 500L
        private const val LOCK_WAIT_SECONDS = 10L
        private const val SENT_MESSAGE_DELAY_MS = 10L
        private const val RETRY_ATTEMPT_DELAY_MS = 10000L
    }
}
