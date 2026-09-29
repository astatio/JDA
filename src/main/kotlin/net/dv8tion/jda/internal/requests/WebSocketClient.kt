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

import com.neovisionaries.ws.client.ThreadType
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import com.neovisionaries.ws.client.WebSocketListener
import gnu.trove.iterator.TLongObjectIterator
import gnu.trove.map.TLongObjectMap
import net.dv8tion.jda.api.GatewayEncoding
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDAInfo
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audio.hooks.ConnectionListener
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.ExceptionEvent
import net.dv8tion.jda.api.events.RawGatewayEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent
import net.dv8tion.jda.api.events.session.SessionInvalidateEvent
import net.dv8tion.jda.api.events.session.SessionRecreateEvent
import net.dv8tion.jda.api.events.session.SessionResumeEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.exceptions.ParsingException
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.requests.CloseCode
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.SessionController
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.data.DataType
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.audio.ConnectionRequest
import net.dv8tion.jda.internal.audio.ConnectionStage
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.handle.ApplicationCommandPermissionsUpdateHandler
import net.dv8tion.jda.internal.handle.AutoModExecutionHandler
import net.dv8tion.jda.internal.handle.AutoModRuleHandler
import net.dv8tion.jda.internal.handle.ChannelCreateHandler
import net.dv8tion.jda.internal.handle.ChannelDeleteHandler
import net.dv8tion.jda.internal.handle.ChannelUpdateHandler
import net.dv8tion.jda.internal.handle.EntitlementCreateHandler
import net.dv8tion.jda.internal.handle.EntitlementDeleteHandler
import net.dv8tion.jda.internal.handle.EntitlementUpdateHandler
import net.dv8tion.jda.internal.handle.EventCache
import net.dv8tion.jda.internal.handle.GuildAuditLogEntryCreateHandler
import net.dv8tion.jda.internal.handle.GuildBanHandler
import net.dv8tion.jda.internal.handle.GuildCreateHandler
import net.dv8tion.jda.internal.handle.GuildDeleteHandler
import net.dv8tion.jda.internal.handle.GuildEmojisUpdateHandler
import net.dv8tion.jda.internal.handle.GuildMemberAddHandler
import net.dv8tion.jda.internal.handle.GuildMemberRemoveHandler
import net.dv8tion.jda.internal.handle.GuildMemberUpdateHandler
import net.dv8tion.jda.internal.handle.GuildMembersChunkHandler
import net.dv8tion.jda.internal.handle.GuildRoleCreateHandler
import net.dv8tion.jda.internal.handle.GuildRoleDeleteHandler
import net.dv8tion.jda.internal.handle.GuildRoleUpdateHandler
import net.dv8tion.jda.internal.handle.GuildSoundboardSoundCreateHandler
import net.dv8tion.jda.internal.handle.GuildSoundboardSoundDeleteHandler
import net.dv8tion.jda.internal.handle.GuildSoundboardSoundUpdateHandler
import net.dv8tion.jda.internal.handle.GuildSoundboardSoundsUpdateHandler
import net.dv8tion.jda.internal.handle.GuildStickersUpdateHandler
import net.dv8tion.jda.internal.handle.GuildSyncHandler
import net.dv8tion.jda.internal.handle.GuildUpdateHandler
import net.dv8tion.jda.internal.handle.InteractionCreateHandler
import net.dv8tion.jda.internal.handle.InviteCreateHandler
import net.dv8tion.jda.internal.handle.InviteDeleteHandler
import net.dv8tion.jda.internal.handle.MessageBulkDeleteHandler
import net.dv8tion.jda.internal.handle.MessageCreateHandler
import net.dv8tion.jda.internal.handle.MessageDeleteHandler
import net.dv8tion.jda.internal.handle.MessagePollVoteHandler
import net.dv8tion.jda.internal.handle.MessageReactionBulkRemoveHandler
import net.dv8tion.jda.internal.handle.MessageReactionClearEmojiHandler
import net.dv8tion.jda.internal.handle.MessageReactionHandler
import net.dv8tion.jda.internal.handle.MessageUpdateHandler
import net.dv8tion.jda.internal.handle.PresenceUpdateHandler
import net.dv8tion.jda.internal.handle.ReadyHandler
import net.dv8tion.jda.internal.handle.ScheduledEventCreateHandler
import net.dv8tion.jda.internal.handle.ScheduledEventDeleteHandler
import net.dv8tion.jda.internal.handle.ScheduledEventUpdateHandler
import net.dv8tion.jda.internal.handle.ScheduledEventUserHandler
import net.dv8tion.jda.internal.handle.SocketHandler
import net.dv8tion.jda.internal.handle.StageInstanceCreateHandler
import net.dv8tion.jda.internal.handle.StageInstanceDeleteHandler
import net.dv8tion.jda.internal.handle.StageInstanceUpdateHandler
import net.dv8tion.jda.internal.handle.ThreadCreateHandler
import net.dv8tion.jda.internal.handle.ThreadDeleteHandler
import net.dv8tion.jda.internal.handle.ThreadListSyncHandler
import net.dv8tion.jda.internal.handle.ThreadMemberUpdateHandler
import net.dv8tion.jda.internal.handle.ThreadMembersUpdateHandler
import net.dv8tion.jda.internal.handle.ThreadUpdateHandler
import net.dv8tion.jda.internal.handle.TypingStartHandler
import net.dv8tion.jda.internal.handle.UserUpdateHandler
import net.dv8tion.jda.internal.handle.VoiceChannelEffectSendHandler
import net.dv8tion.jda.internal.handle.VoiceChannelStatusUpdateHandler
import net.dv8tion.jda.internal.handle.VoiceServerUpdateHandler
import net.dv8tion.jda.internal.handle.VoiceStateUpdateHandler
import net.dv8tion.jda.internal.managers.AudioManagerImpl
import net.dv8tion.jda.internal.managers.PresenceImpl
import net.dv8tion.jda.internal.utils.IOUtil
import net.dv8tion.jda.internal.utils.JDALogger
import net.dv8tion.jda.internal.utils.ShutdownReason
import net.dv8tion.jda.internal.utils.cache.AbstractCacheView
import net.dv8tion.jda.internal.utils.compress.Decompressor
import net.dv8tion.jda.internal.utils.compress.ZlibDecompressor
import org.slf4j.Logger
import org.slf4j.MDC
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import java.util.Objects
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.zip.DataFormatException
import javax.annotation.Nonnull

