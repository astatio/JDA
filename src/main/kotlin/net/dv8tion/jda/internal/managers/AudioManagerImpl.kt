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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.SpeakingMode
import net.dv8tion.jda.api.audio.hooks.ConnectionListener
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.audio.hooks.ListenerProxy
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.audio.AudioConnection
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.PermissionUtil
import java.util.EnumSet
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.Nonnull

open class AudioManagerImpl(
    @JvmField protected val guild: GuildImpl,
) : AudioManager {
    @Suppress("ktlint:standard:property-naming") // Public field name is part of the API shape
    @JvmField
    val CONNECTION_LOCK: ReentrantLock = ReentrantLock()

    @JvmField
    protected val connectionListener: ListenerProxy = ListenerProxy()

    @JvmField
    protected var audioConnection: AudioConnection? = null

    @JvmField
    protected var speakingModes: EnumSet<SpeakingMode> = EnumSet.of(SpeakingMode.VOICE)

    @JvmField
    protected var sendHandler: AudioSendHandler? = null

    @JvmField
    protected var receiveHandler: AudioReceiveHandler? = null

    @JvmField
    protected var queueTimeout: Long = 100

    @JvmField
    protected var shouldReconnect: Boolean = true

    @JvmField
    protected var selfMuted: Boolean = false

    @JvmField
    protected var selfDeafened: Boolean = false

    @JvmField
    protected var timeout: Long = AudioManager.DEFAULT_CONNECTION_TIMEOUT

    fun getAudioConnection(): AudioConnection? = audioConnection

    override fun openAudioConnection(
        @Nonnull channel: AudioChannel,
    ) {
        Checks.notNull(channel, "Provided AudioChannel")

        if (getGuild() != channel.guild) {
            throw IllegalArgumentException(
                "The provided AudioChannel is not a part of the Guild that this AudioManager handles." +
                    "Please provide a AudioChannel from the proper Guild",
            )
        }
        val self = getGuild().selfMember
        // if (!self.hasPermission(channel, Permission.VOICE_CONNECT))
        //    throw new InsufficientPermissionException(Permission.VOICE_CONNECT);

        // If we are already connected to this AudioChannel, then do nothing.
        if (audioConnection != null && channel == audioConnection!!.getChannel()) {
            return
        }

        checkChannel(channel, self)

        getJDA().directAudioController.connect(channel)
        if (audioConnection != null) {
            audioConnection!!.setChannel(channel)
        }
    }

    private fun checkChannel(
        channel: AudioChannel,
        self: Member,
    ) {
        val perms = Permission.getPermissions(PermissionUtil.getEffectivePermission(channel, self))
        if (!perms.contains(Permission.VOICE_CONNECT)) {
            throw InsufficientPermissionException(channel, Permission.VOICE_CONNECT)
        }

        // if userLimit is 0 if no limit is set!
        val userLimit = if (channel is VoiceChannel) channel.userLimit else 0
        if (userLimit > 0 && !perms.contains(Permission.ADMINISTRATOR)) {
            // Check if we can actually join this channel
            // - If there is a userlimit
            // - If that userlimit is reached
            // - If we don't have voice move others permissions
            // VOICE_MOVE_OTHERS allows access because you would be able to move people out to
            // open up a slot anyway
            if (userLimit <= channel.members.size && !perms.contains(Permission.VOICE_MOVE_OTHERS)) {
                throw InsufficientPermissionException(
                    channel,
                    Permission.VOICE_MOVE_OTHERS,
                    "Unable to connect to AudioChannel due to userlimit! Requires permission VOICE_MOVE_OTHERS to bypass",
                )
            }
        }
    }

    override fun closeAudioConnection() {
        getJDA().audioLifeCyclePool.execute {
            getJDA().setContext()
            closeAudioConnection(ConnectionStatus.NOT_CONNECTED)
        }
    }

    fun closeAudioConnection(reason: ConnectionStatus) {
        MiscUtil.locked(
            CONNECTION_LOCK,
            Runnable {
                if (audioConnection != null) {
                    audioConnection!!.close(reason)
                } else if (reason != ConnectionStatus.DISCONNECTED_REMOVED_FROM_GUILD) {
                    getJDA().directAudioController.disconnect(getGuild())
                }
                audioConnection = null
            },
        )
    }

    override fun setSpeakingMode(
        @Nonnull mode: MutableCollection<SpeakingMode>,
    ) {
        Checks.notEmpty(mode, "Speaking Mode")
        speakingModes = EnumSet.copyOf(mode)
        if (audioConnection != null) {
            audioConnection!!.setSpeakingMode(speakingModes)
        }
    }

    @Nonnull
    override fun getSpeakingMode(): EnumSet<SpeakingMode> = EnumSet.copyOf(speakingModes)

    @Nonnull
    override fun getJDA(): JDAImpl = getGuild().jda

    @Nonnull
    override fun getGuild(): GuildImpl = guild

    override fun getConnectedChannel(): AudioChannelUnion? =
        if (audioConnection == null) null else audioConnection!!.getChannel() as AudioChannelUnion

    override fun isConnected(): Boolean = audioConnection != null

    override fun setConnectTimeout(timeout: Long) {
        this.timeout = timeout
    }

    override fun getConnectTimeout(): Long = timeout

    override fun setSendingHandler(handler: AudioSendHandler?) {
        sendHandler = handler
        if (audioConnection != null) {
            audioConnection!!.setSendingHandler(handler)
        }
    }

    override fun getSendingHandler(): AudioSendHandler? = sendHandler

    override fun setReceivingHandler(handler: AudioReceiveHandler?) {
        receiveHandler = handler
        if (audioConnection != null) {
            audioConnection!!.setReceivingHandler(handler)
        }
    }

    override fun getReceivingHandler(): AudioReceiveHandler? = receiveHandler

    override fun setConnectionListener(listener: ConnectionListener?) {
        connectionListener.setListener(listener)
    }

    override fun getConnectionListener(): ConnectionListener? = connectionListener.getListener()

    @Nonnull
    override fun getConnectionStatus(): ConnectionStatus = audioConnection?.getConnectionStatus() ?: ConnectionStatus.NOT_CONNECTED

    override fun setAutoReconnect(shouldReconnect: Boolean) {
        this.shouldReconnect = shouldReconnect
        if (audioConnection != null) {
            audioConnection!!.setAutoReconnect(shouldReconnect)
        }
    }

    override fun isAutoReconnect(): Boolean = shouldReconnect

    override fun setSelfMuted(muted: Boolean) {
        if (selfMuted != muted) {
            selfMuted = muted
            updateVoiceState()
        }
    }

    override fun isSelfMuted(): Boolean = selfMuted

    override fun setSelfDeafened(deafened: Boolean) {
        if (selfDeafened != deafened) {
            selfDeafened = deafened
            updateVoiceState()
        }
    }

    override fun isSelfDeafened(): Boolean = selfDeafened

    fun getListenerProxy(): ConnectionListener = connectionListener

    fun setAudioConnection(audioConnection: AudioConnection?) {
        if (audioConnection == null) {
            this.audioConnection = null
            return
        }

        // This will set the audioConnection to null,
        // which we then immediately override with the new connection
        if (this.audioConnection != null) {
            closeAudioConnection(ConnectionStatus.AUDIO_REGION_CHANGE)
        }
        this.audioConnection = audioConnection
        audioConnection.setSendingHandler(sendHandler)
        audioConnection.setReceivingHandler(receiveHandler)
        audioConnection.setQueueTimeout(queueTimeout)
        audioConnection.setSpeakingMode(speakingModes)
    }

    fun setConnectedChannel(channel: AudioChannel) {
        if (audioConnection != null) {
            audioConnection!!.setChannel(channel)
        }
    }

    fun setQueueTimeout(queueTimeout: Long) {
        this.queueTimeout = queueTimeout
        if (audioConnection != null) {
            audioConnection!!.setQueueTimeout(queueTimeout)
        }
    }

    protected fun updateVoiceState() {
        val channel = getConnectedChannel()
        if (channel != null) {
            // This is technically equivalent to an audio open/move packet.
            getJDA().directAudioController.connect(channel)
        }
    }

    // If this was in JDK9 we would be using java.lang.ref.Cleaner instead!
    @Deprecated("Deprecated in Java 9 because the finalization system is being changed/removed")
    @Suppress("deprecation")
    protected fun finalize() {
        if (audioConnection != null) {
            AudioManager.LOG.warn(
                "Finalized AudioManager with active audio connection. GuildId: {}",
                getGuild().id,
            )
            audioConnection!!.close(ConnectionStatus.DISCONNECTED_REMOVED_FROM_GUILD)
        }
        audioConnection = null
    }
}
