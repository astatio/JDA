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

import gnu.trove.iterator.TLongObjectIterator
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.CacheConsumer
import net.dv8tion.jda.internal.utils.JDALogger
import org.slf4j.Logger
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicInteger

open class EventCache {
    private val eventCache: EnumMap<Type, TLongObjectMap<MutableList<CacheNode>>> = EnumMap(Type::class.java)

    @Synchronized
    fun timeout(responseTotal: Long) {
        if (eventCache.isEmpty()) {
            return
        }
        val count = AtomicInteger()
        eventCache.forEach { (type, map) ->
            if (map.isEmpty()) {
                return@forEach
            }
            val iterator: TLongObjectIterator<MutableList<CacheNode>> = map.iterator()
            while (iterator.hasNext()) {
                iterator.advance()
                val triggerId = iterator.key()
                val cache = iterator.value()
                // Remove when this node is more than 100 events ago
                cache.removeIf { node ->
                    val remove = responseTotal - node.responseTotal > TIMEOUT_AMOUNT
                    if (remove) {
                        count.incrementAndGet()
                        LOG.trace("Removing type {}/{} from event cache with payload {}", type, triggerId, node.event)
                    }
                    remove
                }
                if (cache.isEmpty()) {
                    iterator.remove()
                }
            }
        }
        val amount = count.get()
        if (amount > 0) {
            LOG.debug("Removed {} events from cache that were too old to be recycled", amount)
        }
    }

    @Synchronized
    fun cache(
        type: Type,
        triggerId: Long,
        responseTotal: Long,
        event: DataObject,
        handler: CacheConsumer,
    ) {
        val triggerCache =
            eventCache.computeIfAbsent(type) { _ -> TLongObjectHashMap() }

        var items = triggerCache.get(triggerId)
        if (items == null) {
            items = ArrayList()
            triggerCache.put(triggerId, items)
        }

        items.add(CacheNode(responseTotal, event, handler))
    }

    @Synchronized
    fun playbackCache(
        type: Type,
        triggerId: Long,
    ) {
        val typeCache = this.eventCache[type] ?: return

        val items = typeCache.remove(triggerId)
        if (items != null && items.isNotEmpty()) {
            LOG.debug("Replaying {} events from the EventCache for type {} with id: {}", items.size, type, triggerId)
            for (item in items) {
                item.execute()
            }
        }
    }

    @Synchronized
    fun size(): Int =
        eventCache.values
            .stream()
            .mapToLong { typeMap ->
                typeMap
                    .valueCollection()
                    .stream()
                    .mapToLong { it.size.toLong() }
                    .sum()
            }.sum()
            .toInt()

    @Synchronized
    fun clear() {
        eventCache.clear()
    }

    @Synchronized
    fun clear(
        type: Type,
        id: Long,
    ) {
        val typeCache = this.eventCache[type] ?: return

        val events = typeCache.remove(id)
        if (events != null) {
            LOG.debug("Clearing cache for type {} with ID {} (Size: {})", type, id, events.size)
        }
    }

    enum class Type {
        USER,
        MEMBER,
        GUILD,
        CHANNEL,
        ROLE,
        SOUNDBOARD_SOUND,
        RELATIONSHIP,
        CALL,
        SCHEDULED_EVENT,
    }

    private class CacheNode(
        val responseTotal: Long,
        val event: DataObject,
        val callback: CacheConsumer,
    ) {
        fun execute() {
            callback.execute(responseTotal, event)
        }
    }

    companion object {
        @JvmField
        val LOG: Logger = JDALogger.getLog(EventCache::class.java)

        /** Sequence difference after which events will be removed from cache */
        const val TIMEOUT_AMOUNT: Long = 100
    }
}
