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

import gnu.trove.iterator.TLongIterator
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.dv8tion.jda.api.audio.hooks.ConnectionListener
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.guild.UnavailableGuildJoinedEvent
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.managers.AudioManagerImpl
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.cache.AbstractCacheView
import java.util.LinkedList

class GuildSetupNode internal constructor(
    private val id: Long,
    private val controller: GuildSetupController,
    internal val type: Type,
) {
    private val cachedEvents: MutableList<DataObject> = LinkedList()

    internal var members: TLongObjectMap<DataObject>? = null
    internal var removedMembers: TLongSet? = null
    private var partialGuild: DataObject? = null
    private var expectedMemberCount = 1
    internal var requestedChunk = false
    internal var firedUnavailableJoin = false
    internal var markedUnavailable = false
    internal var status: GuildSetupController.Status = GuildSetupController.Status.INIT

    fun getIdLong(): Long = id

    fun getId(): String = java.lang.Long.toUnsignedString(id)

    fun getStatus(): GuildSetupController.Status = status

    fun getGuildPayload(): DataObject? = partialGuild

    fun getExpectedMemberCount(): Int = expectedMemberCount

    fun getCurrentMemberCount(): Int {
        val knownMembers = TLongHashSet(members!!.keySet())
        knownMembers.removeAll(removedMembers!!)
        return knownMembers.size()
    }

    fun getType(): Type = type

    fun isJoin(): Boolean = type == Type.JOIN

    fun isMarkedUnavailable(): Boolean = markedUnavailable

    fun requestedChunks(): Boolean = requestedChunk

    fun containsMember(userId: Long): Boolean {
        if (members == null || members!!.isEmpty()) {
            return false
        }
        return members!!.containsKey(userId)
    }

    override fun toString(): String =
        EntityString(this)
            .setType(type)
            .addMetadata("id", id)
            .addMetadata("status", status)
            .addMetadata("expectedMemberCount", expectedMemberCount)
            .addMetadata("requestedChunk", requestedChunk)
            .addMetadata("markedUnavailable", markedUnavailable)
            .toString()

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is GuildSetupNode) {
            return false
        }
        return other.id == id
    }

    private fun getController(): GuildSetupController = controller

    internal fun updateStatus(status: GuildSetupController.Status) {
        if (status == this.status) {
            return
        }
        try {
            getController().listener.onStatusChange(id, this.status, status)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            GuildSetupController.log.error("Uncaught exception in status listener", ex)
        }
        this.status = status
    }

    internal fun reset() {
        updateStatus(GuildSetupController.Status.UNAVAILABLE)
        expectedMemberCount = 1
        partialGuild = null
        requestedChunk = false
        members?.clear()
        removedMembers?.clear()
        cachedEvents.clear()
    }

    @Suppress("EmptyFunctionBlock", "UnusedParameter")
    internal fun handleReady(obj: DataObject) {}

    internal fun handleCreate(obj: DataObject) {
        if (partialGuild == null) {
            partialGuild = obj
        } else {
            for (key in obj.keys()) {
                partialGuild!!.put(key, obj.opt(key).orElse(null))
            }
        }
        val unavailable = partialGuild!!.getBoolean("unavailable")
        this.markedUnavailable = unavailable
        if (unavailable) {
            if (!firedUnavailableJoin && isJoin()) {
                firedUnavailableJoin = true
                val api = getController().getJDA()
                api.handleEvent(UnavailableGuildJoinedEvent(api, api.getResponseTotal(), id))
            }
            return
        }

        ensureMembers()
    }

    internal fun handleSync(obj: DataObject) {
        if (partialGuild == null) {
            // In this case we received a GUILD_DELETE with unavailable = true while syncing
            // however we have to wait for the GUILD_CREATE with unavailable = false before
            // requesting new chunks
            GuildSetupController.log.debug("Dropping sync update due to unavailable guild")
            return
        }
        for (key in obj.keys()) {
            partialGuild!!.put(key, obj.opt(key).orElse(null))
        }

        ensureMembers()
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    internal fun handleMemberChunk(
        last: Boolean,
        arr: DataArray,
    ): Boolean {
        if (partialGuild == null) {
            // In this case we received a GUILD_DELETE with unavailable = true while chunking
            // however we have to wait for the GUILD_CREATE with unavailable = false before
            // requesting new chunks
            GuildSetupController.log.debug("Dropping member chunk due to unavailable guild")
            return true
        }
        for (index in 0 until arr.length()) {
            val obj = arr.getObject(index)
            val id = obj.getObject("user").getLong("id")
            members!!.put(id, obj)
        }

        if (last || members!!.size() >= expectedMemberCount || !getController().getJDA().chunkGuild(id)) {
            completeSetup()
            return false
        }
        return true
    }

    internal fun handleAddMember(member: DataObject) {
        if (members == null || removedMembers == null) {
            return
        }
        expectedMemberCount++
        val userId = member.getObject("user").getLong("id")
        members!!.put(userId, member)
        removedMembers!!.remove(userId)
    }

    internal fun handleRemoveMember(member: DataObject) {
        if (members == null || removedMembers == null) {
            return
        }
        expectedMemberCount--
        val userId = member.getObject("user").getLong("id")
        members!!.remove(userId)
        removedMembers!!.add(userId)
        val eventCache = getController().getJDA().getEventCache()
        // if no other setup node contains this userId we clear it here
        if (!getController().containsMember(userId, this)) {
            eventCache.clear(EventCache.Type.USER, userId)
        }
    }

    internal fun cacheEvent(event: DataObject) {
        GuildSetupController.log.trace("Caching {} event during init. GuildId: {}", event.getString("t"), id)
        cachedEvents.add(event)
        // Check if more than 2000 events cached - suspicious
        // Print warning every 1000 events
        val cacheSize = cachedEvents.size
        if (cacheSize >= CACHE_EVENT_WARNING_THRESHOLD && cacheSize % CACHE_EVENT_WARNING_INTERVAL == 0) {
            val controller = getController()
            GuildSetupController.log.warn(
                "Accumulating suspicious amounts of cached events during guild setup, " +
                    "something might be wrong. Cached: {} Members: {}/{} Status: {} GuildId: {} Incomplete: {}/{}",
                cacheSize,
                getCurrentMemberCount(),
                getExpectedMemberCount(),
                status,
                id,
                controller.getChunkingCount(),
                controller.getIncompleteCount(),
            )

            if (status == GuildSetupController.Status.CHUNKING) {
                GuildSetupController.log.debug("Forcing new chunk request for guild: {}", id)
                controller.sendChunkRequest(id)
            }
        }
    }

    internal fun cleanup() {
        updateStatus(GuildSetupController.Status.REMOVED)
        val eventCache = getController().getJDA().getEventCache()
        eventCache.clear(EventCache.Type.GUILD, id)
        if (partialGuild == null) {
            return
        }

        val channels = partialGuild!!.optArray("channels")
        val roles = partialGuild!!.optArray("roles")
        channels.ifPresent { arr ->
            for (i in 0 until arr.length()) {
                val json = arr.getObject(i)
                val id = json.getLong("id")
                eventCache.clear(EventCache.Type.CHANNEL, id)
            }
        }

        roles.ifPresent { arr ->
            for (i in 0 until arr.length()) {
                val json = arr.getObject(i)
                val id = json.getLong("id")
                eventCache.clear(EventCache.Type.ROLE, id)
            }
        }

        if (members != null) {
            val it = members!!.iterator()
            while (it.hasNext()) {
                it.advance()
                val userId = it.key()
                if (!getController().containsMember(userId, this)) {
                    // if no other setup node contains this userId we clear it here
                    eventCache.clear(EventCache.Type.USER, userId)
                }
            }
        }
    }

    private fun completeSetup() {
        updateStatus(GuildSetupController.Status.BUILDING)
        val api = getController().getJDA()
        val it: TLongIterator = removedMembers!!.iterator()
        while (it.hasNext()) {
            members!!.remove(it.next())
        }
        removedMembers!!.clear()
        val guild = api.getEntityBuilder().createGuild(id, partialGuild!!, members!!, expectedMemberCount)
        updateAudioManagerReference(guild)
        when (type) {
            Type.AVAILABLE -> {
                api.handleEvent(GuildAvailableEvent(api, api.getResponseTotal(), guild))
                getController().remove(id)
            }
            Type.JOIN -> {
                api.handleEvent(GuildJoinEvent(api, api.getResponseTotal(), guild))
                if (requestedChunk) {
                    getController().ready(id)
                } else {
                    getController().remove(id)
                }
            }
            else -> {
                api.handleEvent(GuildReadyEvent(api, api.getResponseTotal(), guild))
                getController().ready(id)
            }
        }
        updateStatus(GuildSetupController.Status.READY)
        GuildSetupController.log.debug("Finished setup for guild {} firing cached events {}", id, cachedEvents.size)
        api.getClient().handle(cachedEvents)
        api.getEventCache().playbackCache(EventCache.Type.GUILD, id)
    }

    private fun ensureMembers() {
        expectedMemberCount = partialGuild!!.getInt("member_count")
        members = TLongObjectHashMap(expectedMemberCount)
        removedMembers = TLongHashSet()
        val memberArray = partialGuild!!.getArray("members")
        if (!getController().getJDA().chunkGuild(id)) {
            handleMemberChunk(true, memberArray)
        } else if (memberArray.length() < expectedMemberCount && !requestedChunk) {
            updateStatus(GuildSetupController.Status.CHUNKING)
            getController().addGuildForChunking(id, isJoin())
            requestedChunk = true
        } else if (handleMemberChunk(false, memberArray) && !requestedChunk) {
            // Discord sent us enough members to satisfy the member_count
            //  but we found duplicates and still didn't reach enough to satisfy the count
            //  in this case we try to do chunking instead
            // This is caused by lazy guilds and intended behavior according to jake
            GuildSetupController.log.trace(
                "Received suspicious members with a guild payload. Attempting to chunk. " +
                    "member_count: {} members: {} actual_members: {} guild_id: {}",
                expectedMemberCount,
                memberArray.length(),
                members!!.size(),
                id,
            )
            members!!.clear()
            updateStatus(GuildSetupController.Status.CHUNKING)
            getController().addGuildForChunking(id, isJoin())
            requestedChunk = true
        }
    }

    private fun updateAudioManagerReference(guild: GuildImpl) {
        val api = getController().getJDA()
        val managerView: AbstractCacheView<AudioManager> = api.getAudioManagersView()
        managerView.writeLock().use {
            val audioManagerMap = managerView.getMap()
            val mng = audioManagerMap.get(id) as AudioManagerImpl?
            if (mng == null) {
                return
            }
            val listener: ConnectionListener? = mng.getConnectionListener()
            val newMng = AudioManagerImpl(guild)
            newMng.setSelfMuted(mng.isSelfMuted())
            newMng.setSelfDeafened(mng.isSelfDeafened())
            newMng.setQueueTimeout(mng.getConnectTimeout())
            newMng.setSendingHandler(mng.getSendingHandler())
            newMng.setReceivingHandler(mng.getReceivingHandler())
            newMng.setConnectionListener(listener)
            newMng.setAutoReconnect(mng.isAutoReconnect())

            if (mng.isConnected()) {
                val channelId = mng.getConnectedChannel()!!.getIdLong()

                val channel: VoiceChannel? = api.getVoiceChannelById(channelId)
                if (channel != null) {
                    if (mng.isConnected()) {
                        mng.closeAudioConnection(ConnectionStatus.ERROR_CANNOT_RESUME)
                    }
                } else {
                    // The voice channel is not cached. It was probably deleted.
                    api.getClient().removeAudioConnection(id)
                    listener?.onStatusChange(ConnectionStatus.DISCONNECTED_CHANNEL_DELETED)
                }
            }
            audioManagerMap.put(id, newMng)
        }
    }

    enum class Type {
        INIT,
        JOIN,
        AVAILABLE,
    }

    companion object {
        private const val CACHE_EVENT_WARNING_THRESHOLD = 2000
        private const val CACHE_EVENT_WARNING_INTERVAL = 1000
    }
}
