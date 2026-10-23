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
import gnu.trove.map.hash.TLongObjectHashMap
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.MemberImpl
import net.dv8tion.jda.internal.utils.Helpers
import java.util.ArrayList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiConsumer

class MemberChunkManager(
    private val client: WebSocketClient,
) {
    private val lock = ReentrantLock()
    private val requests: TLongObjectMap<ChunkRequest> = TLongObjectHashMap()
    private var timeoutHandle: Future<*>? = null

    fun clear() {
        MiscUtil.locked(lock, Runnable { requests.clear() })
    }

    private fun init() {
        MiscUtil.locked(
            lock,
            Runnable {
                if (timeoutHandle == null) {
                    timeoutHandle =
                        client
                            .getJDA()
                            .gatewayPool
                            .scheduleAtFixedRate(
                                TimeoutHandler(),
                                TIMEOUT_INTERVAL_SECONDS,
                                TIMEOUT_INTERVAL_SECONDS,
                                TimeUnit.SECONDS,
                            )
                }
            },
        )
    }

    fun shutdown() {
        timeoutHandle?.cancel(false)
    }

    fun chunkGuild(
        guild: GuildImpl,
        presence: Boolean,
        handler: BiConsumer<Boolean, List<Member>>,
    ): ChunkRequest {
        init()
        val request =
            DataObject
                .empty()
                .put("guild_id", guild.id)
                .put("presences", presence)
                .put("limit", 0)
                .put("query", "")

        val chunkRequest = ChunkRequest(handler, guild, request)
        makeRequest(chunkRequest)
        return chunkRequest
    }

    fun chunkGuild(
        guild: GuildImpl,
        query: String,
        limit: Int,
        handler: BiConsumer<Boolean, List<Member>>,
    ): ChunkRequest {
        init()
        val request =
            DataObject
                .empty()
                .put("guild_id", guild.id)
                .put("limit", Math.min(100, Math.max(1, limit)))
                .put("query", query)

        val chunkRequest = ChunkRequest(handler, guild, request)
        makeRequest(chunkRequest)
        return chunkRequest
    }

    fun chunkGuild(
        guild: GuildImpl,
        presence: Boolean,
        userIds: LongArray,
        handler: BiConsumer<Boolean, List<Member>>,
    ): ChunkRequest {
        init()
        val request =
            DataObject
                .empty()
                .put("guild_id", guild.id)
                .put("presences", presence)
                .put("user_ids", userIds)

        val chunkRequest = ChunkRequest(handler, guild, request)
        makeRequest(chunkRequest)
        return chunkRequest
    }

    @Suppress("UnusedParameter") // part of the Java-facing gateway contract
    fun handleChunk(
        guildId: Long,
        response: DataObject,
    ): Boolean =
        MiscUtil.locked(
            lock,
            java.util.function.Supplier {
                val nonce = response.getString("nonce", null)
                if (nonce.isNullOrEmpty()) {
                    return@Supplier false
                }
                val key = nonce.toLong()
                val request = requests.get(key)
                if (request == null) {
                    return@Supplier false
                }

                val lastChunk = isLastChunk(response)
                request.handleChunk(lastChunk, response)
                if (lastChunk || request.isCancelled) {
                    requests.remove(key)
                    request.complete(null)
                }
                true
            },
        )

    fun cancelRequest(request: ChunkRequest) {
        MiscUtil.locked(lock, Runnable { requests.remove(request.nonce) })
    }

    private fun makeRequest(request: ChunkRequest) {
        MiscUtil.locked(
            lock,
            Runnable {
                requests.put(request.nonce, request)
                sendChunkRequest(request.getRequest())
            },
        )
    }

    private fun sendChunkRequest(request: DataObject) {
        client.sendChunkRequest(request)
    }

    inner class ChunkRequest(
        private val handler: BiConsumer<Boolean, List<Member>>,
        private val guild: GuildImpl,
        private val request: DataObject,
    ) : CompletableFuture<Void>() {
        internal val nonce: Long = ThreadLocalRandom.current().nextLong() and 1L.inv()
        private var startTime: Long = 0
        private var timeout: Long = MAX_CHUNK_AGE

        init {
            request.put("nonce", nonceString)
        }

        fun setTimeout(timeout: Long): ChunkRequest {
            this.timeout = timeout
            return this
        }

        fun isNonce(nonce: String): Boolean = this.nonce == nonce.toLong()

        fun getNonce(): String = nonceString

        private val nonceString: String
            get() = nonce.toString()

        fun getAge(): Long = if (startTime <= 0) 0 else System.currentTimeMillis() - startTime

        fun isExpired(): Boolean = getAge() > timeout

        fun getRequest(): DataObject {
            startTime = System.currentTimeMillis()
            return request
        }

        private fun toMembers(chunk: DataObject): List<Member> {
            val builder: EntityBuilder = guild.jda.entityBuilder
            val memberArray: DataArray = chunk.getArray("members")
            val presences: TLongObjectMap<DataObject> =
                chunk
                    .optArray("presences")
                    .map { Helpers.convertToMap({ o -> o.getObject("user").getUnsignedLong("id") }, it) }
                    .orElseGet { TLongObjectHashMap() }
            val collect = ArrayList<Member>(memberArray.length())
            for (i in 0 until memberArray.length()) {
                val json = memberArray.getObject(i)
                val userId = json.getObject("user").getUnsignedLong("id")
                val presence = presences.get(userId)
                val member: MemberImpl = builder.createMember(guild, json, null, presence)
                builder.updateMemberCache(member)
                collect.add(member)
            }
            return collect
        }

        fun handleChunk(
            last: Boolean,
            chunk: DataObject,
        ) {
            try {
                if (!isDone) {
                    handler.accept(last, toMembers(chunk))
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") ex: Throwable,
            ) {
                completeExceptionally(ex)
                if (ex is Error) {
                    throw ex
                }
            }
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            client.cancelChunkRequest(getNonce())
            cancelRequest(this)
            return super.cancel(mayInterruptIfRunning)
        }
    }

    private inner class TimeoutHandler : Runnable {
        override fun run() {
            MiscUtil.locked(
                lock,
                Runnable {
                    requests.forEachValue(
                        object : gnu.trove.procedure.TObjectProcedure<ChunkRequest> {
                            override fun execute(request: ChunkRequest): Boolean {
                                if (request.isExpired()) {
                                    request.completeExceptionally(TimeoutException())
                                }
                                return true
                            }
                        },
                    )
                    requests.valueCollection().removeIf(java.util.function.Predicate { it.isDone })
                },
            )
        }
    }

    companion object {
        private const val MAX_CHUNK_AGE: Long = 10 * 1000 // 10 seconds
        private const val TIMEOUT_INTERVAL_SECONDS: Long = 5

        @JvmStatic
        fun isLastChunk(chunk: DataObject): Boolean = chunk.getInt("chunk_index") + 1 == chunk.getInt("chunk_count")
    }
}
