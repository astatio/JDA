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

package net.dv8tion.jda.internal.audio

import com.neovisionaries.ws.client.WebSocket
import com.sun.jna.ptr.PointerByReference
import gnu.trove.map.TIntLongMap
import gnu.trove.map.TIntObjectMap
import gnu.trove.map.hash.TIntLongHashMap
import gnu.trove.map.hash.TIntObjectHashMap
import net.dv8tion.jda.api.audio.AudioNatives
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.CombinedAudio
import net.dv8tion.jda.api.audio.OpusPacket
import net.dv8tion.jda.api.audio.SpeakingMode
import net.dv8tion.jda.api.audio.UserAudio
import net.dv8tion.jda.api.audio.factory.IAudioSendSystem
import net.dv8tion.jda.api.audio.factory.IPacketProvider
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.ExceptionEvent
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.managers.AudioManagerImpl
import net.dv8tion.jda.internal.utils.IOUtil
import net.dv8tion.jda.internal.utils.JDALogger
import net.dv8tion.jda.internal.utils.ResizingByteBuffer
import org.slf4j.Logger
import tomp2p.opuswrapper.Opus
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import java.util.Collections
import java.util.EnumSet
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import javax.annotation.Nonnull

open class AudioConnection(
    manager: AudioManagerImpl,
    endpoint: String,
    sessionId: String,
    token: String,
    channel: AudioChannel,
) {
    @Volatile
    @JvmField
    internal var udpSocket: DatagramSocket? = null

    private val ssrcMap: TIntLongMap = TIntLongHashMap()
    private val opusDecoders: TIntObjectMap<Decoder> = TIntObjectHashMap()
    private val combinedQueue: HashMap<User, Queue<AudioData>> = HashMap()
    private val threadIdentifier: String
    private val webSocket: AudioWebSocket
    private val api: JDAImpl

    @JvmField
    internal val readyLock: ReentrantLock = ReentrantLock()

    @JvmField
    internal val readyCondvar: Condition = readyLock.newCondition()

    private var channel: AudioChannel
    private var opusEncoder: PointerByReference? = null
    private var combinedAudioExecutor: ScheduledExecutorService? = null
    private var sendSystem: IAudioSendSystem? = null
    private var receiveThread: Thread? = null
    private var queueTimeout: Long = 0
    private var shutdown = false

    @Volatile
    private var sendHandler: AudioSendHandler? = null

    @Volatile
    private var receiveHandler: AudioReceiveHandler? = null

    @Suppress("unused") // it is used by a nested class!
    @Volatile
    private var couldReceive = false

    @Volatile
    private var speakingMode: Int = SpeakingMode.VOICE.raw

    init {
        api = channel.jda as JDAImpl
        this.channel = channel
        threadIdentifier = api.identifierString + " AudioConnection Guild: " + channel.guild.id

        webSocket =
            AudioWebSocket(
                this,
                manager.getListenerProxy(),
                endpoint,
                channel.guild,
                sessionId,
                token,
                manager.isAutoReconnect,
            )

        val daveSession =
            manager.jda
                .audioModuleConfig
                .daveSessionFactory
                .createDaveSession(webSocket, manager.jda.selfUser.idLong, channel.idLong)
        webSocket.setDaveSession(daveSession)
    }

    // Used by AudioManagerImpl

    fun startConnection() {
        webSocket.startConnection()
    }

    fun getConnectionStatus(): ConnectionStatus = webSocket.getConnectionStatus()

    fun setAutoReconnect(shouldReconnect: Boolean) {
        webSocket.setAutoReconnect(shouldReconnect)
    }

    fun setSendingHandler(handler: AudioSendHandler?) {
        sendHandler = handler
        if (webSocket.isReady()) {
            setupSendSystem()
        }
    }

    fun setReceivingHandler(handler: AudioReceiveHandler?) {
        receiveHandler = handler
        if (webSocket.isReady()) {
            setupReceiveSystem()
        }
    }

    fun setSpeakingMode(mode: EnumSet<SpeakingMode>) {
        val raw = SpeakingMode.getRaw(mode)
        if (raw != speakingMode && webSocket.isReady()) {
            setSpeaking(raw)
        }
        speakingMode = raw
    }

    fun setQueueTimeout(queueTimeout: Long) {
        this.queueTimeout = queueTimeout
    }

    fun getChannel(): AudioChannel = channel

    fun setChannel(channel: AudioChannel) {
        this.channel = channel
    }

    fun getJDA(): JDAImpl = api

    fun getGuild(): Guild = channel.guild

    fun close(closeStatus: ConnectionStatus) {
        shutdown()
        webSocket.close(closeStatus)
    }

    @Synchronized
    fun shutdown() {
        shutdown = true
        if (sendSystem != null) {
            sendSystem!!.shutdown()
            sendSystem = null
        }
        if (receiveThread != null) {
            receiveThread!!.interrupt()
            receiveThread = null
        }
        if (combinedAudioExecutor != null) {
            combinedAudioExecutor!!.shutdownNow()
            combinedAudioExecutor = null
        }
        if (opusEncoder != null) {
            Opus.INSTANCE.opus_encoder_destroy(opusEncoder)
            opusEncoder = null
        }

        opusDecoders.valueCollection().forEach(Decoder::close)
        opusDecoders.clear()

        MiscUtil.locked(readyLock, Runnable { readyCondvar.signalAll() })
    }

    fun getWebSocket(): WebSocket = webSocket.socket!!

    // Used by AudioWebSocket

    @JvmName("prepareReady")
    internal fun prepareReady() {
        val readyThread =
            Thread {
                api.setContext()

                val ready =
                    MiscUtil.locked(
                        readyLock,
                        Supplier {
                            val timeout = getGuild().audioManager.connectTimeout
                            while (!webSocket.isReady()) {
                                try {
                                    val activated = readyCondvar.await(timeout, TimeUnit.MILLISECONDS)
                                    if (!activated) {
                                        webSocket.close(ConnectionStatus.ERROR_CONNECTION_TIMEOUT)
                                        shutdown = true
                                    }
                                    if (shutdown) {
                                        return@Supplier false
                                    }
                                } catch (e: InterruptedException) {
                                    LOG.error("AudioConnection ready thread got interrupted while sleeping", e)
                                    return@Supplier false
                                }
                            }

                            true
                        },
                    )

                if (ready) {
                    setupSendSystem()
                    setupReceiveSystem()
                }
            }
        readyThread.setUncaughtExceptionHandler { _, throwable ->
            LOG.error("Uncaught exception in Audio ready-thread", throwable)
            val api = api
            api.handleEvent(ExceptionEvent(api, throwable, true))
        }
        readyThread.isDaemon = true
        readyThread.name = "$threadIdentifier Ready Thread"
        readyThread.start()
    }

    @JvmName("removeUserSSRC")
    internal fun removeUserSSRC(userId: Long) {
        val ssrcRef = AtomicInteger(0)
        val modified =
            ssrcMap.retainEntries { ssrc, id ->
                val isEntry = id == userId
                if (isEntry) {
                    ssrcRef.set(ssrc)
                }
                // if isEntry == true we don't want to retain it
                !isEntry
            }
        if (!modified) {
            return
        }
        val decoder = opusDecoders.remove(ssrcRef.get())
        if (decoder != null) { // cleanup decoder
            decoder.close()
        }
    }

    @JvmName("updateUserSSRC")
    internal fun updateUserSSRC(
        ssrc: Int,
        userId: Long,
    ) {
        if (ssrcMap.containsKey(ssrc)) {
            val previousId = ssrcMap.get(ssrc)
            if (previousId != userId) {
                // Different User already existed with this ssrc. What should we do? Just replace?
                // Probably should nuke the old opusDecoder.
                // Log for now and see if any user report the error.
                LOG.error(
                    "Yeah.. So.. JDA received a UserSSRC update for an ssrc that already had a User set. Inform" +
                        " devs.\n" +
                        "ChannelId: {} SSRC: {} oldId: {} newId: {}",
                    channel.id,
                    ssrc,
                    previousId,
                    userId,
                )
            }
        } else {
            ssrcMap.put(ssrc, userId)

            // Only create a decoder if we are actively handling received audio.
            if (receiveThread != null && AudioNatives.ensureOpus()) {
                opusDecoders.put(ssrc, Decoder(ssrc))
            }
        }
    }

    // Internals

    private fun isUdpSocketOpen(): Boolean = udpSocket?.isClosed == false

    @Synchronized
    private fun setupSendSystem() {
        if (isUdpSocketOpen() && sendHandler != null && sendSystem == null) {
            setSpeaking(speakingMode)
            val factory = api.audioSendFactory
            sendSystem = factory.createSendSystem(PacketProvider())
            sendSystem!!.setContextMap(api.contextMap)
            sendSystem!!.start()
        } else if (sendHandler == null && sendSystem != null) {
            sendSystem!!.shutdown()
            sendSystem = null

            if (opusEncoder != null) {
                Opus.INSTANCE.opus_encoder_destroy(opusEncoder)
                opusEncoder = null
            }
        }
    }

    @Synchronized
    private fun setupReceiveSystem() {
        if (isUdpSocketOpen() && receiveHandler != null && receiveThread == null) {
            setupReceiveThread()
        } else if (receiveHandler == null && receiveThread != null) {
            receiveThread!!.interrupt()
            receiveThread = null

            if (combinedAudioExecutor != null) {
                combinedAudioExecutor!!.shutdownNow()
                combinedAudioExecutor = null
            }

            opusDecoders.valueCollection().forEach(Decoder::close)
            opusDecoders.clear()
        } else if (receiveHandler != null && !receiveHandler!!.canReceiveCombined() && combinedAudioExecutor != null) {
            combinedAudioExecutor!!.shutdownNow()
            combinedAudioExecutor = null
        }
    }

    @Suppress("LoopWithTooManyJumpStatements", "TooGenericExceptionCaught")
    @Synchronized
    private fun setupReceiveThread() {
        if (receiveThread == null) {
            receiveThread =
                Thread {
                    api.setContext()
                    try {
                        udpSocket!!.soTimeout = UDP_SO_TIMEOUT_MS
                    } catch (e: SocketException) {
                        LOG.error("Couldn't set SO_TIMEOUT for UDP socket", e)
                    }

                    val buffer = ByteArray(RECEIVE_PACKET_BUFFER_SIZE)
                    val decryptBuffer = ResizingByteBuffer(ByteBuffer.allocateDirect(DECRYPT_BUFFER_SIZE))
                    while (!udpSocket!!.isClosed && !Thread.currentThread().isInterrupted) {
                        val receivedPacket = DatagramPacket(buffer, buffer.size)
                        try {
                            udpSocket!!.receive(receivedPacket)

                            val shouldDecode =
                                receiveHandler != null &&
                                    (receiveHandler!!.canReceiveUser() || receiveHandler!!.canReceiveCombined())
                            val canReceive =
                                receiveHandler != null &&
                                    (
                                        receiveHandler!!.canReceiveUser() ||
                                            receiveHandler!!.canReceiveCombined() ||
                                            receiveHandler!!.canReceiveEncoded()
                                    )
                            if (canReceive && webSocket.getSecretKey() != null) {
                                couldReceive = true

                                val audioPacket = AudioPacket(receivedPacket)
                                val ssrc = audioPacket.getSSRC()
                                val userId = if (ssrcMap.containsKey(ssrc)) ssrcMap.get(ssrc) else 0L
                                if (userId == 0L) {
                                    continue
                                }

                                val decryptedPacket =
                                    audioPacket.asDecryptAudioPacket(webSocket.crypto!!, userId, decryptBuffer)
                                if (decryptedPacket == null) {
                                    continue
                                }

                                var decoder = opusDecoders.get(ssrc)
                                if (decoder == null) {
                                    if (AudioNatives.ensureOpus()) {
                                        decoder = Decoder(ssrc)
                                        opusDecoders.put(ssrc, decoder)
                                    } else if (!receiveHandler!!.canReceiveEncoded()) {
                                        LOG.error("Unable to decode audio due to missing opus binaries!")
                                        break
                                    }
                                }
                                val opusPacket = OpusPacket(decryptedPacket, userId, decoder)
                                if (receiveHandler!!.canReceiveEncoded()) {
                                    receiveHandler!!.handleEncodedAudio(opusPacket)
                                }
                                if (!shouldDecode || !opusPacket.canDecode()) {
                                    continue
                                }

                                val user = api.getUserById(userId)
                                if (user == null) {
                                    LOG.warn(
                                        "Received audio data with a known SSRC, but the userId associate with the SSRC" +
                                            " is unknown to JDA! You likely need to cache members.",
                                    )
                                    continue
                                }
                                val decodedAudio = opusPacket.decode()
                                // If decodedAudio is null, then the Opus decode failed,
                                // so throw away the packet.
                                if (decodedAudio == null) {
                                    // decoder error logged in method
                                    continue
                                }
                                if (receiveHandler!!.canReceiveUser()) {
                                    receiveHandler!!.handleUserAudio(UserAudio(user, decodedAudio))
                                }
                                if (receiveHandler!!.canReceiveCombined() &&
                                    receiveHandler!!.includeUserInCombinedAudio(user)
                                ) {
                                    var queue = combinedQueue[user]
                                    if (queue == null) {
                                        queue = ConcurrentLinkedQueue()
                                        combinedQueue[user] = queue
                                    }
                                    queue.add(AudioData(decodedAudio))
                                }
                            } else {
                                couldReceive = false
                            }
                        } catch (_: SocketTimeoutException) {
                            // Ignore. We set a low timeout so that we wont block forever so we can
                            // properly shutdown the loop.
                        } catch (_: SocketException) {
                            // The socket was closed while we were listening for the next packet.
                            // This is expected. Ignore the exception.
                            // The thread will exit during the next while
                            // iteration because the udpSocket.isClosed() will return true.
                        } catch (e: Exception) {
                            LOG.error("There was some random exception while waiting for udp packets", e)
                        }
                    }
                }
            receiveThread!!.setUncaughtExceptionHandler { _, throwable ->
                LOG.error("There was some uncaught exception in the audio receive thread", throwable)
                val api = api
                api.handleEvent(ExceptionEvent(api, throwable, true))
            }
            receiveThread!!.isDaemon = true
            receiveThread!!.name = "$threadIdentifier Receiving Thread"
            receiveThread!!.start()
        }

        if (receiveHandler!!.canReceiveCombined()) {
            setupCombinedExecutor()
        }
    }

    @Suppress("LoopWithTooManyJumpStatements", "TooGenericExceptionCaught")
    @Synchronized
    private fun setupCombinedExecutor() {
        if (combinedAudioExecutor == null) {
            combinedAudioExecutor =
                Executors.newSingleThreadScheduledExecutor { task ->
                    val t = Thread(task, "$threadIdentifier Combined Thread")
                    t.isDaemon = true
                    t.setUncaughtExceptionHandler { _, throwable ->
                        LOG.error(
                            "I have no idea how, but there was an uncaught exception in the combinedAudioExecutor",
                            throwable,
                        )
                        val api = api
                        api.handleEvent(ExceptionEvent(api, throwable, true))
                    }
                    t
                }
            combinedAudioExecutor!!.scheduleAtFixedRate(
                {
                    api.setContext()
                    try {
                        val users: MutableList<User> = ArrayList()
                        val audioParts: MutableList<ShortArray> = ArrayList()
                        if (receiveHandler != null && receiveHandler!!.canReceiveCombined()) {
                            val currentTime = System.currentTimeMillis()
                            for ((user, queue) in combinedQueue) {
                                if (queue.isEmpty()) {
                                    continue
                                }

                                var audioData = queue.poll()
                                // Make sure the audio packet is younger than 100ms
                                while (audioData != null && currentTime - audioData.time > queueTimeout) {
                                    audioData = queue.poll()
                                }

                                // If none of the audio packets were younger than 100ms, then
                                // there is nothing to add.
                                if (audioData == null) {
                                    continue
                                }
                                users.add(user)
                                audioParts.add(audioData.data)
                            }

                            if (audioParts.isNotEmpty()) {
                                val audioLength = audioParts.maxOf { it.size }
                                val mix = ShortArray(COMBINED_AUDIO_SAMPLES) // 960 PCM samples for each channel
                                var sample: Int
                                for (i in 0 until audioLength) {
                                    sample = 0
                                    val iterator = audioParts.iterator()
                                    while (iterator.hasNext()) {
                                        val audio = iterator.next()
                                        if (i < audio.size) {
                                            sample += audio[i]
                                        } else {
                                            iterator.remove()
                                        }
                                    }
                                    if (sample > Short.MAX_VALUE) {
                                        mix[i] = Short.MAX_VALUE
                                    } else if (sample < Short.MIN_VALUE) {
                                        mix[i] = Short.MIN_VALUE
                                    } else {
                                        mix[i] = sample.toShort()
                                    }
                                }
                                receiveHandler!!.handleCombinedAudio(CombinedAudio(users, mix))
                            } else {
                                // No audio to mix, provide 20 MS of silence.
                                // (960 PCM samples for each channel)
                                receiveHandler!!.handleCombinedAudio(
                                    CombinedAudio(Collections.emptyList(), ShortArray(COMBINED_AUDIO_SAMPLES)),
                                )
                            }
                        }
                    } catch (e: Exception) {
                        LOG.error("There was some unexpected exception in the combinedAudioExecutor!", e)
                    }
                },
                0,
                COMBINED_AUDIO_INTERVAL_MS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun encodeToOpus(rawAudio: ByteBuffer): ByteBuffer? {
        val nonEncodedBuffer = ShortBuffer.allocate(rawAudio.remaining() / 2)
        val encoded = ByteBuffer.allocateDirect(4096)
        var i = rawAudio.position()
        while (i < rawAudio.limit()) {
            val firstByte = 0x000000FF and rawAudio.get(i).toInt() // Promotes to int and handles the fact that it was unsigned.
            val secondByte = 0x000000FF and rawAudio.get(i + 1).toInt()

            // Combines the 2 bytes into a short. Opus deals with unsigned shorts, not bytes.
            val toShort = ((firstByte shl 8) or secondByte).toShort()

            nonEncodedBuffer.put(toShort)
            i += 2
        }
        (nonEncodedBuffer as Buffer).flip()

        val result =
            Opus.INSTANCE.opus_encode(
                opusEncoder,
                nonEncodedBuffer,
                OpusPacket.OPUS_FRAME_SIZE,
                encoded,
                encoded.capacity(),
            )
        if (result <= 0) {
            LOG.error("Received error code from opus_encode(...): {}", result)
            return null
        }

        (encoded as Buffer).position(0).limit(result)
        return encoded
    }

    private fun setSpeaking(raw: Int) {
        val obj =
            DataObject
                .empty()
                .put("speaking", raw)
                .put("ssrc", webSocket.getSSRC())
                .put("delay", 0)
        webSocket.send(VoiceCode.USER_SPEAKING_UPDATE, obj)
    }

    @Deprecated("Deprecated in Java 9 because the finalization system is being changed/removed")
    @Suppress("deprecation")
    protected fun finalize() {
        shutdown()
    }

    private inner class PacketProvider : IPacketProvider {
        private var seq: Char = 0.toChar() // Sequence of audio packets. Used to determine the order of the packets.
        private var timestamp: Int = 0 // Used to sync up our packets within the same timeframe of other people talking.
        private val buffer = ResizingByteBuffer(ByteBuffer.allocateDirect(2048))
        private var temporaryDirectBuffer: ByteBuffer? = null
        private var datagramBuffer: ByteBuffer? = null

        @Nonnull
        override fun getIdentifier(): String = threadIdentifier

        @Nonnull
        override fun getConnectedChannel(): AudioChannel = channel

        @Nonnull
        override fun getUdpSocket(): DatagramSocket = this@AudioConnection.udpSocket!!

        @Nonnull
        override fun getSocketAddress(): InetSocketAddress = webSocket.getAddress()

        override fun getNextPacket(unused: Boolean): DatagramPacket? {
            val buffer = getNextPacketRaw(unused)
            return if (buffer == null) null else getDatagramPacket(buffer)
        }

        @Suppress("NestedBlockDepth", "TooGenericExceptionCaught")
        override fun getNextPacketRaw(unused: Boolean): ByteBuffer? {
            try {
                if (sendHandler != null && sendHandler!!.canProvide()) {
                    var rawAudio = sendHandler!!.provide20MsAudio()
                    if (rawAudio != null && rawAudio.hasRemaining()) {
                        if (!sendHandler!!.isOpus) {
                            rawAudio = encodeAudio(rawAudio)
                            if (rawAudio == null) {
                                return buffer.buffer()
                            }
                        }

                        loadEncryptedPacketData(ensureDirect(rawAudio))

                        seq = seq + 1
                    }
                }
            } catch (e: Exception) {
                LOG.error("There was an error while getting next audio packet", e)
            }

            timestamp += OpusPacket.OPUS_FRAME_SIZE
            return buffer.buffer()
        }

        private fun ensureDirect(buffer: ByteBuffer): ByteBuffer {
            if (buffer.isDirect) {
                return buffer
            }

            if (temporaryDirectBuffer == null) {
                temporaryDirectBuffer = ByteBuffer.allocateDirect(buffer.remaining())
            }

            temporaryDirectBuffer = IOUtil.replace(temporaryDirectBuffer!!, buffer)
            return temporaryDirectBuffer!!
        }

        @Suppress("ReturnCount")
        private fun encodeAudio(rawAudio: ByteBuffer): ByteBuffer? {
            if (opusEncoder == null) {
                if (!AudioNatives.ensureOpus()) {
                    if (!printedError) {
                        LOG.error("Unable to process PCM audio without opus binaries!")
                    }
                    printedError = true
                    return null
                }
                val error = IntBuffer.allocate(1)
                opusEncoder =
                    Opus.INSTANCE.opus_encoder_create(
                        OpusPacket.OPUS_SAMPLE_RATE,
                        OpusPacket.OPUS_CHANNEL_COUNT,
                        Opus.OPUS_APPLICATION_AUDIO,
                        error,
                    )
                if (error.get() != Opus.OPUS_OK && opusEncoder == null) {
                    LOG.error("Received error status from opus_encoder_create(...): {}", error.get())
                    return null
                }
            }
            return encodeToOpus(rawAudio)
        }

        private fun getDatagramPacket(b: ByteBuffer): DatagramPacket {
            if (datagramBuffer == null) {
                datagramBuffer = ByteBuffer.allocate(b.remaining())
            }

            datagramBuffer = IOUtil.replace(datagramBuffer!!, b)

            val data = datagramBuffer!!.array()
            val offset = datagramBuffer!!.arrayOffset() + datagramBuffer!!.position()
            val length = datagramBuffer!!.remaining()
            return DatagramPacket(data, offset, length, webSocket.getAddress())
        }

        private fun loadEncryptedPacketData(rawAudio: ByteBuffer) {
            val packet = AudioPacket(seq, timestamp, webSocket.getSSRC(), rawAudio)
            packet.asEncryptedPacket(webSocket.crypto!!, buffer)
        }

        override fun onConnectionError(
            @Nonnull status: ConnectionStatus,
        ) {
            LOG.warn("IAudioSendSystem reported a connection error of: {}", status)
            LOG.warn("Shutting down AudioConnection.")
            webSocket.close(status)
        }

        override fun onConnectionLost() {
            LOG.warn("Closing AudioConnection due to inability to send audio packets.")
            LOG.warn(
                "Cannot send audio packet because JDA cannot navigate the route to Discord.\n" +
                    "Are you sure you have internet connection? It is likely that you've lost connection.",
            )
            webSocket.close(ConnectionStatus.ERROR_LOST_CONNECTION)
        }
    }

    private class AudioData(
        @JvmField internal val data: ShortArray,
    ) {
        @JvmField
        internal val time: Long = System.currentTimeMillis()
    }

    companion object {
        @JvmField
        val LOG: Logger = JDALogger.getLog(AudioConnection::class.java)

        @Suppress("MagicNumber") // Public constant kept verbatim from the Java original.
        const val MAX_UINT_32: Long = 4294967295L

        private const val UDP_SO_TIMEOUT_MS: Int = 1000
        private const val RECEIVE_PACKET_BUFFER_SIZE: Int = 4096
        private const val DECRYPT_BUFFER_SIZE: Int = 1024
        private const val COMBINED_AUDIO_SAMPLES: Int = 1920
        private const val COMBINED_AUDIO_INTERVAL_MS: Long = 20

        private var printedError: Boolean = false
    }
}
