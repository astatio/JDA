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

import gnu.trove.map.TIntObjectMap
import gnu.trove.map.hash.TIntObjectHashMap
import gnu.trove.set.TIntSet
import gnu.trove.set.hash.TIntHashSet
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.utils.ClosableIterator
import net.dv8tion.jda.api.utils.LockIterator
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.cache.ShardCacheView
import net.dv8tion.jda.internal.utils.ChainedClosableIterator
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import org.apache.commons.collections4.iterators.ObjectArrayIterator
import java.util.Collections
import java.util.Objects
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Stream
import java.util.stream.StreamSupport

class ShardCacheViewImpl private constructor(
    map: TIntObjectMap<JDA>,
) : ReadWriteLockCache<JDA>(),
    ShardCacheView {
    private val elements: TIntObjectMap<JDA> = map

    constructor() : this(TIntObjectHashMap())

    constructor(initialCapacity: Int) : this(TIntObjectHashMap(initialCapacity))

    fun clear() {
        writeLock().use { elements.clear() }
    }

    fun remove(shardId: Int): JDA? = writeLock().use { elements.remove(shardId) }

    fun getMap(): TIntObjectMap<JDA> {
        if (!lock.writeLock().isHeldByCurrentThread) {
            throw IllegalStateException("Cannot access map without holding write lock!")
        }
        return elements
    }

    fun keySet(): TIntSet = readLock().use { TIntHashSet(elements.keySet()) }

    override fun forEach(action: Consumer<in JDA>) {
        Objects.requireNonNull(action)
        readLock().use {
            for (shard in elements.valueCollection()) {
                action.accept(shard)
            }
        }
    }

    override fun asList(): List<JDA> {
        if (isEmpty) {
            return Collections.emptyList()
        }
        return readLock().use {
            val cached = getCachedList()
            if (cached != null) {
                return@use cached
            }
            cache(ArrayList(elements.valueCollection()))
        }
    }

    override fun asSet(): Set<JDA> {
        if (isEmpty) {
            return Collections.emptySet()
        }
        return readLock().use {
            val cached = getCachedSet()
            if (cached != null) {
                return@use cached
            }
            cache(HashSet(elements.valueCollection()))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun lockedIterator(): LockIterator<JDA> {
        val readLock = lock.readLock()
        MiscUtil.tryLock(readLock)
        try {
            val directIterator = elements.valueCollection().iterator()
            return LockIterator(directIterator, readLock)
        } catch (t: Throwable) {
            readLock.unlock()
            throw t
        }
    }

    override fun size(): Long = elements.size().toLong()

    override fun isEmpty(): Boolean = elements.isEmpty

    @Suppress("NestedBlockDepth")
    override fun getElementsByName(
        name: String,
        ignoreCase: Boolean,
    ): List<JDA> {
        Checks.notEmpty(name, "Name")
        if (elements.isEmpty) {
            return Collections.emptyList()
        }

        return readLock().use {
            val list = ArrayList<JDA>()
            for (elem in elements.valueCollection()) {
                val elementName: String? = elem.shardInfo.shardString
                if (elementName != null) {
                    if (ignoreCase) {
                        if (elementName.equals(name, ignoreCase = true)) {
                            list.add(elem)
                        }
                    } else {
                        if (elementName == name) {
                            list.add(elem)
                        }
                    }
                }
            }

            list
        }
    }

    override fun spliterator(): Spliterator<JDA> =
        readLock().use {
            Spliterators.spliterator(iterator(), size(), Spliterator.IMMUTABLE or Spliterator.NONNULL)
        }

    override fun stream(): Stream<JDA> = StreamSupport.stream(spliterator(), false)

    override fun parallelStream(): Stream<JDA> = StreamSupport.stream(spliterator(), true)

    @Suppress("UNCHECKED_CAST")
    override fun iterator(): MutableIterator<JDA> =
        readLock().use { ObjectArrayIterator(elements.values(EMPTY_ARRAY) as Array<JDA>) as MutableIterator<JDA> }

    override fun getElementById(id: Int): JDA? = readLock().use { this.elements[id] }

    override fun hashCode(): Int = readLock().use { elements.hashCode() }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ShardCacheViewImpl) {
            return false
        }
        readLock().use {
            other.readLock().use {
                return this.elements == other.elements
            }
        }
    }

    override fun toString(): String = asList().toString()

    class UnifiedShardCacheViewImpl(
        private val generator: Supplier<out Stream<out ShardCacheView>>,
    ) : ShardCacheView {
        override fun size(): Long = distinctStream().mapToLong { it.size() }.sum()

        override fun isEmpty(): Boolean = generator.get().allMatch { it.isEmpty }

        override fun asList(): List<JDA> {
            val list = ArrayList<JDA>()
            stream().forEach { list.add(it) }
            return Collections.unmodifiableList(list)
        }

        override fun asSet(): Set<JDA> {
            val set = HashSet<JDA>()
            generator.get().flatMap { it.stream() }.forEach { set.add(it) }
            return Collections.unmodifiableSet(set)
        }

        override fun lockedIterator(): ClosableIterator<JDA> {
            val gen = this.generator.get().iterator()
            return ChainedClosableIterator(gen)
        }

        override fun getElementsByName(
            name: String,
            ignoreCase: Boolean,
        ): List<JDA> =
            distinctStream()
                .flatMap { view -> view.getElementsByName(name, ignoreCase).stream() }
                .collect(Helpers.toUnmodifiableList())

        override fun getElementById(id: Int): JDA? =
            generator
                .get()
                .map { view -> view.getElementById(id) }
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null)

        override fun stream(): Stream<JDA> = generator.get().flatMap { it.stream() }.distinct()

        override fun parallelStream(): Stream<JDA> = generator.get().flatMap { it.parallelStream() }.distinct()

        override fun iterator(): MutableIterator<JDA> = stream().iterator()

        private fun distinctStream(): Stream<out ShardCacheView> = generator.get().distinct()
    }

    companion object {
        private val EMPTY_ARRAY: Array<JDA> = arrayOf()
    }
}
