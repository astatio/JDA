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

import com.neovisionaries.ws.client.ThreadType
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import net.dv8tion.jda.api.JDAInfo
import net.dv8tion.jda.api.audio.SpeakingMode
import net.dv8tion.jda.api.audio.dave.DaveProtocolCallbacks
import net.dv8tion.jda.api.audio.dave.DaveSession
import net.dv8tion.jda.api.audio.hooks.ConnectionListener
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.ExceptionEvent
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.managers.AudioManagerImpl
import net.dv8tion.jda.internal.utils.IOUtil
import net.dv8tion.jda.internal.utils.JDALogger
import org.slf4j.Logger
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.annotation.Nonnull

internal open class AudioWebSocket(
    private val audioConnection: AudioConnection,
    private val listener: ConnectionListener,
    endpoint: String,
    private val guild: Guild,
    private val sessionId: String,
    private val token: String,
    private var shouldReconnect: Boolean,
) : WebSocketAdapter(),
    DaveProtocolCallbacks {
    @Volatile
    @JvmField
    internal var encryption: AudioEncryption? = null

    @Volatile
    @JvmField
    internal var crypto: CryptoAdapter? = null

    @JvmField
    internal var socket: WebSocket? = null

    private var daveSession: DaveSession? = null
    private val keepAlivePool: ScheduledExecutorService = getJDA().audioLifeCyclePool

    @Volatile
    private var connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED
    private var ready = false
    private var reconnecting = false
    private var ssrc: Int = 0
    private var secretKey: ByteArray? = null
    private var keepAliveHandle: Future<*>? = null
    private var address: InetSocketAddress? = null
    private var sequence: Long = 0

    @Volatile
    private var shutdown = false

    private val wssEndpoint: String

    init {
        // Add the version query parameter
        val url = IOUtil.addQuery(endpoint, "v", JDAInfo.AUDIO_GATEWAY_VERSION)
        // Append the Secure Websocket scheme so that our websocket library knows how to connect
        wssEndpoint =
            if (url.startsWith("wss://")) {
                url
            } else {
                "wss://$url"
            }

        if (sessionId.isEmpty()) {
            throw IllegalArgumentException("Cannot create a audio websocket connection using a null/empty sessionId!")
        }
        if (token.isEmpty()) {
            throw IllegalArgumentException("Cannot create a audio websocket connection using a null/empty token!")
        }
    }

    fun setDaveSession(daveSession: DaveSession) {
        this.daveSession = daveSession
    }

    // Used by AudioConnection

    @JvmName("send")
    internal fun send(message: String) {
        LOG.trace("<- {}", message)
        socket!!.sendText(message)
    }

    @JvmName("send")
    internal fun send(
        op: Int,
        data: Any?,
    ) {
        send(
            DataObject
                .empty()
                .put("op", op)
                .put("d", data)
                .toString(),
        )
    }

    @JvmName("startConnection")
    internal fun startConnection() {
        if (!reconnecting && socket != null) {
            throw IllegalStateException(
                "Somehow, someway, this AudioWebSocket has already attempted to start a connection!",
            )
        }

        try {
            val socketFactory = WebSocketFactory(getJDA().webSocketFactory)
            IOUtil.setServerName(socketFactory, wssEndpoint)
            if (socketFactory.socketTimeout > 0) {
                socketFactory.socketTimeout = maxOf(MIN_SOCKET_TIMEOUT_MS, socketFactory.socketTimeout)
            } else {
                socketFactory.socketTimeout = WEBSOCKET_CONNECT_TIMEOUT_MS
            }
            socket = socketFactory.createSocket(wssEndpoint)
            socket!!.isDirectTextMessage = true
            socket!!.addListener(this)
            changeStatus(ConnectionStatus.CONNECTING_AWAITING_WEBSOCKET_CONNECT)
            socket!!.connectAsynchronously()
        } catch (e: IOException) {
            LOG.warn(
                "Encountered IOException while attempting to connect to {}: {}\n" +
                    "Closing connection and attempting to reconnect.",
                wssEndpoint,
                e.message,
            )
            this.close(ConnectionStatus.ERROR_WEBSOCKET_UNABLE_TO_CONNECT)
        }
    }

    @JvmName("close")
    internal fun close(closeStatus: ConnectionStatus) {
        // Makes sure we don't run this method again
        // after the socket.close(1000) call fires onDisconnect
        if (shutdown) {
            return
        }
        locked { manager ->
            if (shutdown) {
                return@locked
            }
            var status = closeStatus
            ready = false
            shutdown = true
            stopKeepAlive()

            if (audioConnection.udpSocket != null) {
                audioConnection.udpSocket!!.close()
            }
            if (socket != null) {
                socket!!.sendClose()
            }

            audioConnection.shutdown()
            daveSession!!.destroy()

            val disconnectedChannel = manager.connectedChannel
            manager.setAudioConnection(null)

            // Verify that it is actually a lost of connection
            // and not due the connected channel being deleted.
            val api = getJDA()
            if (status == ConnectionStatus.DISCONNECTED_KICKED_FROM_CHANNEL &&
                (!api.client.isSession() || !api.client.isConnected())
            ) {
                LOG.debug("Connection was closed due to session invalidate!")
                status = ConnectionStatus.ERROR_CANNOT_RESUME
            } else if (status == ConnectionStatus.ERROR_LOST_CONNECTION ||
                status == ConnectionStatus.DISCONNECTED_KICKED_FROM_CHANNEL
            ) {
                // Get guild from JDA, don't use [guild] field to make sure
                // that we don't have a problem of an out of date guild stored in [guild]
                // during a possible mWS invalidate.
                val connGuild = api.getGuildById(guild.idLong)
                if (connGuild != null) {
                    val channel = connGuild.getGuildChannelById(audioConnection.getChannel().idLong) as AudioChannel?
                    if (channel == null) {
                        status = ConnectionStatus.DISCONNECTED_CHANNEL_DELETED
                    }
                }
            }

            changeStatus(status)

            // decide if we reconnect.
            if (shouldReconnect &&
                // indicated that the connection was purposely closed. don't reconnect.
                status.shouldReconnect() &&
                // Already handled.
                status != ConnectionStatus.AUDIO_REGION_CHANGE
            ) {
                if (disconnectedChannel == null) {
                    LOG.debug("Cannot reconnect due to null audio channel")
                    return@locked
                }
                api.directAudioController.reconnect(disconnectedChannel)
            } else if (status == ConnectionStatus.DISCONNECTED_REMOVED_FROM_GUILD) {
                // Remove audio manager as we are no longer in the guild
                api.audioManagersView.remove(guild.idLong)
            } else if (status != ConnectionStatus.AUDIO_REGION_CHANGE &&
                status != ConnectionStatus.DISCONNECTED_KICKED_FROM_CHANNEL
            ) {
                api.directAudioController.disconnect(guild)
            }
        }
    }

    @JvmName("changeStatus")
    internal fun changeStatus(newStatus: ConnectionStatus) {
        connectionStatus = newStatus
        listener.onStatusChange(newStatus)
    }

    @JvmName("setAutoReconnect")
    internal fun setAutoReconnect(shouldReconnect: Boolean) {
        this.shouldReconnect = shouldReconnect
    }

    @JvmName("getConnectionStatus")
    internal fun getConnectionStatus(): ConnectionStatus = connectionStatus

    @JvmName("getAddress")
    internal fun getAddress(): InetSocketAddress = address!!

    @JvmName("getSecretKey")
    internal fun getSecretKey(): ByteArray? = secretKey

    @JvmName("getSSRC")
    internal fun getSSRC(): Int = ssrc

    @JvmName("isReady")
    internal fun isReady(): Boolean = ready

    // TCP Listeners

    override fun onThreadStarted(
        websocket: WebSocket,
        threadType: ThreadType,
        thread: Thread,
    ) {
        getJDA().setContext()
    }

    override fun onConnected(
        websocket: WebSocket,
        headers: MutableMap<String, MutableList<String>>,
    ) {
        if (shutdown) {
            // Somehow this AudioWebSocket was shutdown before we finished connecting....
            // thus we just disconnect here since we were asked to shutdown
            socket!!.sendClose(NORMAL_CLOSURE_CODE)
            return
        }

        if (reconnecting) {
            resume()
        } else {
            identify()
        }
        changeStatus(ConnectionStatus.CONNECTING_AWAITING_AUTHENTICATION)
        audioConnection.prepareReady()
        reconnecting = false
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onTextMessage(
        websocket: WebSocket,
        data: ByteArray,
    ) {
        try {
            handleEvent(DataObject.fromJson(data))
        } catch (ex: Exception) {
            var message = "malformed"
            try {
                message = String(data, StandardCharsets.UTF_8)
            } catch (ignored: Exception) {
            }
            LOG.error("Encountered exception trying to handle an event message: {}", message, ex)
        }
    }

    @Suppress("ReturnCount")
    override fun onDisconnected(
        websocket: WebSocket,
        serverCloseFrame: WebSocketFrame?,
        clientCloseFrame: WebSocketFrame?,
        closedByServer: Boolean,
    ) {
        if (shutdown) {
            return
        }
        LOG.debug("The Audio connection was closed!\nBy remote? {}", closedByServer)
        if (serverCloseFrame != null) {
            LOG.debug("Reason: {}\nClose code: {}", serverCloseFrame.closeReason, serverCloseFrame.closeCode)
            val code = serverCloseFrame.closeCode
            val closeCode = VoiceCode.Close.from(code)
            when (closeCode) {
                VoiceCode.Close.RATE_LIMIT_EXCEEDED,
                VoiceCode.Close.SERVER_NOT_FOUND,
                VoiceCode.Close.SERVER_CRASH,
                VoiceCode.Close.INVALID_SESSION,
                -> this.close(ConnectionStatus.ERROR_CANNOT_RESUME)
                VoiceCode.Close.AUTHENTICATION_FAILED ->
                    this.close(ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE)
                VoiceCode.Close.DISCONNECTED_ALL_CLIENTS,
                VoiceCode.Close.DISCONNECTED,
                -> this.close(ConnectionStatus.DISCONNECTED_KICKED_FROM_CHANNEL)
                else -> this.reconnect()
            }
            return
        }
        if (clientCloseFrame != null) {
            LOG.debug(
                "ClientReason: {}\nClientCode: {}",
                clientCloseFrame.closeReason,
                clientCloseFrame.closeCode,
            )
            if (clientCloseFrame.closeCode != NORMAL_CLOSURE_CODE) {
                // unexpected close -> error -> attempt resume
                this.reconnect()
                return
            }
        }
        this.close(ConnectionStatus.NOT_CONNECTED)
    }

    override fun onUnexpectedError(
        websocket: WebSocket,
        cause: WebSocketException,
    ) {
        handleCallbackError(websocket, cause)
    }

    override fun handleCallbackError(
        websocket: WebSocket,
        cause: Throwable,
    ) {
        LOG.error("There was some audio websocket error", cause)
        val api = getJDA()
        api.handleEvent(ExceptionEvent(api, cause, true))
    }

    override fun onThreadCreated(
        websocket: WebSocket,
        threadType: ThreadType,
        thread: Thread,
    ) {
        val identifier = getJDA().identifierString
        val guildId = guild.id
        when (threadType) {
            ThreadType.CONNECT_THREAD ->
                thread.name = identifier + " AudioWS-ConnectThread (guildId: " + guildId + ')'
            ThreadType.FINISH_THREAD ->
                thread.name = identifier + " AudioWS-FinishThread (guildId: " + guildId + ')'
            ThreadType.WRITING_THREAD ->
                thread.name = identifier + " AudioWS-WriteThread (guildId: " + guildId + ')'
            ThreadType.READING_THREAD ->
                thread.name = identifier + " AudioWS-ReadThread (guildId: " + guildId + ')'
        }
    }

    override fun onConnectError(
        webSocket: WebSocket,
        e: WebSocketException,
    ) {
        LOG.warn(
            "Failed to establish websocket connection to {}: {} - {}\n" +
                "Closing connection and attempting to reconnect.",
            wssEndpoint,
            e.error,
            e.message,
        )
        this.close(ConnectionStatus.ERROR_WEBSOCKET_UNABLE_TO_CONNECT)
    }

    // Dave Protocol

    fun getDaveSession(): DaveSession = daveSession!!

    private fun sendBinary(
        opcode: Int,
        payload: ByteBuffer,
    ) {
        val buffer = ByteBuffer.allocate(1 + payload.remaining()).put(opcode.toByte()).put(payload)
        buffer.flip()
        socket!!.sendBinary(buffer.array())
    }

    override fun onBinaryMessage(
        websocket: WebSocket,
        binary: ByteArray,
    ) {
        val message = ByteBuffer.allocateDirect(binary.size)
        message.put(binary)
        message.flip()

        val sequence = message.getShort()
        this.sequence = (sequence.toLong()) and MLS_SEQUENCE_MASK
        val opcode = (message.get().toInt()) and 0xFF
        when (opcode) {
            VoiceCode.MLS_EXTERNAL_SENDER -> {
                LOG.trace("-> MLS_EXTERNAL_SENDER")
                daveSession!!.onDaveProtocolMLSExternalSenderPackage(message)
            }
            VoiceCode.MLS_PROPOSALS -> {
                LOG.trace("-> MLS_PROPOSALS")
                daveSession!!.onMLSProposals(message)
            }
            VoiceCode.MLS_ANNOUNCE_COMMIT_TRANSITION -> {
                LOG.trace("-> MLS_ANNOUNCE_COMMIT_TRANSITION")
                val transitionId = (message.getShort().toInt()) and 0xFFFF
                daveSession!!.onMLSPrepareCommitTransition(transitionId, message)
            }
            VoiceCode.MLS_WELCOME -> {
                LOG.trace("-> MLS_WELCOME")
                val transitionId = (message.getShort().toInt()) and 0xFFFF
                daveSession!!.onMLSWelcome(transitionId, message)
            }
            else -> LOG.trace("-> UNKNOWN OP {}", opcode)
        }
    }

    override fun sendMLSKeyPackage(
        @Nonnull mlsKeyPackage: ByteBuffer,
    ) {
        LOG.trace("<- MLS_KEY_PACKAGE")
        sendBinary(VoiceCode.MLS_KEY_PACKAGE, mlsKeyPackage)
    }

    override fun sendDaveProtocolReadyForTransition(transitionId: Int) {
        LOG.trace("<- DAVE_TRANSITION_READY")
        send(VoiceCode.DAVE_TRANSITION_READY, DataObject.empty().put("transition_id", transitionId))
    }

    override fun sendMLSCommitWelcome(
        @Nonnull commitWelcomeMessage: ByteBuffer,
    ) {
        LOG.trace("<- MLS_COMMIT_WELCOME")
        sendBinary(VoiceCode.MLS_COMMIT_WELCOME, commitWelcomeMessage)
    }

    override fun sendMLSInvalidCommitWelcome(transitionId: Int) {
        LOG.trace("<- MLS_INVALID_COMMIT_WELCOME")
        send(VoiceCode.MLS_INVALID_COMMIT_WELCOME, DataObject.empty().put("transition_id", transitionId))
    }

    // Internals

    private fun handleEvent(contentAll: DataObject) {
        val opCode = contentAll.getInt("op")
        sequence = contentAll.getLong("seq", sequence)

        when (opCode) {
            VoiceCode.HELLO -> {
                LOG.trace("-> HELLO {}", contentAll)
                val payload = contentAll.getObject("d")
                val interval = payload.getInt("heartbeat_interval")
                stopKeepAlive()
                setupKeepAlive(interval)
                daveSession!!.initialize()
            }
            VoiceCode.READY -> {
                LOG.trace("-> READY {}", contentAll)
                val content = contentAll.getObject("d")
                ssrc = content.getInt("ssrc")
                val port = content.getInt("port")
                val ip = content.getString("ip")
                val modes = content.getArray("modes")
                encryption = CryptoAdapter.negotiate(AudioEncryption.fromArray(modes))
                if (encryption == null) {
                    close(ConnectionStatus.ERROR_UNSUPPORTED_ENCRYPTION_MODES)
                    LOG.error("None of the provided encryption modes are supported: {}", modes)
                    return
                } else {
                    LOG.debug("Using encryption mode " + encryption!!.key)
                }

                // Find our external IP and Port using Discord
                var externalIpAndPort: InetSocketAddress?

                changeStatus(ConnectionStatus.CONNECTING_ATTEMPTING_UDP_DISCOVERY)
                var tries = 0
                do {
                    externalIpAndPort = handleUdpDiscovery(InetSocketAddress(ip, port), ssrc)
                    tries++
                    if (externalIpAndPort == null && tries > UDP_DISCOVERY_MAX_TRIES) {
                        close(ConnectionStatus.ERROR_UDP_UNABLE_TO_CONNECT)
                        return
                    }
                } while (externalIpAndPort == null)

                daveSession!!.assignSsrcToCodec(DaveSession.Codec.OPUS, ssrc)

                val `object` =
                    DataObject
                        .empty()
                        .put("protocol", "udp")
                        .put(
                            "data",
                            DataObject
                                .empty()
                                .put("address", externalIpAndPort.hostString)
                                .put("port", externalIpAndPort.port)
                                .put("mode", encryption!!.key),
                        ) // Discord requires encryption
                send(VoiceCode.SELECT_PROTOCOL, `object`)
                changeStatus(ConnectionStatus.CONNECTING_AWAITING_READY)
            }
            VoiceCode.RESUMED -> {
                LOG.trace("-> RESUMED {}", contentAll)
                LOG.debug("Successfully resumed session!")
                changeStatus(ConnectionStatus.CONNECTED)
                ready = true
                MiscUtil.locked(audioConnection.readyLock, Runnable { audioConnection.readyCondvar.signalAll() })
            }
            VoiceCode.SESSION_DESCRIPTION -> {
                LOG.trace("-> SESSION_DESCRIPTION {}", contentAll)
                send(
                    VoiceCode.USER_SPEAKING_UPDATE, // required to receive audio?
                    DataObject
                        .empty()
                        .put("delay", 0)
                        .put("speaking", 0)
                        .put("ssrc", ssrc),
                )
                // secret_key is an array of 32 ints that are less than 256, so they are bytes.
                val keyArray = contentAll.getObject("d").getArray("secret_key")

                val secretKey = ByteArray(DISCORD_SECRET_KEY_LENGTH)
                for (i in 0 until keyArray.length()) {
                    secretKey[i] = keyArray.getInt(i).toByte()
                }
                this.secretKey = secretKey

                crypto = DaveCryptoAdapter(CryptoAdapter.getAdapter(encryption!!, secretKey), daveSession!!, ssrc)
                daveSession!!.onSelectProtocolAck(contentAll.getObject("d").getInt("dave_protocol_version"))

                LOG.debug("Audio connection has finished connecting!")
                ready = true
                MiscUtil.locked(audioConnection.readyLock, Runnable { audioConnection.readyCondvar.signalAll() })
                changeStatus(ConnectionStatus.CONNECTED)
            }
            VoiceCode.HEARTBEAT -> {
                LOG.trace("-> HEARTBEAT {}", contentAll)
                send(VoiceCode.HEARTBEAT, System.currentTimeMillis())
            }
            VoiceCode.HEARTBEAT_ACK -> {
                LOG.trace("-> HEARTBEAT_ACK {}", contentAll)
                val ping = System.currentTimeMillis() - contentAll.getObject("d").getLong("t")
                listener.onPing(ping)
            }
            VoiceCode.USER_SPEAKING_UPDATE -> {
                LOG.trace("-> USER_SPEAKING_UPDATE {}", contentAll)
                val content = contentAll.getObject("d")
                val ssrc = content.getInt("ssrc")
                val userId = content.getUnsignedLong("user_id")
                audioConnection.updateUserSSRC(ssrc, userId)
                daveSession!!.addUser(userId)

                val speaking = SpeakingMode.getModes(content.getInt("speaking"))
                val user = getUser(userId)
                if (user == null) {
                    // more relevant for audio connection
                    LOG.trace("Got an Audio USER_SPEAKING_UPDATE for a non-existent User. JSON: {}", contentAll)
                    listener.onUserSpeakingModeUpdate(UserSnowflake.fromId(userId), speaking)
                } else {
                    listener.onUserSpeakingModeUpdate(user as UserSnowflake, speaking)
                }
            }
            VoiceCode.USER_BULK_CONNECT -> {
                LOG.trace("-> USER_BULK_CONNECT {}", contentAll)
                val payload = contentAll.getObject("d")
                val userIds = payload.getArray("user_ids")
                for (i in 0 until userIds.length()) {
                    val userId = userIds.getUnsignedLong(i)
                    daveSession!!.addUser(userId)
                }
            }
            VoiceCode.USER_DISCONNECT -> {
                LOG.trace("-> USER_DISCONNECT {}", contentAll)
                val payload = contentAll.getObject("d")
                val userId = payload.getUnsignedLong("user_id")
                audioConnection.removeUserSSRC(userId)
                daveSession!!.removeUser(userId)
            }
            VoiceCode.DAVE_PREPARE_TRANSITION -> {
                LOG.trace("-> DAVE_PREPARE_TRANSITION {}", contentAll)
                val payload = contentAll.getObject("d")
                daveSession!!.onDaveProtocolPrepareTransition(
                    payload.getInt("transition_id"),
                    payload.getInt("protocol_version"),
                )
            }
            VoiceCode.DAVE_EXECUTE_TRANSITION -> {
                LOG.trace("-> DAVE_EXECUTE_TRANSITION {}", contentAll)
                val payload = contentAll.getObject("d")
                daveSession!!.onDaveProtocolExecuteTransition(payload.getInt("transition_id"))
            }
            VoiceCode.DAVE_PREPARE_EPOCH -> {
                LOG.trace("-> DAVE_PREPARE_EPOCH {}", contentAll)
                val payload = contentAll.getObject("d")
                daveSession!!.onDaveProtocolPrepareEpoch(
                    payload.getUnsignedLong("epoch"),
                    payload.getInt("protocol_version"),
                )
            }
            else -> {
                LOG.trace("-> UNKNOWN OP {}: {}", opCode, contentAll)
                // undocumented / unused
            }
        }
    }

    private fun identify() {
        sequence = 0
        val maxDaveProtocolVersion = daveSession!!.maxProtocolVersion
        if (maxDaveProtocolVersion == 0) {
            LOG.warn(
                "Maximum Dave Protocol Version is 0. " +
                    "This means your connection does not properly support encryption. " +
                    "This will fail to work in the future.",
            )
        }

        val connectObj =
            DataObject
                .empty()
                .put("server_id", guild.id)
                .put("user_id", getJDA().selfUser.id)
                .put("session_id", sessionId)
                .put("token", token)
                .put("max_dave_protocol_version", maxDaveProtocolVersion)
        send(VoiceCode.IDENTIFY, connectObj)
    }

    private fun resume() {
        LOG.debug("Sending resume payload...")
        val resumeObj =
            DataObject
                .empty()
                .put("server_id", guild.id)
                .put("session_id", sessionId)
                .put("token", token)
                .put("seq_ack", sequence)
        send(VoiceCode.RESUME, resumeObj)
    }

    private fun getJDA(): JDAImpl = audioConnection.getJDA()

    private fun locked(consumer: Consumer<AudioManagerImpl>) {
        val manager = guild.audioManager as AudioManagerImpl
        MiscUtil.locked(manager.CONNECTION_LOCK, Runnable { consumer.accept(manager) })
    }

    private fun reconnect() {
        if (shutdown) {
            return
        }
        locked { _ ->
            if (shutdown) {
                return@locked
            }
            ready = false
            reconnecting = true
            changeStatus(ConnectionStatus.ERROR_LOST_CONNECTION)
            startConnection()
        }
    }

    private fun handleUdpDiscovery(
        address: InetSocketAddress,
        ssrc: Int,
    ): InetSocketAddress? {
        // We will now send a packet to discord to punch a port hole in the NAT wall.
        // This is called UDP hole punching.
        try {
            // First close existing socket from possible previous attempts
            if (audioConnection.udpSocket != null) {
                audioConnection.udpSocket!!.close()
            }
            // Create new UDP socket for communication
            audioConnection.udpSocket = DatagramSocket()

            // Create a byte array of length 74 containing our ssrc.
            val buffer = ByteBuffer.allocate(UDP_DISCOVERY_PACKET_SIZE) // taken from documentation
            buffer.putShort(1) // 1 = send (receive will be 2)
            buffer.putShort(UDP_DISCOVERY_DATA_LENGTH.toShort()) // length bytes (required)
            // Put the ssrc that we were given into the packet to send back to discord.
            // rest of the bytes are used only in the response (address/port)
            buffer.putInt(ssrc)

            // Construct our packet to be sent loaded with the byte buffer we store the ssrc in.
            val discoveryPacket = DatagramPacket(buffer.array(), buffer.array().size, address)
            audioConnection.udpSocket!!.send(discoveryPacket)

            // Discord responds to our packet, returning a packet containing our external ip and the
            // port we connected through.
            // Give a buffer the same size as the one we sent.
            val receivedPacket = DatagramPacket(ByteArray(UDP_DISCOVERY_PACKET_SIZE), UDP_DISCOVERY_PACKET_SIZE)
            audioConnection.udpSocket!!.soTimeout = UDP_DISCOVERY_TIMEOUT_MS
            audioConnection.udpSocket!!.receive(receivedPacket)

            // The byte array returned by discord containing our external ip and the port
            // that we used to connect to discord with.
            val received = receivedPacket.data

            // Example string:"   121.83.253.66 ��"
            // You'll notice that there are 4 leading nulls and a large amount of nulls
            // between the the ip and the last 2 bytes.
            // Not sure why these exist.
            // The last 2 bytes are the port. More info below.

            // Take bytes between SSRC and PORT and put them into a string
            // null bytes at the beginning are skipped
            // and the rest are appended to the end of the string
            var ourIP = String(received, 8, received.size - 10, StandardCharsets.UTF_8)
            // Removes the extra nulls attached to the end of the IP string
            ourIP = ourIP.trim { it <= ' ' }

            // The port exists as the last 2 bytes in the packet data,
            // and is encoded as an UNSIGNED short.
            // Furthermore, it is stored in Little Endian instead of normal Big Endian.
            // We will first need to convert the byte order from Little Endian to Big Endian
            // (reverse the order)
            // Then we will need to deal with the fact that the bytes represent an unsigned short.
            // Java cannot deal with unsigned types, so we will have to promote the short to a
            // higher type.

            // Get our port which is stored as little endian at the end of the packet
            // We AND it with 0xFFFF to ensure that it isn't sign extended
            val ourPort = (IOUtil.getShortBigEndian(received, received.size - 2).toInt()) and 0xFFFF
            this.address = address
            return InetSocketAddress(ourIP, ourPort)
        } catch (_: IOException) {
            // We either timed out or the socket could not be created (firewall?)
            return null
        }
    }

    private fun stopKeepAlive() {
        if (keepAliveHandle != null) {
            keepAliveHandle!!.cancel(true)
        }
        keepAliveHandle = null
    }

    private fun setupKeepAlive(keepAliveInterval: Int) {
        if (keepAliveHandle != null) {
            LOG.error("Setting up a KeepAlive runnable while the previous one seems to still be active!!")
        }

        try {
            if (socket != null) {
                val rawSocket: Socket? = socket!!.socket
                if (rawSocket != null) {
                    rawSocket.soTimeout = keepAliveInterval + KEEP_ALIVE_TIMEOUT_GRACE_MS
                }
            }
        } catch (ex: SocketException) {
            LOG.warn("Failed to setup timeout for socket", ex)
        }

        val keepAliveRunnable =
            Runnable {
                getJDA().setContext()
                if (socket != null && socket!!.isOpen) { // TCP keep-alive
                    val packet = DataObject.empty().put("t", System.currentTimeMillis())
                    if (sequence > 0) {
                        packet.put("seq_ack", sequence)
                    }
                    send(VoiceCode.HEARTBEAT, packet)
                }
                if (audioConnection.udpSocket != null && !audioConnection.udpSocket!!.isClosed) { // UDP keep-alive
                    try {
                        val keepAlivePacket = DatagramPacket(UDP_KEEP_ALIVE, UDP_KEEP_ALIVE.size, address)
                        audioConnection.udpSocket!!.send(keepAlivePacket)
                    } catch (_: NoRouteToHostException) {
                        LOG.warn("Closing AudioConnection due to inability to ping audio packets.")
                        LOG.warn(
                            "Cannot send audio packet because JDA navigate the route to Discord.\n" +
                                "Are you sure you have internet connection? It is likely that you've lost connection.",
                        )
                        this.close(ConnectionStatus.ERROR_LOST_CONNECTION)
                    } catch (e: IOException) {
                        LOG.error("There was some error sending an audio keepalive packet", e)
                    }
                }
            }

        try {
            keepAliveHandle =
                keepAlivePool.scheduleAtFixedRate(keepAliveRunnable, 0, keepAliveInterval.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
        } // ignored because this is probably caused due to a race condition
        // related to the threadpool shutdown.
    }

    private fun getUser(userId: Long): User? = getJDA().getUserById(userId)

    @Deprecated("Deprecated in Java 9 because the finalization system is being changed/removed")
    @Suppress("deprecation")
    protected fun finalize() {
        if (!shutdown) {
            LOG.error("Finalization hook of AudioWebSocket was triggered without properly shutting down")
            close(ConnectionStatus.NOT_CONNECTED)
        }
    }

    companion object {
        @JvmField
        val LOG: Logger = JDALogger.getLog(AudioWebSocket::class.java)

        const val DISCORD_SECRET_KEY_LENGTH: Int = 32

        private const val MIN_SOCKET_TIMEOUT_MS: Int = 1000
        private const val WEBSOCKET_CONNECT_TIMEOUT_MS: Int = 10000
        private const val NORMAL_CLOSURE_CODE: Int = 1000
        private const val UDP_DISCOVERY_PACKET_SIZE: Int = 74
        private const val UDP_DISCOVERY_DATA_LENGTH: Int = 70
        private const val UDP_DISCOVERY_TIMEOUT_MS: Int = 1000
        private const val UDP_DISCOVERY_MAX_TRIES: Int = 5
        private const val KEEP_ALIVE_TIMEOUT_GRACE_MS: Int = 10000
        private const val MLS_SEQUENCE_MASK: Long = 0xFFFF

        private val UDP_KEEP_ALIVE = byteArrayOf(0xC9.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
    }
}
