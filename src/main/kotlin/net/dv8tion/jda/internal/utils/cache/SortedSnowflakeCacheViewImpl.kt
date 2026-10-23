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

import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.utils.cache.SortedSnowflakeCacheView
import org.apache.commons.collections4.iterators.ObjectArrayIterator
import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.NavigableSet
import java.util.Spliterator
import java.util.Spliterators
import java.util.TreeSet
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Stream

class SortedSnowflakeCacheViewImpl<T>(
    type: Class<T>,
    nameMapper: Function<T, String>?,
    private val comparator: Comparator<T>,
) : SnowflakeCacheViewImpl<T>(type, nameMapper),
    SortedSnowflakeCacheView<T>
    where T : ISnowflake, T : Comparable<T> {
    constructor(type: Class<T>, comparator: Comparator<T>) : this(type, null, comparator)

    override fun forEach(action: Consumer<in T>) {
        readLock().use { iterator().forEachRemaining(action) }
    }

    override fun forEachUnordered(action: Consumer<in T>) {
        super<SnowflakeCacheViewImpl>.forEach(action)
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
            list.sortWith(comparator)
            cache(list)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun asSet(): NavigableSet<T> {
        if (isEmpty) {
            return Collections.emptyNavigableSet()
        }
        return readLock().use {
            val cached = getCachedSet() as NavigableSet<T>?
            if (cached != null) {
                return@use cached
            }
            val set = TreeSet(comparator)
            elements.forEachValue { set.add(it) }
            cache(set)
        }
    }

    override fun getElementsByName(
        name: String,
        ignoreCase: Boolean,
    ): List<T> {
        val filtered = super<SnowflakeCacheViewImpl>.getElementsByName(name, ignoreCase)
        (filtered as MutableList<T>).sortWith(comparator)
        return filtered
    }

    override fun spliterator(): Spliterator<T> =
        readLock().use { Spliterators.spliterator(iterator(), elements.size().toLong(), SPLIT_CHARACTERISTICS) }

    override fun streamUnordered(): Stream<T> = super<SnowflakeCacheViewImpl>.stream()

    override fun parallelStreamUnordered(): Stream<T> = super<SnowflakeCacheViewImpl>.parallelStream()

    override fun stream(): Stream<T> = super<SnowflakeCacheViewImpl>.stream().sorted(comparator)

    override fun parallelStream(): Stream<T> = super<SnowflakeCacheViewImpl>.parallelStream().sorted(comparator)

    @Suppress("UNCHECKED_CAST")
    override fun iterator(): MutableIterator<T> =
        readLock().use {
            val arr = elements.values(emptyArray) as Array<T>
            Arrays.sort(arr, comparator)
            ObjectArrayIterator(arr) as MutableIterator<T>
        }

    companion object {
        protected const val SPLIT_CHARACTERISTICS: Int =
            Spliterator.IMMUTABLE or Spliterator.ORDERED or Spliterator.NONNULL
    }
}
