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

package net.dv8tion.jda.internal.utils.cache

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.utils.ClosableIterator
import net.dv8tion.jda.api.utils.LockIterator
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.cache.ChannelCacheView
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Collections
import java.util.EnumMap
import java.util.Objects
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.Stream

open class ChannelCacheViewImpl<T : Channel>(
    type: Class<T>,
) : ReadWriteLockCache<T>(),
    ChannelCacheView<T> {
    @JvmField
    protected val caches: EnumMap<ChannelType, TLongObjectMap<T>> = EnumMap(ChannelType::class.java)

    init {
        for (channelType in ChannelType.values()) {
            val key = normalizeKey(channelType)
            val clazz = key.`interface`
            if (key != ChannelType.UNKNOWN && type.isAssignableFrom(clazz)) {
                caches[key] = TLongObjectHashMap()
            }
        }
    }

    // Store all threads under the same channel type,
    // makes it easier because the interface is shared
    protected fun normalizeKey(type: ChannelType): ChannelType = if (type.isThread) ChannelType.GUILD_PUBLIC_THREAD else type

    @Suppress("UNCHECKED_CAST")
    protected fun <C : T> getMap(type: ChannelType): TLongObjectMap<C>? = caches[normalizeKey(type)] as TLongObjectMap<C>?

    @Suppress("UNCHECKED_CAST")
    open fun <C : T> put(element: C): C? =
        writeLock().use {
            getMap<C>(element.type)!!.put(element.idLong, element)
        }

    @Suppress("UNCHECKED_CAST", "TYPE_PARAMETER_UNUSED_IN_FORMALS")
    open fun <C : T> remove(
        type: ChannelType,
        id: Long,
    ): C? =
        writeLock().use {
            getMap<T>(type)!!.remove(id) as C?
        }

    open fun <C : T> remove(channel: C): C? = remove(channel.type, channel.idLong)

    open fun <C : T> removeIf(
        typeFilter: Class<C>,
        predicate: Predicate<in C>,
    ) {
        writeLock().use { (ofType(typeFilter) as FilteredCacheView<C>).removeIf(predicate) }
    }

    fun clear() {
        writeLock().use { caches.values.forEach { it.clear() } }
    }

    override fun <C : T> ofType(type: Class<C>): ChannelCacheView<C> = FilteredCacheView(type)

    override fun forEach(action: Consumer<in T>) {
        readLock().use {
            for (cache in caches.values) {
                cache.valueCollection().forEach(action)
            }
        }
    }

    override fun asList(): List<T> {
        var list = getCachedList()
        if (list == null) {
            val newList = applyStream { it.collect(Collectors.toList()) }
            list = cache(newList)
        }
        return list
    }

    override fun asSet(): Set<T> {
        var set = getCachedSet()
        if (set == null) {
            val newSet = applyStream { it.collect(Collectors.toSet()) }
            set = cache(newSet)
        }
        return set
    }

    @Suppress("TooGenericExceptionCaught")
    override fun lockedIterator(): ClosableIterator<T> {
        val readLock = lock.readLock()
        MiscUtil.tryLock(readLock)
        try {
            val directIterator: Iterator<T> =
                caches.values
                    .stream()
                    .flatMap { map -> map.valueCollection().stream() }
                    .iterator()
            return LockIterator(directIterator, readLock)
        } catch (t: Throwable) {
            readLock.unlock()
            throw t
        }
    }

    override fun size(): Long =
        readLock().use {
            caches.values
                .stream()
                .mapToLong { it.size().toLong() }
                .sum()
        }

    override fun isEmpty(): Boolean = readLock().use { caches.values.stream().allMatch { it.isEmpty } }

    override fun getElementsByName(
        name: String,
        ignoreCase: Boolean,
    ): List<T> {
        Checks.notEmpty(name, "Name")
        return applyStream { stream ->
            stream
                .filter { channel -> Helpers.equals(channel.name, name, ignoreCase) }
                .collect(Helpers.toUnmodifiableList())
        }
    }

    override fun stream(): Stream<T> = this.asList().stream()

    override fun parallelStream(): Stream<T> = this.asList().parallelStream()

    override fun getElementById(id: Long): T? =
        readLock().use {
            for (cache in caches.values) {
                val element = cache.get(id)
                if (element != null) {
                    return@use element
                }
            }
            null
        }

    override fun getElementById(
        type: ChannelType,
        id: Long,
    ): T? {
        Checks.notNull(type, "ChannelType")
        return readLock().use {
            val map = getMap<T>(type)
            map?.get(id)
        }
    }

    override fun iterator(): MutableIterator<T> = stream().iterator()

    open inner class FilteredCacheView<C : T>(
        type: Class<C>,
    ) : ChannelCacheView<C> {
        @JvmField
        protected val type: Class<C>

        @JvmField
        protected val filteredMaps: List<TLongObjectMap<C>>

        init {
            Checks.notNull(type, "Type")
            this.type = type
            checkChannelInterface(this.type)
            @Suppress("UNCHECKED_CAST")
            this.filteredMaps =
                caches.entries
                    .stream()
                    .filter { entry ->
                        entry.key != null && this.type.isAssignableFrom(entry.key.`interface`)
                    }.map { entry -> entry.value as TLongObjectMap<C> }
                    .collect(Collectors.toList())
        }

        private fun checkChannelInterface(type: Class<C>) {
            var isValidInterfaceType = false
            for (channelType in ChannelType.values()) {
                isValidInterfaceType = isValidInterfaceType or type.isAssignableFrom(channelType.`interface`)
            }
            Checks.check(isValidInterfaceType, "Type %s is not a valid channel interface", type.simpleName)
        }

        fun removeIf(filter: Predicate<in C>) {
            this.filteredMaps.forEach { map -> map.valueCollection().removeIf(filter) }
        }

        override fun asList(): List<C> = applyStream { it.collect(Helpers.toUnmodifiableList()) }

        override fun asSet(): Set<C> =
            applyStream { it.collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet)) }

        @Suppress("TooGenericExceptionCaught")
        override fun lockedIterator(): ClosableIterator<C> {
            val readLock = lock.readLock()
            MiscUtil.tryLock(readLock)
            try {
                val directIterator: Iterator<C> =
                    filteredMaps
                        .stream()
                        .flatMap { map -> map.valueCollection().stream() }
                        .iterator()
                return LockIterator(directIterator, readLock)
            } catch (t: Throwable) {
                readLock.unlock()
                throw t
            }
        }

        override fun size(): Long = readLock().use { filteredMaps.stream().mapToLong { it.size().toLong() }.sum() }

        override fun isEmpty(): Boolean = readLock().use { filteredMaps.stream().allMatch { it.isEmpty } }

        override fun getElementsByName(
            name: String,
            ignoreCase: Boolean,
        ): List<C> {
            Checks.notEmpty(name, "Name")
            return applyStream { stream ->
                stream
                    .filter { channel -> Helpers.equals(channel.name, name, ignoreCase) }
                    .collect(Helpers.toUnmodifiableList())
            }
        }

        override fun stream(): Stream<C> = asList().stream()

        override fun parallelStream(): Stream<C> = asList().parallelStream()

        override fun <C1 : C> ofType(type: Class<C1>): ChannelCacheView<C1> = ChannelCacheViewImpl@this.ofType(type)

        override fun getElementById(
            type: ChannelType,
            id: Long,
        ): C? {
            val channel = ChannelCacheViewImpl@this.getElementById(type, id)
            return if (this.type.isInstance(channel)) this.type.cast(channel) else null
        }

        override fun getElementById(id: Long): C? =
            readLock().use {
                filteredMaps
                    .stream()
                    .map { it[id] }
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null)
            }

        @Suppress("UNCHECKED_CAST")
        override fun iterator(): MutableIterator<C> = asList().iterator() as MutableIterator<C>

        private fun <R> applyStream(transform: (Stream<C>) -> R): R =
            lockedIterator().use { iterator ->
                transform(
                    java.util.stream.StreamSupport.stream(
                        java.util.Spliterators.spliteratorUnknownSize(iterator, 0),
                        false,
                    ),
                )
            }
    }
}
