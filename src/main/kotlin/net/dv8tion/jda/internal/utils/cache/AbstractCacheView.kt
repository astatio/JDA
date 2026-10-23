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
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.dv8tion.jda.api.utils.LockIterator
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.internal.utils.Checks
import org.apache.commons.collections4.iterators.ObjectArrayIterator
import java.util.Collections
import java.util.Objects
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Stream
import java.util.stream.StreamSupport

abstract class AbstractCacheView<T> protected constructor(
    protected val type: Class<T>,
    protected val nameMapper: Function<T, String>?,
) : ReadWriteLockCache<T>(),
    CacheView<T> {
    @JvmField
    protected val elements: TLongObjectMap<T> = TLongObjectHashMap()

    @JvmField
    protected val emptyArray: Array<T>

    init {
        @Suppress("UNCHECKED_CAST")
        this.emptyArray =
            java.lang.reflect.Array
                .newInstance(type, 0) as Array<T>
    }

    fun clear() {
        writeLock().use { elements.clear() }
    }

    fun getMap(): TLongObjectMap<T> {
        if (!lock.writeLock().isHeldByCurrentThread) {
            throw IllegalStateException("Cannot access map directly without holding write lock!")
        }
        return elements
    }

    fun get(id: Long): T? = readLock().use { elements.get(id) }

    fun remove(id: Long): T? = writeLock().use { elements.remove(id) }

    fun keySet(): TLongSet = readLock().use { TLongHashSet(elements.keySet()) }

    override fun forEach(action: Consumer<in T>) {
        Objects.requireNonNull(action)
        readLock().use {
            for (elem in elements.valueCollection()) {
                action.accept(elem)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun lockedIterator(): LockIterator<T> {
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

    override fun asList(): List<T> {
        if (isEmpty) {
            return Collections.emptyList()
        }
        return readLock().use {
            val cached = getCachedList()
            if (cached != null) {
                return@use cached
            }
            val list = ArrayList<T>(elements.size())
            elements.forEachValue { list.add(it) }
            cache(list)
        }
    }

    override fun asSet(): Set<T> {
        if (isEmpty) {
            return Collections.emptySet()
        }
        return readLock().use {
            val cached = getCachedSet()
            if (cached != null) {
                return@use cached
            }
            val set = HashSet<T>(elements.size())
            elements.forEachValue { set.add(it) }
            cache(set)
        }
    }

    override fun size(): Long = elements.size().toLong()

    override fun isEmpty(): Boolean = elements.isEmpty

    @Suppress("ReturnCount")
    override fun getElementsByName(
        name: String,
        ignoreCase: Boolean,
    ): List<T> {
        Checks.notEmpty(name, "Name")
        if (elements.isEmpty) {
            return Collections.emptyList()
        }
        if (nameMapper == null) { // no getName method available
            throw UnsupportedOperationException("The contained elements are not assigned with names.")
        }
        if (isEmpty) {
            return Collections.emptyList()
        }
        val list = ArrayList<T>()
        forEach { elem ->
            val elementName: String? = nameMapper.apply(elem)
            if (elementName != null && equals(ignoreCase, elementName, name)) {
                list.add(elem)
            }
        }
        return list // must be modifiable because of SortedSnowflakeCacheView
    }

    override fun spliterator(): Spliterator<T> = readLock().use { Spliterators.spliterator(elements.values(), Spliterator.IMMUTABLE) }

    override fun stream(): Stream<T> = StreamSupport.stream(spliterator(), false)

    override fun parallelStream(): Stream<T> = StreamSupport.stream(spliterator(), true)

    @Suppress("UNCHECKED_CAST")
    override fun iterator(): MutableIterator<T> =
        readLock().use { ObjectArrayIterator(elements.values(emptyArray) as Array<T>) as MutableIterator<T> }

    override fun toString(): String = asList().toString()

    override fun hashCode(): Int = readLock().use { elements.hashCode() }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is AbstractCacheView<*>) {
            return false
        }
        readLock().use {
            other.readLock().use {
                return this.elements == other.elements
            }
        }
    }

    protected fun equals(
        ignoreCase: Boolean,
        first: String,
        second: String,
    ): Boolean = if (ignoreCase) first.equals(second, ignoreCase = true) else first == second
}
