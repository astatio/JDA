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

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.dv8tion.jda.api.events.guild.GuildTimeoutEvent
import net.dv8tion.jda.api.events.guild.UnavailableGuildLeaveEvent
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.requests.MemberChunkManager
import net.dv8tion.jda.internal.requests.WebSocketClient
import net.dv8tion.jda.internal.utils.JDALogger
import org.slf4j.Logger
import java.util.HashSet
import java.util.Objects
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

open class GuildSetupController(
    private val api: JDAImpl,
) {
    private val setupNodes: TLongObjectMap<GuildSetupNode> = TLongObjectHashMap()
    private val chunkingGuilds: TLongSet = TLongHashSet()
    private val unavailableGuilds: TLongSet = TLongHashSet()

    // The TODO below is ported verbatim from the Java original; it records a real upstream follow-up.
    @Suppress("ForbiddenComment")
    // TODO: Rewrite this incompleteCount system to just rely on the state of each node
    private var incompleteCount = 0

    private var timeoutHandle: Future<*>? = null

    internal var listener: StatusListener =
        StatusListener { id, oldStatus, newStatus -> log.trace("[{}] Updated status {}->{}", id, oldStatus, newStatus) }

    internal fun getJDA(): JDAImpl = api

    internal fun addGuildForChunking(
        id: Long,
        join: Boolean,
    ) {
        log.trace("Adding guild for chunking ID: {}", id)
        if (join || incompleteCount <= 0) {
            if (incompleteCount <= 0) {
                // this happens during runtime -> chunk right away
                sendChunkRequest(id)
                return
            }
            incompleteCount++
        }
        chunkingGuilds.add(id)
        tryChunking()
    }

    internal fun remove(id: Long) {
        unavailableGuilds.remove(id)
        setupNodes.remove(id)
        chunkingGuilds.remove(id)
        checkReady()
    }

    fun ready(id: Long) {
        remove(id)
        incompleteCount--
        checkReady()
    }

    // Check if we can send a ready event
    private fun checkReady() {
        val client: WebSocketClient = getJDA().getClient()
        // If no guilds are marked as incomplete we can fire a ready
        if (incompleteCount < 1 && !client.isReady()) {
            timeoutHandle?.cancel(false)
            timeoutHandle = null
            client.ready()
        } else if (incompleteCount <= TIMEOUT_THRESHOLD) {
            startTimeout() // try to timeout the other guilds
        }
    }

    fun setIncompleteCount(count: Int): Boolean {
        this.incompleteCount = count
        log.debug("Setting incomplete count to {}", incompleteCount)
        checkReady()
        return count != 0
    }

    fun onReady(
        id: Long,
        obj: DataObject,
    ) {
        log.trace("Adding id to setup cache {}", id)
        val node = GuildSetupNode(id, this, GuildSetupNode.Type.INIT)
        setupNodes.put(id, node)
        node.handleReady(obj)
        if (node.markedUnavailable) {
            incompleteCount--
            tryChunking()
        }
    }

    fun onCreate(
        id: Long,
        obj: DataObject,
    ) {
        val available = obj.isNull("unavailable") || !obj.getBoolean("unavailable")
        log.trace("Received guild create for id: {} available: {}", id, available)

        if (available && unavailableGuilds.contains(id) && !setupNodes.containsKey(id)) {
            // Guild was unavailable for a moment, its back now so initialize it again!
            unavailableGuilds.remove(id)
            setupNodes.put(id, GuildSetupNode(id, this, GuildSetupNode.Type.AVAILABLE))
        }

        var node = setupNodes.get(id)
        if (node == null) {
            // this is a join event
            node = GuildSetupNode(id, this, GuildSetupNode.Type.JOIN)
            setupNodes.put(id, node)
            // do not increment incomplete counter, it is only relevant to init guilds
        } else if (node.markedUnavailable && available && incompleteCount > 0) {
            // Looks like this guild decided to become available again during startup
            // that means we can now consider it for ReadyEvent status again!
            incompleteCount++
        }
        node.handleCreate(obj)
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun onDelete(
        id: Long,
        obj: DataObject,
    ): Boolean {
        val available = obj.isNull("unavailable") || !obj.getBoolean("unavailable")
        if (isUnavailable(id) && available) {
            log.debug("Leaving unavailable guild with id {}", id)
            remove(id)
            api.getEventManager().handle(UnavailableGuildLeaveEvent(api, api.getResponseTotal(), id))
            return true
        }

        val node = setupNodes.get(id) ?: return false
        log.debug("Received guild delete for id: {} available: {}", id, available)
        if (!available) {
            // The guild is currently unavailable and should be ignored for chunking requests
            if (!node.markedUnavailable) {
                node.markedUnavailable = true // this prevents repeated decrements from duplicate events
                if (incompleteCount > 0) {
                    // Allow other guilds to start chunking
                    chunkingGuilds.remove(id)
                    incompleteCount--
                }
            }
            node.reset()
        } else {
            // This guild was deleted
            node.cleanup() // clear EventCache
            if (node.isJoin() && !node.requestedChunk) {
                remove(id)
            } else {
                ready(id)
            }
            api.getEventManager().handle(UnavailableGuildLeaveEvent(api, api.getResponseTotal(), id))
        }
        log.debug("Updated incompleteCount to {}", incompleteCount)
        checkReady()
        return true
    }

    fun onMemberChunk(
        id: Long,
        chunk: DataObject,
    ) {
        val members = chunk.getArray("members")
        val index = chunk.getInt("chunk_index")
        val count = chunk.getInt("chunk_count")
        log.debug("Received member chunk for guild id: {} size: {} index: {}/{}", id, members.length(), index, count)
        val node = setupNodes.get(id)
        if (node != null) {
            node.handleMemberChunk(MemberChunkManager.isLastChunk(chunk), members)
        }
    }

    fun onAddMember(
        id: Long,
        member: DataObject,
    ): Boolean {
        val node = setupNodes.get(id) ?: return false
        log.debug("Received GUILD_MEMBER_ADD during setup, adding member to guild. GuildID: {}", id)
        node.handleAddMember(member)
        return true
    }

    fun onRemoveMember(
        id: Long,
        member: DataObject,
    ): Boolean {
        val node = setupNodes.get(id) ?: return false
        log.debug("Received GUILD_MEMBER_REMOVE during setup, removing member from guild. GuildID: {}", id)
        node.handleRemoveMember(member)
        return true
    }

    fun onSync(
        id: Long,
        obj: DataObject,
    ) {
        setupNodes.get(id)?.handleSync(obj)
    }

    fun isLocked(id: Long): Boolean = setupNodes.containsKey(id)

    fun isUnavailable(id: Long): Boolean = unavailableGuilds.contains(id)

    fun isKnown(id: Long): Boolean = isLocked(id) || isUnavailable(id)

    fun cacheEvent(
        guildId: Long,
        event: DataObject,
    ) {
        val node = setupNodes.get(guildId)
        if (node != null) {
            node.cacheEvent(event)
        } else {
            log.warn(
                "Attempted to cache event for a guild that is not locked. {}",
                event,
                IllegalStateException("Guild is not locked for setup"),
            )
        }
    }

    fun clearCache() {
        setupNodes.clear()
        chunkingGuilds.clear()
        unavailableGuilds.clear()
        incompleteCount = 0
        close()
    }

    fun close() {
        timeoutHandle?.cancel(false)
        timeoutHandle = null
    }

    @Suppress("ReferenceEquality")
    fun containsMember(
        userId: Long,
        excludedNode: GuildSetupNode?,
    ): Boolean {
        val it = setupNodes.iterator()
        while (it.hasNext()) {
            it.advance()
            val node = it.value()
            if (node !== excludedNode && node.containsMember(userId)) {
                return true
            }
        }
        return false
    }

    fun getUnavailableGuilds(): TLongSet = unavailableGuilds

    fun getSetupNodes(): Set<GuildSetupNode> = HashSet(setupNodes.valueCollection())

    fun getSetupNodes(status: Status): Set<GuildSetupNode> = getSetupNodes().filterTo(HashSet()) { node -> node.status == status }

    fun getSetupNodeById(id: Long): GuildSetupNode? = setupNodes.get(id)

    fun getSetupNodeById(id: String): GuildSetupNode? = getSetupNodeById(MiscUtil.parseSnowflake(id))

    fun setStatusListener(listener: StatusListener) {
        this.listener = Objects.requireNonNull(listener)
    }

    // Chunking

    internal fun getIncompleteCount(): Int = incompleteCount

    internal fun getChunkingCount(): Int = chunkingGuilds.size()

    internal fun sendChunkRequest(obj: Any) {
        log.debug("Sending chunking requests for {} guilds", if (obj is DataArray) obj.length() else 1)

        getJDA()
            .getClient()
            .sendChunkRequest(
                DataObject
                    .empty()
                    .put("guild_id", obj)
                    .put("query", "")
                    .put("limit", 0),
            )
    }

    private fun tryChunking() {
        chunkingGuilds.forEach { id ->
            sendChunkRequest(id)
            true
        }
        chunkingGuilds.clear()
    }

    private fun startTimeout() {
        if (timeoutHandle != null || incompleteCount < 1) { // We don't need to start a timeout for 0 guilds
            return
        }

        log.debug("Starting {} second timeout for {} guilds", TIMEOUT_DURATION, incompleteCount)
        timeoutHandle = getJDA().getGatewayPool().schedule({ onTimeout() }, TIMEOUT_DURATION, TimeUnit.SECONDS)
    }

    fun onUnavailable(id: Long) {
        unavailableGuilds.add(id)
        log.debug("Guild with id {} is now marked unavailable. Total: {}", id, unavailableGuilds.size())
    }

    fun onTimeout() {
        if (incompleteCount < 1) {
            return
        }
        log.warn("Automatically marking {} guilds as unavailable due to timeout!", incompleteCount)
        val iterator = setupNodes.iterator()
        while (iterator.hasNext()) {
            iterator.advance()
            val node = iterator.value()
            iterator.remove()
            unavailableGuilds.add(node.getIdLong())
            // Inform users that the guild timed out
            getJDA().handleEvent(GuildTimeoutEvent(getJDA(), node.getIdLong()))
        }
        incompleteCount = 0
        checkReady()
    }

    enum class Status {
        INIT,
        CHUNKING,
        BUILDING,
        READY,
        UNAVAILABLE,
        REMOVED,
    }

    fun interface StatusListener {
        fun onStatusChange(
            guildId: Long,
            oldStatus: Status,
            newStatus: Status,
        )
    }

    companion object {
        internal val log: Logger = JDALogger.getLog(GuildSetupController::class.java)

        private const val TIMEOUT_DURATION: Long = 75 // seconds
        private const val TIMEOUT_THRESHOLD: Int = 60 // Half of 120 rate limit
    }
}