open class WebSocketClient(
    @JvmField internal val api: JDAImpl,
    @JvmField protected val compression: Compression,
    @JvmField protected val gatewayIntents: Int,
    @JvmField protected val encoding: GatewayEncoding,
) : WebSocketAdapter(),
    WebSocketListener {
    @JvmField
    protected val shardInfo: JDA.ShardInfo = api.shardInfo

    @JvmField
    protected val handlers: MutableMap<String, SocketHandler> = HashMap()

    @JvmField
    protected val chunkManager: MemberChunkManager = MemberChunkManager(this)

    @JvmField
    var socket: WebSocket? = null

    @JvmField
    protected var traceMetadata: String? = null

    @Volatile
    @JvmField
    protected var sessionId: String? = null

    @JvmField
    protected val readLock: Any = Any()

    @JvmField
    protected var decompressor: Decompressor? = null

    @JvmField
    protected var resumeUrl: String? = null

    @JvmField
    internal val queueLock: ReentrantLock = ReentrantLock()

    @JvmField
    internal val executor: ScheduledExecutorService = api.gatewayPool

    @JvmField
    protected var ratelimitThread: WebSocketSendingThread? = null

    @Volatile
    @JvmField
    protected var keepAliveThread: Future<*>? = null

    @JvmField
    protected val reconnectLock: ReentrantLock = ReentrantLock()

    @JvmField
    protected val reconnectCondvar: Condition = reconnectLock.newCondition()

    @JvmField
    protected var initiating: Boolean = false

    @JvmField
    protected var missedHeartbeats: Int = 0

    @JvmField
    protected var reconnectTimeoutS: Int = RECONNECT_TIMEOUT_INITIAL_S

    @JvmField
    protected var heartbeatStartTime: Long = 0

    @JvmField
    protected var identifyTime: Long = 0

    @JvmField
    internal val queuedAudioConnections: TLongObjectMap<ConnectionRequest> = MiscUtil.newLongMap()

    @JvmField
    internal val chunkSyncQueue: Queue<DataObject> = ConcurrentLinkedQueue()

    @JvmField
    internal val ratelimitQueue: Queue<DataObject> = ConcurrentLinkedQueue()

    @Volatile
    @JvmField
    protected var ratelimitResetTime: Long = 0

    @JvmField
    protected val messagesSent: AtomicInteger = AtomicInteger(0)

    @Volatile
    @JvmField
    protected var shutdown: Boolean = false

    @JvmField
    protected var shouldReconnect: Boolean

    @JvmField
    protected var handleIdentifyRateLimit: Boolean = false

    @JvmField
    protected var connected: Boolean = false

    @Volatile
    @JvmField
    protected var printedRateLimitMessage: Boolean = false

    @Volatile
    @JvmField
    internal var sentAuthInfo: Boolean = false

    @JvmField
    protected var firstInit: Boolean = true

    @JvmField
    protected var processingReady: Boolean = true

    @Volatile
    @JvmField
    protected var connectNode: ConnectNode? = null

    init {
        shouldReconnect = api.isAutoReconnect
        connectNode = StartingNode()
        setupHandlers()
        appendSession()
    }

    @Suppress("TooGenericExceptionCaught") // mirrors the Java multi-catch that rethrows the same type
    private fun appendSession() {
        try {
            api.sessionController.appendSession(connectNode!!)
        } catch (e: RuntimeException) {
            failAppendSession(e)
        } catch (e: Error) {
            failAppendSession(e)
        }
    }

    private fun failAppendSession(e: Throwable): Nothing {
        LOG.error("Failed to append new session to session controller queue. Shutting down!", e)
        api.setStatus(JDA.Status.SHUTDOWN)
        api.handleEvent(ShutdownEvent(api, OffsetDateTime.now(), ABNORMAL_CLOSE_CODE))
        throw e
    }

    fun getJDA(): JDA = api

    fun setAutoReconnect(reconnect: Boolean) {
        shouldReconnect = reconnect
    }

    fun isConnected(): Boolean = connected

    fun getGatewayIntents(): Int = gatewayIntents

    fun getChunkManager(): MemberChunkManager = chunkManager

    fun ready() {
        if (initiating) {
            initiating = false
            processingReady = false
            if (firstInit) {
                firstInit = false
                if (api.guilds.size >= LARGE_GUILD_COUNT) {
                    JDAImpl.LOG.warn(" __      __ _    ___  _  _  ___  _  _   ___  _ ")
                    JDAImpl.LOG.warn(" \\ \\    / //_\\  | _ \\| \\| ||_ _|| \\| | / __|| |")
                    JDAImpl.LOG.warn("  \\ \\/\\/ // _ \\ |   /| .` | | | | .` || (_ ||_|")
                    JDAImpl.LOG.warn("   \\_/\\_//_/ \\_\\|_|_\\|_|\\_||___||_|\\_| \\___|(_)")
                    JDAImpl.LOG.warn("You're running a session with over 2000 connected")
                    JDAImpl.LOG.warn("guilds. You should shard the connection in order")
                    JDAImpl.LOG.warn("to split the load or things like resuming")
                    JDAImpl.LOG.warn("connection might not work as expected.")
                    JDAImpl.LOG.warn("For more info see https://git.io/vrFWP")
                }
                JDAImpl.LOG.info("Finished Loading!")
                api.handleEvent(ReadyEvent(api))
            } else {
                updateAudioManagerReferences()
                JDAImpl.LOG.info("Finished (Re)Loading!")
                api.handleEvent(SessionRecreateEvent(api))
            }
        } else {
            JDAImpl.LOG.debug("Successfully resumed Session!")
            api.handleEvent(SessionResumeEvent(api))
        }
        api.setStatus(JDA.Status.CONNECTED)
    }

    fun isReady(): Boolean = !initiating

    fun isSession(): Boolean = sessionId != null

    fun handle(events: List<DataObject>) {
        events.forEach(this::onDispatch)
    }

    fun send(message: DataObject) {
        locked("Interrupted while trying to add request to queue", Runnable { ratelimitQueue.add(message) })
    }

    fun cancelChunkRequest(nonce: String) {
        locked(
            "Interrupted while trying to cancel chunk request",
            Runnable { chunkSyncQueue.removeIf { it.getString("nonce", "") == nonce } },
        )
    }

    fun sendChunkRequest(request: DataObject) {
        locked("Interrupted while trying to add chunk request", Runnable { chunkSyncQueue.add(request) })
    }

    @JvmName("send")
    internal fun send(
        message: DataObject,
        skipQueue: Boolean,
    ): Boolean {
        if (!connected) {
            return false
        }

        val now = System.currentTimeMillis()

        if (ratelimitResetTime <= now) {
            messagesSent.set(0)
            ratelimitResetTime = now + RATE_LIMIT_RESET_MS
            printedRateLimitMessage = false
        }

        // Allows 115 messages to be sent before limiting.
        return if (messagesSent.get() <= RATE_LIMIT_MESSAGES || (skipQueue && messagesSent.get() <= RATE_LIMIT_MESSAGES_SKIP_QUEUE)) {
            if (LOG.isTraceEnabled) {
                val redactedMessage = message.toString().replace(getToken(), "<REDACTED>")
                LOG.trace("<- {}", redactedMessage)
            }
            if (encoding == GatewayEncoding.ETF) {
                socket!!.sendBinary(message.toETF())
            } else {
                socket!!.sendText(message.toString())
            }
            messagesSent.getAndIncrement()
            true
        } else {
            if (!printedRateLimitMessage) {
                LOG.warn(
                    "Hit the WebSocket RateLimit! This can be caused by too many presence or voice status updates" +
                        " (connect/disconnect/mute/deaf). Regular: {} Voice: {} Chunking: {}",
                    ratelimitQueue.size,
                    queuedAudioConnections.size(),
                    chunkSyncQueue.size,
                )
                printedRateLimitMessage = true
            }
            false
        }
    }

    protected fun setupSendingThread() {
        ratelimitThread = WebSocketSendingThread(this)
        ratelimitThread!!.start()
    }

    @Suppress("SwallowedException") // mirrors the original's deliberately ignored close-timeout failure
    private fun prepareClose() {
        try {
            val rawSocket = socket?.socket
            rawSocket?.soTimeout = SOCKET_TIMEOUT_MS
        } catch (ignored: SocketException) {
            // no-op
        }
    }

    fun close() {
        prepareClose()
        socket?.sendClose(GRACEFUL_CLOSE_CODE)
    }

    fun close(code: Int) {
        prepareClose()
        socket?.sendClose(code)
    }

    fun close(
        code: Int,
        reason: String,
    ) {
        prepareClose()
        socket?.sendClose(code, reason)
    }

    fun shutdown() {
        val callOnShutdown =
            MiscUtil.locked(
                reconnectLock,
                Supplier {
                    if (shutdown) {
                        return@Supplier false
                    }
                    shutdown = true
                    shouldReconnect = false
                    connectNode?.let { api.sessionController.removeSession(it) }
                    val wasConnected = connected
                    close(GRACEFUL_CLOSE_CODE, "Shutting down")
                    reconnectCondvar.signalAll() // signal reconnect attempts to stop
                    !wasConnected
                },
            )

        if (callOnShutdown) {
            onShutdown(GRACEFUL_CLOSE_CODE)
        }
    }

    /*
       ### Start Internal methods ###
     */

    protected fun onShutdown(rawCloseCode: Int) {
        api.shutdownInternals(ShutdownEvent(api, OffsetDateTime.now(), rawCloseCode))
    }

    @Synchronized
    protected fun connect() {
        if (api.status != JDA.Status.ATTEMPTING_TO_RECONNECT) {
            api.setStatus(JDA.Status.CONNECTING_TO_WEBSOCKET)
        }
        if (shutdown) {
            throw RejectedExecutionException("JDA is shutdown!")
        }
        initiating = true

        try {
            var gatewayUrl = resumeUrl ?: api.gatewayUrl
            gatewayUrl =
                IOUtil.addQuery(
                    gatewayUrl,
                    "encoding",
                    encoding.name.lowercase(Locale.ROOT),
                    "v",
                    JDAInfo.DISCORD_GATEWAY_VERSION,
                )
            if (compression != Compression.NONE) {
                gatewayUrl = IOUtil.addQuery(gatewayUrl, "compress", compression.key)
                if (compression == Compression.ZLIB) {
                    if (decompressor == null || decompressor!!.getType() != Compression.ZLIB) {
                        decompressor = ZlibDecompressor(api.maxBufferSize)
                    }
                }
            }

            val socketFactory = WebSocketFactory(api.webSocketFactory)
            IOUtil.setServerName(socketFactory, gatewayUrl)
            if (socketFactory.socketTimeout > 0) {
                socketFactory.socketTimeout = maxOf(SOCKET_TIMEOUT_MIN_MS, socketFactory.socketTimeout)
            } else {
                socketFactory.socketTimeout = SOCKET_TIMEOUT_MS
            }

            socket = socketFactory.createSocket(gatewayUrl)
            socket!!.setDirectTextMessage(true)
            socket!!.addHeader("Accept-Encoding", "gzip").addListener(this).connect()
        } catch (e: IOException) {
            failConnect(e)
        } catch (e: WebSocketException) {
            failConnect(e)
        } catch (e: IllegalArgumentException) {
            failConnect(e)
        }
    }

    private fun failConnect(e: Exception): Nothing {
        resumeUrl = null
        api.resetGatewayUrl()
        // Completely fail here. We couldn't make the connection.
        throw IllegalStateException(e)
    }

    @Throws(Exception::class)
    override fun onThreadStarted(
        websocket: WebSocket,
        threadType: ThreadType,
        thread: Thread,
    ) {
        api.setContext()
    }

    override fun onConnected(
        websocket: WebSocket,
        headers: MutableMap<String, MutableList<String>>,
    ) {
        prepareClose() // set 10s timeout in-case discord never sends us a HELLO payload
        api.setStatus(JDA.Status.IDENTIFYING_SESSION)
        if (sessionId == null) {
            LOG.info("Connected to WebSocket")
            // Log which intents are used on debug level since most people won't know how to use the
            // binary output anyway
            LOG.debug("Connected with gateway intents: {}", Integer.toBinaryString(gatewayIntents))
        } else {
            // no need to log for resume here
            LOG.debug("Connected to WebSocket")
        }
        connected = true
        // reconnectTimeoutS = 2; We will reset this when the session was started successfully
        // (ready/resume)
        messagesSent.set(0)
        ratelimitResetTime = System.currentTimeMillis() + RATE_LIMIT_RESET_MS
        if (sessionId == null) {
            sendIdentify()
        } else {
            sendResume()
        }
    }

    override fun onDisconnected(
        websocket: WebSocket,
        serverCloseFrame: WebSocketFrame?,
        clientCloseFrame: WebSocketFrame?,
        closedByServer: Boolean,
    ) {
        sentAuthInfo = false
        connected = false
        // Use a new thread to avoid issues with sleep interruption
        if (Thread.currentThread().isInterrupted) {
            val thread = Thread { handleDisconnect(serverCloseFrame, clientCloseFrame, closedByServer) }
            thread.name = api.identifierString + " MainWS-ReconnectThread"
            thread.start()
        } else {
            handleDisconnect(serverCloseFrame, clientCloseFrame, closedByServer)
        }
    }

    private fun handleDisconnect(
        serverCloseFrame: WebSocketFrame?,
        clientCloseFrame: WebSocketFrame?,
        closedByServer: Boolean,
    ) {
        api.setStatus(JDA.Status.DISCONNECTED)
        var closeCode: CloseCode? = null
        var rawCloseCode = DEFAULT_CLOSE_CODE
        // When we get 1000 from remote close we will try to resume
        // as apparently discord doesn't understand what "graceful disconnect" means
        var isInvalidate = false

        keepAliveThread?.cancel(false)
        keepAliveThread = null
        if (closedByServer && serverCloseFrame != null) {
            rawCloseCode = serverCloseFrame.closeCode
            val rawCloseReason = serverCloseFrame.closeReason
            closeCode = CloseCode.from(rawCloseCode)
            when (closeCode) {
                CloseCode.RATE_LIMITED ->
                    LOG.error(
                        "WebSocket connection closed due to ratelimit! Sent more than 120 websocket messages in under 60 seconds!",
                    )
                CloseCode.UNKNOWN_ERROR ->
                    LOG.error("WebSocket connection closed due to server error! {}: {}", rawCloseCode, rawCloseReason)
                null ->
                    if (rawCloseReason != null) {
                        LOG.warn("WebSocket connection closed with code {}: {}", rawCloseCode, rawCloseReason)
                    } else {
                        LOG.warn("WebSocket connection closed with unknown meaning for close-code {}", rawCloseCode)
                    }
                else -> LOG.debug("WebSocket connection closed with code {}", closeCode)
            }
        } else if (clientCloseFrame != null) {
            rawCloseCode = clientCloseFrame.closeCode
            if (rawCloseCode == GRACEFUL_CLOSE_CODE && INVALIDATE_REASON == clientCloseFrame.closeReason) {
                // When we close with 1000 we properly dropped our session due to invalidation
                // in that case we can be sure that resume will not work and instead we invalidate
                // and reconnect here
                isInvalidate = true
            }
        }

        // null is considered -reconnectable- as we do not know the close-code meaning
        val closeCodeIsReconnect = closeCode == null || closeCode.isReconnect
        if (!shouldReconnect || !closeCodeIsReconnect || executor.isShutdown) { // we should not reconnect
            ratelimitThread?.shutdown()
            ratelimitThread = null

            if (!closeCodeIsReconnect) {
                // it is possible that a token can be invalidated due to too many reconnect attempts
                // or that a bot reached a new shard minimum and cannot connect with the current
                // settings
                // if that is the case we have to drop our connection and inform the user with a
                // fatal error message
                LOG.error(
                    "WebSocket connection was closed and cannot be recovered due to identification issues\n{}",
                    closeCode,
                )

                // Forward the close reason to any hooks to awaitStatus / awaitReady
                // Since people cannot read logs, we have to explicitly forward this error.
                when (closeCode) {
                    CloseCode.SHARDING_REQUIRED, CloseCode.INVALID_SHARD -> api.shutdownReason = ShutdownReason.INVALID_SHARDS
                    CloseCode.DISALLOWED_INTENTS -> api.shutdownReason = ShutdownReason.DISALLOWED_INTENTS
                    CloseCode.GRACEFUL_CLOSE -> {}
                    else -> api.shutdownReason = ShutdownReason("Connection closed with code $closeCode")
                }
            }

            decompressor?.shutdown()

            onShutdown(rawCloseCode)
        } else {
            // reset our decompression tools
            synchronized(readLock) {
                decompressor?.reset()
            }
            if (isInvalidate) {
                invalidate() // 1000 means our session is dropped so we cannot resume
            }
            api.handleEvent(
                SessionDisconnectEvent(api, serverCloseFrame, clientCloseFrame, closedByServer, OffsetDateTime.now()),
            )
            try {
                handleReconnect(rawCloseCode)
            } catch (e: InterruptedException) {
                LOG.error("Failed to resume due to interrupted thread", e)
                invalidate()
                queueReconnect()
            }
        }
    }

    @Throws(InterruptedException::class)
    private fun handleReconnect(code: Int) {
        if (sessionId == null) {
            if (handleIdentifyRateLimit) {
                val backoff = calculateIdentifyBackoff()
                if (backoff > 0) {
                    // it seems that most of the time this is already sub-0 when we reach this point
                    LOG.error("Encountered IDENTIFY Rate Limit! Waiting {} milliseconds before trying again!", backoff)
                    Thread.sleep(backoff)
                } else {
                    LOG.error("Encountered IDENTIFY Rate Limit!")
                }
            }
            LOG.warn("Got disconnected from WebSocket (Code {}). Appending to reconnect queue", code)
            queueReconnect()
        } else { // if resume is possible
            LOG.debug("Got disconnected from WebSocket (Code: {}). Attempting to resume session", code)
            reconnect()
        }
    }

    protected fun calculateIdentifyBackoff(): Long {
        val currentTime = System.currentTimeMillis()
        // calculate remaining backoff time since identify
        return currentTime - (identifyTime + IDENTIFY_BACKOFF)
    }

    @Suppress("SwallowedException") // the original logs a fixed message and shuts down, the cause is not used
    protected fun queueReconnect() {
        try {
            api.setStatus(JDA.Status.RECONNECT_QUEUED)
            connectNode = ReconnectNode()
            api.sessionController.appendSession(connectNode!!)
        } catch (ex: IllegalStateException) {
            LOG.error("Reconnect queue rejected session. Shutting down...")
            api.setStatus(JDA.Status.SHUTDOWN)
            api.handleEvent(ShutdownEvent(api, OffsetDateTime.now(), ABNORMAL_CLOSE_CODE))
        }
    }

    @Throws(InterruptedException::class)
    protected fun reconnect() {
        reconnect(false)
    }

    /**
     * This method is used to start the reconnect of the JDA instance.
     * It is public for access from SessionReconnectQueue extensions.
     *
     * @param callFromQueue
     *         whether this was in SessionReconnectQueue and got polled
     */
    @Throws(InterruptedException::class)
    @Suppress(
        "TooGenericExceptionCaught", // mirrors the Java catch of RuntimeException during reconnect
        "SwallowedException", // RejectedExecutionException/InterruptedException signal shutdown, no cause needed
        "LoopWithTooManyJumpStatements", // faithful port of the Java reconnect loop's break/return flow
    )
    fun reconnect(callFromQueue: Boolean) {
        var contextEntries: Set<MDC.MDCCloseable>? = null
        var previousContext: Map<String, String>? = null
        run {
            val contextMap = api.contextMap
            if (callFromQueue && contextMap != null) {
                previousContext = MDC.getCopyOfContextMap()
                contextEntries =
                    contextMap.entries
                        .stream()
                        .map { (key, value) -> MDC.putCloseable(key, value) }
                        .collect(Collectors.toSet())
            }
        }

        var message = ""
        if (callFromQueue) {
            message =
                "Queue is attempting to reconnect a shard...%s ".format(
                    " Shard: " + shardInfo.shardString,
                )
        }
        if (sessionId != null) {
            reconnectTimeoutS = 0
        }
        LOG.debug("{}Attempting to reconnect in {}s", message, reconnectTimeoutS)
        val isShutdown =
            MiscUtil.locked(
                reconnectLock,
                Supplier {
                    while (shouldReconnect) {
                        api.setStatus(JDA.Status.WAITING_TO_RECONNECT)

                        val delay = reconnectTimeoutS
                        // Exponential backoff, reset on session creation (ready/resume)
                        reconnectTimeoutS =
                            if (reconnectTimeoutS ==
                                0
                            ) {
                                RECONNECT_TIMEOUT_INITIAL_S
                            } else {
                                minOf(reconnectTimeoutS shl 1, api.maxReconnectDelay)
                            }

                        try {
                            // On shutdown, this condvar is notified and we stop reconnecting
                            reconnectCondvar.await(delay.toLong(), TimeUnit.SECONDS)
                            if (!shouldReconnect) {
                                break
                            }

                            handleIdentifyRateLimit = false
                            api.setStatus(JDA.Status.ATTEMPTING_TO_RECONNECT)
                            LOG.debug("Attempting to reconnect!")
                            connect()
                            break
                        } catch (ex: RejectedExecutionException) {
                            // JDA has already been shutdown so we can stop here
                            return@Supplier true
                        } catch (ex: InterruptedException) {
                            // JDA has already been shutdown so we can stop here
                            return@Supplier true
                        } catch (ex: RuntimeException) {
                            LOG.debug("Reconnect failed with exception", ex)
                            LOG.warn("Reconnect failed! Next attempt in {}s", reconnectTimeoutS)
                        }
                    }
                    !shouldReconnect
                },
            )

        if (isShutdown) {
            LOG.debug("Reconnect cancelled due to shutdown.")
            shutdown()
        }

        contextEntries?.forEach { it.close() }
        previousContext?.forEach { (key, value) -> MDC.put(key, value) }
    }

    protected fun setupKeepAlive(timeout: Int) {
        try {
            socket?.socket?.soTimeout = timeout + SOCKET_TIMEOUT_MS
        } catch (ex: SocketException) {
            LOG.warn("Failed to setup timeout for socket", ex)
        }

        keepAliveThread =
            executor.scheduleAtFixedRate(
                {
                    api.setContext()
                    if (connected) {
                        sendKeepAlive()
                    }
                },
                0,
                timeout.toLong(),
                TimeUnit.MILLISECONDS,
            )
    }

    protected fun sendKeepAlive() {
        val keepAlivePacket = DataObject.empty().put("op", WebSocketCode.HEARTBEAT).put("d", api.responseTotal)

        if (missedHeartbeats >= MISSED_HEARTBEAT_THRESHOLD) {
            missedHeartbeats = 0
            LOG.warn("Missed 2 heartbeats! Trying to reconnect...")
            prepareClose()
            socket!!.disconnect(RECONNECT_CLOSE_CODE, "ZOMBIE CONNECTION")
        } else {
            missedHeartbeats += 1
            send(keepAlivePacket, true)
            heartbeatStartTime = System.currentTimeMillis()
        }
    }

    protected fun sendIdentify() {
        LOG.debug("Sending Identify-packet...")
        val presenceObj = api.presence as PresenceImpl
        val connectionProperties =
            DataObject
                .empty()
                .put("os", System.getProperty("os.name"))
                .put("browser", "JDA")
                .put("device", "JDA")
        val payload =
            DataObject
                .empty()
                .put("presence", presenceObj.getFullPresence())
                .put("token", getToken())
                .put("properties", connectionProperties)
                .put("large_threshold", api.largeThreshold)
                .put("intents", gatewayIntents)

        val identify = DataObject.empty().put("op", WebSocketCode.IDENTIFY).put("d", payload)
        payload.put("shard", DataArray.empty().add(shardInfo.shardId).add(shardInfo.shardTotal))
        send(identify, true)
        handleIdentifyRateLimit = true
        identifyTime = System.currentTimeMillis()
        sentAuthInfo = true
        api.setStatus(JDA.Status.AWAITING_LOGIN_CONFIRMATION)
    }

    protected fun sendResume() {
        LOG.debug("Sending Resume-packet...")
        val resume =
            DataObject
                .empty()
                .put("op", WebSocketCode.RESUME)
                .put(
                    "d",
                    DataObject
                        .empty()
                        .put("session_id", sessionId)
                        .put("token", getToken())
                        .put("seq", api.responseTotal),
                )
        send(resume, true)
        // sentAuthInfo = true; set on RESUMED response as this could fail
        api.setStatus(JDA.Status.AWAITING_LOGIN_CONFIRMATION)
    }

    protected fun invalidate() {
        resumeUrl = null
        sessionId = null
        sentAuthInfo = false

        locked("Interrupted while trying to invalidate chunk/sync queue", Runnable { chunkSyncQueue.clear() })

        api.channelsView.clear()

        api.guildsView.clear()
        api.usersView.clear()

        api.eventCache.clear()
        api.guildSetupController.clearCache()
        chunkManager.clear()

        api.handleEvent(SessionInvalidateEvent(api))
    }

    protected fun updateAudioManagerReferences() {
        val managerView: AbstractCacheView<AudioManager> = api.audioManagersView
        managerView.writeLock().use {
            val managerMap = managerView.getMap()
            if (managerMap.size() > 0) {
                LOG.trace("Updating AudioManager references")
            }

            val it: TLongObjectIterator<AudioManager> = managerMap.iterator()
            while (it.hasNext()) {
                it.advance()
                val guildId = it.key()
                val mng = it.value() as AudioManagerImpl

                val guild = api.getGuildById(guildId) as GuildImpl?
                if (guild == null) {
                    // We no longer have access to the guild that this audio manager was for. Set
                    // the value to null.
                    queuedAudioConnections.remove(guildId)
                    mng.closeAudioConnection(ConnectionStatus.DISCONNECTED_REMOVED_DURING_RECONNECT)
                    it.remove()
                }
            }
        }
    }

    protected fun getToken(): String =
        // all bot tokens are prefixed with "Bot "
        api.token.substring("Bot ".length)

    protected fun convertPresencesReplace(
        responseTotal: Long,
        array: DataArray,
    ): List<DataObject> {
        // Needs special handling due to content of "d" being an array
        val output = ArrayList<DataObject>()
        for (i in 0 until array.length()) {
            val presence = array.getObject(i)
            val obj = DataObject.empty()
            obj
                .put("comment", "This was constructed from a PRESENCES_REPLACE payload")
                .put("op", WebSocketCode.DISPATCH)
                .put("s", responseTotal)
                .put("d", presence)
                .put("t", "PRESENCE_UPDATE")
            output.add(obj)
        }
        return output
    }

    @Suppress("TooGenericExceptionCaught") // event handling must not let a listener exception kill the gateway thread
    protected fun handleEvent(content: DataObject) {
        try {
            onEvent(content)
        } catch (ex: Exception) {
            LOG.error("Encountered exception on lifecycle level\nJSON: {}", content, ex)
            api.handleEvent(ExceptionEvent(api, ex, true))
        }
    }

    protected fun onEvent(content: DataObject) {
        WS_THREAD.set(true)
        val opCode = content.getInt("op")

        if (!content.isNull("s")) {
            api.setResponseTotal(content.getInt("s"))
        }

        when (opCode) {
            WebSocketCode.DISPATCH -> onDispatch(content)
            WebSocketCode.HEARTBEAT -> {
                LOG.debug("Got Keep-Alive request (OP 1). Sending response...")
                sendKeepAlive()
            }
            WebSocketCode.RECONNECT -> {
                LOG.debug("Got Reconnect request (OP 7). Closing connection now...")
                close(RECONNECT_CLOSE_CODE, "OP 7: RECONNECT")
            }
            WebSocketCode.INVALIDATE_SESSION -> {
                LOG.debug("Got Invalidate request (OP 9). Invalidating...")
                val identifyRateLimited = System.currentTimeMillis() - identifyTime < IDENTIFY_BACKOFF
                handleIdentifyRateLimit = handleIdentifyRateLimit && identifyRateLimited

                sentAuthInfo = false
                val isResume = content.getBoolean("d")
                // When d: true we can wait a bit and then try to resume again
                // sending 4000 to not drop session
                val closeCode = if (isResume) RECONNECT_CLOSE_CODE else GRACEFUL_CLOSE_CODE
                if (isResume) {
                    LOG.debug("Session can be recovered... Closing and sending new RESUME request")
                } else {
                    invalidate()
                }

                close(closeCode, INVALIDATE_REASON)
            }
            WebSocketCode.HELLO -> {
                LOG.debug("Got HELLO packet (OP 10). Initializing keep-alive.")
                val data = content.getObject("d")
                setupKeepAlive(data.getInt("heartbeat_interval"))
            }
            WebSocketCode.HEARTBEAT_ACK -> {
                LOG.trace("Got Heartbeat Ack (OP 11).")
                missedHeartbeats = 0
                api.setGatewayPing(System.currentTimeMillis() - heartbeatStartTime)
            }
            else -> LOG.debug("Got unknown op-code: {} with content: {}", opCode, content)
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught") // faithful port of the Java dispatch switch
    protected fun onDispatch(raw: DataObject) {
        val type = raw.getString("t")
        val responseTotal = api.responseTotal

        if (!raw.isType("d", DataType.OBJECT)) {
            // Needs special handling due to content of "d" being an array
            if (type == "PRESENCES_REPLACE") {
                val payload = raw.getArray("d")
                val converted = convertPresencesReplace(responseTotal, payload)
                val handler = getHandler<SocketHandler>("PRESENCE_UPDATE")
                LOG.trace("{} -> {}", type, payload)
                for (o in converted) {
                    handler.handle(responseTotal, o)
                    // Send raw event after cache has been updated - including comment
                    if (api.isRawEvents) {
                        api.handleEvent(RawGatewayEvent(api, responseTotal, o))
                    }
                }
            } else {
                LOG.debug("Received event with unhandled body type JSON: {}", raw)
            }
            return
        }

        val content = raw.getObject("d")
        LOG.trace("{} -> {}", type, content)

        val jda = api
        try {
            when (type) {
                // INIT types
                "READY" -> {
                    reconnectTimeoutS = RECONNECT_TIMEOUT_INITIAL_S
                    api.setStatus(JDA.Status.LOADING_SUBSYSTEMS)
                    processingReady = true
                    handleIdentifyRateLimit = false
                    // first handle the ready payload before applying the session id
                    // this prevents a possible race condition with the cache of the guild setup
                    // controller
                    // otherwise the audio connection requests that are currently pending might be
                    // removed in the process
                    handlers["READY"]!!.handle(responseTotal, raw)
                    sessionId = content.getString("session_id")
                    resumeUrl = content.getString("resume_gateway_url", null)
                    traceMetadata = content.opt("_trace").map { java.lang.String.valueOf(it) }.orElse(null)
                    LOG.debug("Received READY with _trace {}", traceMetadata)
                }
                "RESUMED" -> {
                    reconnectTimeoutS = RECONNECT_TIMEOUT_INITIAL_S
                    sentAuthInfo = true
                    traceMetadata = content.opt("_trace").map { java.lang.String.valueOf(it) }.orElse(traceMetadata)
                    if (!processingReady) {
                        initiating = false
                        ready()
                    } else {
                        LOG.debug("Resumed while still processing initial ready")
                        jda.setStatus(JDA.Status.LOADING_SUBSYSTEMS)
                    }
                }
                else -> {
                    val guildId = content.getLong("guild_id", 0L)
                    if (api.isUnavailable(guildId) && type != "GUILD_CREATE" && type != "GUILD_DELETE") {
                        LOG.debug("Ignoring {} for unavailable guild with id {}. JSON: {}", type, guildId, content)
                    } else {
                        val handler = handlers[type]
                        if (handler != null) {
                            handler.handle(responseTotal, raw)
                        } else {
                            LOG.debug("Unrecognized event:\n{}", raw)
                        }
                    }
                }
            }
            // Send raw event after cache has been updated
            if (api.isRawEvents) {
                api.handleEvent(RawGatewayEvent(api, responseTotal, raw))
            }
        } catch (ex: ParsingException) {
            LOG.warn(
                "Got an unexpected Json-parse error. Please redirect the following message to the devs:\n\tJDA {}\n\t{}\n\t{} -> {}",
                JDAInfo.VERSION,
                ex.message,
                type,
                content,
                ex,
            )
        } catch (ex: Exception) {
            LOG.error(
                "Got an unexpected error. Please redirect the following message to the devs:\n\tJDA {}\n\t{} -> {}",
                JDAInfo.VERSION,
                type,
                content,
                ex,
            )
        }

        if (responseTotal % EventCache.TIMEOUT_AMOUNT == 0L) {
            jda.eventCache.timeout(responseTotal)
        }
    }

    override fun onTextMessage(
        websocket: WebSocket,
        data: ByteArray,
    ) {
        handleEvent(DataObject.fromJson(data))
    }

    @Throws(DataFormatException::class)
    override fun onBinaryMessage(
        websocket: WebSocket,
        binary: ByteArray,
    ) {
        val message: DataObject?
        // Only acquire lock for decompression and unlock for event handling
        synchronized(readLock) {
            message = handleBinary(binary)
        }
        if (message != null) {
            handleEvent(message)
        }
    }

    @Suppress("ReturnCount", "ThrowsCount") // faithful port of the original's early returns and rethrows
    @Throws(DataFormatException::class)
    protected fun handleBinary(binary: ByteArray): DataObject? {
        if (decompressor == null) {
            if (encoding == GatewayEncoding.ETF) {
                return DataObject.fromETF(binary)
            }
            throw IllegalStateException(
                "Cannot decompress binary message due to unknown compression algorithm: " + compression,
            )
        }
        // Scoping allows us to print the json that possibly failed parsing
        val data: ByteArray
        try {
            data = decompressor!!.decompress(binary) ?: return null
        } catch (e: DataFormatException) {
            close(RECONNECT_CLOSE_CODE, "MALFORMED_PACKAGE")
            throw e
        }

        try {
            return if (encoding == GatewayEncoding.ETF) {
                DataObject.fromETF(data)
            } else {
                DataObject.fromJson(data)
            }
        } catch (e: ParsingException) {
            var jsonString = "malformed"
            try {
                jsonString = String(data, StandardCharsets.UTF_8)
            } catch (ignored: Exception) {
                // fall through with the placeholder
            }
            // Print the string that could not be parsed and re-throw the exception
            LOG.error("Failed to parse json: {}", jsonString)
            throw e
        }
    }

    @Throws(Exception::class)
    override fun handleCallbackError(
        websocket: WebSocket,
        cause: Throwable,
    ) {
        handleError(cause)
    }

    @Throws(Exception::class)
    override fun onError(
        websocket: WebSocket,
        cause: WebSocketException,
    ) {
        handleError(cause)
    }

    private fun handleError(cause: Throwable) {
        if (cause.cause is SocketTimeoutException) {
            LOG.debug("Socket timed out")
        } else if (cause.cause is IOException) {
            LOG.debug("Encountered I/O error", cause)
        } else {
            LOG.error("There was an error in the WebSocket connection. Trace: {}", traceMetadata, cause)
            api.handleEvent(ExceptionEvent(api, cause, true))
        }
    }

    @Throws(Exception::class)
    override fun onThreadCreated(
        websocket: WebSocket,
        threadType: ThreadType,
        thread: Thread,
    ) {
        val identifier = api.identifierString
        when (threadType) {
            ThreadType.CONNECT_THREAD -> thread.name = "$identifier MainWS-ConnectThread"
            ThreadType.FINISH_THREAD -> thread.name = "$identifier MainWS-FinishThread"
            ThreadType.READING_THREAD -> thread.name = "$identifier MainWS-ReadThread"
            ThreadType.WRITING_THREAD -> thread.name = "$identifier MainWS-WriteThread"
        }
    }

    @Suppress("TooGenericExceptionCaught") // the lock wrapper logs any failure rather than propagating it
    protected fun locked(
        comment: String,
        task: Runnable,
    ) {
        try {
            MiscUtil.locked(queueLock, task)
        } catch (e: Exception) {
            LOG.error(comment, e)
        }
    }

    @Suppress("TooGenericExceptionCaught") // the lock wrapper logs any failure rather than propagating it
    protected fun <T> locked(
        comment: String,
        task: Supplier<T>,
    ): T? =
        try {
            MiscUtil.locked(queueLock, task)
        } catch (e: Exception) {
            LOG.error(comment, e)
            null
        }

    fun queueAudioReconnect(channel: AudioChannel) {
        locked(
            "There was an error queueing the audio reconnect",
            Runnable {
                val guildId = channel.guild.idLong
                var request = queuedAudioConnections.get(guildId)

                if (request == null) {
                    // If no request, then just reconnect
                    request = ConnectionRequest(channel, ConnectionStage.RECONNECT)
                    queuedAudioConnections.put(guildId, request)
                } else {
                    // If there is a request we change it to reconnect, no matter what it is
                    request.setStage(ConnectionStage.RECONNECT)
                }
                // in all cases, update to this channel
                request.setChannel(channel)
            },
        )
    }

    fun queueAudioConnect(channel: AudioChannel) {
        locked(
            "There was an error queueing the audio connect",
            Runnable {
                val guildId = channel.guild.idLong
                var request = queuedAudioConnections.get(guildId)

                if (request == null) {
                    // starting a whole new connection
                    request = ConnectionRequest(channel, ConnectionStage.CONNECT)
                    queuedAudioConnections.put(guildId, request)
                } else if (request.getStage() == ConnectionStage.DISCONNECT) {
                    // if planned to disconnect, we want to reconnect
                    request.setStage(ConnectionStage.RECONNECT)
                }

                // in all cases, update to this channel
                request.setChannel(channel)
            },
        )
    }

    fun queueAudioDisconnect(guild: Guild) {
        locked(
            "There was an error queueing the audio disconnect",
            Runnable {
                val guildId = guild.idLong
                val request = queuedAudioConnections.get(guildId)

                if (request == null) {
                    // If we do not have a request
                    queuedAudioConnections.put(guildId, ConnectionRequest(guild))
                } else {
                    // If we have a request, change to DISCONNECT
                    request.setStage(ConnectionStage.DISCONNECT)
                }
            },
        )
    }

    fun removeAudioConnection(guildId: Long): ConnectionRequest? {
        // This will only be used by GuildDeleteHandler to ensure that
        // no further voice state updates are sent for this Guild
        return locked(
            "There was an error cleaning up audio connections for deleted guild",
            Supplier { queuedAudioConnections.remove(guildId) },
        )
    }

    fun updateAudioConnection(
        guildId: Long,
        connectedChannel: AudioChannel?,
    ): ConnectionRequest? =
        locked(
            "There was an error updating the audio connection",
            Supplier { updateAudioConnection0(guildId, connectedChannel) },
        )

    @Suppress("ReturnCount") // faithful port of the Java fallthrough switch
    fun updateAudioConnection0(
        guildId: Long,
        connectedChannel: AudioChannel?,
    ): ConnectionRequest? {
        // Called by VoiceStateUpdateHandler when we receive a response from discord
        // about our request to CONNECT or DISCONNECT.
        // "stage" should never be RECONNECT here thus we don't check for that case
        val request = queuedAudioConnections.get(guildId)

        if (request == null) {
            return null
        }
        val requestStage = request.getStage()
        if (connectedChannel == null) {
            // If we got an update that DISCONNECT happened
            // -> If it was on RECONNECT we now switch to CONNECT
            // -> If it was on DISCONNECT we can now remove it
            // -> Otherwise we ignore it
            when (requestStage) {
                ConnectionStage.DISCONNECT -> return queuedAudioConnections.remove(guildId)
                ConnectionStage.RECONNECT -> {
                    request.setStage(ConnectionStage.CONNECT)
                    request.setNextAttemptEpoch(System.currentTimeMillis())
                    return null
                }
                else -> return null
            }
        } else if (requestStage == ConnectionStage.CONNECT) {
            // If the removeRequest was related to a channel that isn't the currently queued
            // request, then don't remove it.
            if (request.getChannelId() == connectedChannel.idLong) {
                return queuedAudioConnections.remove(guildId)
            }
        }
        // If the channel is not the one we are looking for!
        return null
    }

    @JvmName("getNextAudioConnectRequest")
    internal fun getNextAudioConnectRequest(): ConnectionRequest? {
        // Don't try to setup audio connections before JDA has finished loading.
        if (sessionId == null) {
            return null
        }

        val now = System.currentTimeMillis()
        val request = AtomicReference<ConnectionRequest>()
        queuedAudioConnections.retainEntries { guildId, audioRequest ->
            // we use this because it locks the mutex
            if (audioRequest.getNextAttemptEpoch() < now) {
                // Check if the guild is ready
                val guild = api.getGuildById(guildId)
                if (guild == null) {
                    // Not yet ready, check if the guild is known to this shard
                    val controller = api.guildSetupController
                    if (!controller.isKnown(guildId)) {
                        // The guild is not tracked anymore
                        //   -> we can't connect the audio channel
                        LOG.debug(
                            "Removing audio connection request because the guild has been removed. {}",
                            audioRequest,
                        )
                        return@retainEntries false
                    }
                    return@retainEntries true
                }

                val listener: ConnectionListener? = guild.audioManager.connectionListener
                if (audioRequest.getStage() != ConnectionStage.DISCONNECT) {
                    // Check if we can connect to the target channel
                    val channel = guild.getGuildChannelById(audioRequest.getChannelId()) as AudioChannel?
                    if (channel == null) {
                        listener?.onStatusChange(ConnectionStatus.DISCONNECTED_CHANNEL_DELETED)
                        return@retainEntries false
                    }

                    if (!guild.selfMember.hasPermission(channel, Permission.VOICE_CONNECT)) {
                        listener?.onStatusChange(ConnectionStatus.DISCONNECTED_LOST_PERMISSION)
                        return@retainEntries false
                    }
                }
                // This will take the first result
                request.compareAndSet(null, audioRequest)
            }
            true
        }

        return request.get()
    }

    fun getHandlers(): Map<String, SocketHandler> = handlers

    @Suppress("UNCHECKED_CAST") // generic handler lookup, mirrors the Java unchecked cast
    fun <T : SocketHandler> getHandler(type: String): T =
        try {
            handlers[type] as T
        } catch (e: ClassCastException) {
            throw IllegalStateException(e)
        }

    protected fun setupHandlers() {
        val nopHandler = SocketHandler.NOPHandler(api)
        handlers["APPLICATION_COMMAND_PERMISSIONS_UPDATE"] = ApplicationCommandPermissionsUpdateHandler(api)
        handlers["AUTO_MODERATION_RULE_CREATE"] = AutoModRuleHandler(api, "CREATE")
        handlers["AUTO_MODERATION_RULE_UPDATE"] = AutoModRuleHandler(api, "UPDATE")
        handlers["AUTO_MODERATION_RULE_DELETE"] = AutoModRuleHandler(api, "DELETE")
        handlers["AUTO_MODERATION_ACTION_EXECUTION"] = AutoModExecutionHandler(api)
        handlers["CHANNEL_CREATE"] = ChannelCreateHandler(api)
        handlers["CHANNEL_DELETE"] = ChannelDeleteHandler(api)
        handlers["CHANNEL_UPDATE"] = ChannelUpdateHandler(api)
        handlers["ENTITLEMENT_CREATE"] = EntitlementCreateHandler(api)
        handlers["ENTITLEMENT_UPDATE"] = EntitlementUpdateHandler(api)
        handlers["ENTITLEMENT_DELETE"] = EntitlementDeleteHandler(api)
        handlers["GUILD_AUDIT_LOG_ENTRY_CREATE"] = GuildAuditLogEntryCreateHandler(api)
        handlers["GUILD_BAN_ADD"] = GuildBanHandler(api, true)
        handlers["GUILD_BAN_REMOVE"] = GuildBanHandler(api, false)
        handlers["GUILD_CREATE"] = GuildCreateHandler(api)
        handlers["GUILD_DELETE"] = GuildDeleteHandler(api)
        handlers["GUILD_EMOJIS_UPDATE"] = GuildEmojisUpdateHandler(api)
        handlers["GUILD_SCHEDULED_EVENT_CREATE"] = ScheduledEventCreateHandler(api)
        handlers["GUILD_SCHEDULED_EVENT_UPDATE"] = ScheduledEventUpdateHandler(api)
        handlers["GUILD_SCHEDULED_EVENT_DELETE"] = ScheduledEventDeleteHandler(api)
        handlers["GUILD_SCHEDULED_EVENT_USER_ADD"] = ScheduledEventUserHandler(api, true)
        handlers["GUILD_SCHEDULED_EVENT_USER_REMOVE"] = ScheduledEventUserHandler(api, false)
        handlers["GUILD_MEMBER_ADD"] = GuildMemberAddHandler(api)
        handlers["GUILD_MEMBER_REMOVE"] = GuildMemberRemoveHandler(api)
        handlers["GUILD_MEMBER_UPDATE"] = GuildMemberUpdateHandler(api)
        handlers["GUILD_MEMBERS_CHUNK"] = GuildMembersChunkHandler(api)
        handlers["GUILD_ROLE_CREATE"] = GuildRoleCreateHandler(api)
        handlers["GUILD_ROLE_DELETE"] = GuildRoleDeleteHandler(api)
        handlers["GUILD_ROLE_UPDATE"] = GuildRoleUpdateHandler(api)
        handlers["GUILD_SYNC"] = GuildSyncHandler(api)
        handlers["GUILD_STICKERS_UPDATE"] = GuildStickersUpdateHandler(api)
        handlers["GUILD_SOUNDBOARD_SOUND_CREATE"] = GuildSoundboardSoundCreateHandler(api)
        val soundboardSoundUpdateHandler = GuildSoundboardSoundUpdateHandler(api)
        handlers["GUILD_SOUNDBOARD_SOUND_UPDATE"] = soundboardSoundUpdateHandler
        handlers["GUILD_SOUNDBOARD_SOUNDS_UPDATE"] =
            GuildSoundboardSoundsUpdateHandler(api, soundboardSoundUpdateHandler)
        handlers["GUILD_SOUNDBOARD_SOUND_DELETE"] = GuildSoundboardSoundDeleteHandler(api)
        handlers["VOICE_CHANNEL_EFFECT_SEND"] = VoiceChannelEffectSendHandler(api)
        handlers["GUILD_UPDATE"] = GuildUpdateHandler(api)
        handlers["INTERACTION_CREATE"] = InteractionCreateHandler(api)
        handlers["INVITE_CREATE"] = InviteCreateHandler(api)
        handlers["INVITE_DELETE"] = InviteDeleteHandler(api)
        handlers["MESSAGE_CREATE"] = MessageCreateHandler(api)
        handlers["MESSAGE_DELETE"] = MessageDeleteHandler(api)
        handlers["MESSAGE_DELETE_BULK"] = MessageBulkDeleteHandler(api)
        handlers["MESSAGE_REACTION_ADD"] = MessageReactionHandler(api, true)
        handlers["MESSAGE_REACTION_REMOVE"] = MessageReactionHandler(api, false)
        handlers["MESSAGE_REACTION_REMOVE_ALL"] = MessageReactionBulkRemoveHandler(api)
        handlers["MESSAGE_REACTION_REMOVE_EMOJI"] = MessageReactionClearEmojiHandler(api)
        handlers["MESSAGE_POLL_VOTE_ADD"] = MessagePollVoteHandler(api, true)
        handlers["MESSAGE_POLL_VOTE_REMOVE"] = MessagePollVoteHandler(api, false)
        handlers["MESSAGE_UPDATE"] = MessageUpdateHandler(api)
        handlers["PRESENCE_UPDATE"] = PresenceUpdateHandler(api)
        handlers["READY"] = ReadyHandler(api)
        handlers["STAGE_INSTANCE_CREATE"] = StageInstanceCreateHandler(api)
        handlers["STAGE_INSTANCE_DELETE"] = StageInstanceDeleteHandler(api)
        handlers["STAGE_INSTANCE_UPDATE"] = StageInstanceUpdateHandler(api)
        handlers["THREAD_CREATE"] = ThreadCreateHandler(api)
        handlers["THREAD_DELETE"] = ThreadDeleteHandler(api)
        handlers["THREAD_LIST_SYNC"] = ThreadListSyncHandler(api)
        handlers["THREAD_MEMBERS_UPDATE"] = ThreadMembersUpdateHandler(api)
        handlers["THREAD_MEMBER_UPDATE"] = ThreadMemberUpdateHandler(api)
        handlers["THREAD_UPDATE"] = ThreadUpdateHandler(api)
        handlers["TYPING_START"] = TypingStartHandler(api)
        handlers["USER_UPDATE"] = UserUpdateHandler(api)
        handlers["VOICE_SERVER_UPDATE"] = VoiceServerUpdateHandler(api)
        handlers["VOICE_STATE_UPDATE"] = VoiceStateUpdateHandler(api)
        handlers["VOICE_CHANNEL_STATUS_UPDATE"] = VoiceChannelStatusUpdateHandler(api)

        // Unused events
        handlers["CHANNEL_PINS_ACK"] = nopHandler
        handlers["CHANNEL_PINS_UPDATE"] = nopHandler
        handlers["GUILD_INTEGRATIONS_UPDATE"] = nopHandler
        handlers["PRESENCES_REPLACE"] = nopHandler
        handlers["WEBHOOKS_UPDATE"] = nopHandler
    }

    protected abstract inner class ConnectNode : SessionController.SessionConnectNode {
        @Nonnull
        override fun getJDA(): JDA = api

        @Nonnull
        override fun getShardInfo(): JDA.ShardInfo = api.shardInfo
    }

    protected open inner class StartingNode : ConnectNode() {
        override fun isReconnect(): Boolean = false

        @Throws(InterruptedException::class)
        override fun run(isLast: Boolean) {
            if (shutdown) {
                return
            }
            setupSendingThread()
            connect()
            if (isLast) {
                return
            }
            try {
                api.awaitStatus(JDA.Status.LOADING_SUBSYSTEMS, JDA.Status.RECONNECT_QUEUED)
            } catch (ex: IllegalStateException) {
                close()
                LOG.debug("Shutdown while trying to connect: {}", ex.message)
            }
        }

        override fun hashCode(): Int = Objects.hash("C", getJDA())

        override fun equals(other: Any?): Boolean =
            if (other === this) {
                true
            } else if (other !is StartingNode) {
                false
            } else {
                other.getJDA() == getJDA()
            }
    }

    protected open inner class ReconnectNode : ConnectNode() {
        override fun isReconnect(): Boolean = true

        @Throws(InterruptedException::class)
        override fun run(isLast: Boolean) {
            if (shutdown) {
                return
            }
            reconnect(true)
            if (isLast) {
                return
            }
            try {
                api.awaitStatus(JDA.Status.LOADING_SUBSYSTEMS, JDA.Status.RECONNECT_QUEUED)
            } catch (ex: IllegalStateException) {
                close()
                LOG.debug("Shutdown while trying to reconnect: {}", ex.message)
            }
        }

        override fun hashCode(): Int = Objects.hash("R", getJDA())

        override fun equals(other: Any?): Boolean =
            if (other === this) {
                true
            } else if (other !is ReconnectNode) {
                false
            } else {
                other.getJDA() == getJDA()
            }
    }

    companion object {
        @JvmField
        val WS_THREAD: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

        @JvmField
        val LOG: Logger = JDALogger.getLog(WebSocketClient::class.java)

        protected const val INVALIDATE_REASON = "INVALIDATE_SESSION"
        protected const val IDENTIFY_BACKOFF = SessionController.IDENTIFY_DELAY * 1000L

        private const val RECONNECT_TIMEOUT_INITIAL_S = 2
        private const val MISSED_HEARTBEAT_THRESHOLD = 2
        private const val LARGE_GUILD_COUNT = 2000
        private const val RATE_LIMIT_RESET_MS = 60000L
        private const val RATE_LIMIT_MESSAGES = 115
        private const val RATE_LIMIT_MESSAGES_SKIP_QUEUE = 119
        private const val SOCKET_TIMEOUT_MS = 10000
        private const val SOCKET_TIMEOUT_MIN_MS = 1000
        private const val DEFAULT_CLOSE_CODE = 1005
        private const val GRACEFUL_CLOSE_CODE = 1000
        private const val ABNORMAL_CLOSE_CODE = 1006
        private const val RECONNECT_CLOSE_CODE = 4900
    }
}
