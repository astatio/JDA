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

import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.utils.cache.SortedChannelCacheView
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Collections
import java.util.Comparator
import java.util.NavigableSet
import java.util.Spliterator
import java.util.TreeSet
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

class SortedChannelCacheViewImpl<T>(
    type: Class<T>,
) : ChannelCacheViewImpl<T>(type),
    SortedChannelCacheView<T>
    where T : Channel, T : Comparable<T> {
    override fun <C : T> ofType(type: Class<C>): SortedFilteredCacheView<C> = SortedFilteredCacheView(type)

    override fun asList(): List<T> {
        var list = getCachedList()
        if (list == null) {
            list = cache(ArrayList(asSet()))
        }
        return list
    }

    @Suppress("UNCHECKED_CAST")
    override fun asSet(): NavigableSet<T> {
        var set = getCachedSet() as NavigableSet<T>?
        if (set == null) {
            set = cache(applyStream { it.collect(Collectors.toCollection { TreeSet() }) } as NavigableSet<T>)
        }
        return set
    }

    override fun forEachUnordered(action: Consumer<in T>) {
        super<ChannelCacheViewImpl>.forEach(action)
    }

    override fun forEach(action: Consumer<in T>) {
        asSet().forEach(action)
    }

    override fun getElementsByName(name: String): List<T> {
        val elements = super<ChannelCacheViewImpl>.getElementsByName(name) as MutableList<T>
        elements.sortWith(Comparator.naturalOrder())
        return elements
    }

    override fun streamUnordered(): Stream<T> =
        readLock().use {
            caches.values
                .stream()
                .flatMap { cache -> cache.valueCollection().stream() }
                .collect(Collectors.toList())
                .stream()
        }

    override fun parallelStreamUnordered(): Stream<T> = streamUnordered().parallel()

    override fun spliterator(): Spliterator<T> = asSet().spliterator()

    override fun iterator(): MutableIterator<T> = asSet().iterator()

    inner class SortedFilteredCacheView<C : T>(
        type: Class<C>,
    ) : FilteredCacheView<C>(type),
        SortedChannelCacheView<C> {
        override fun asList(): List<C> = applyStream { it.sorted().collect(Helpers.toUnmodifiableList()) }

        override fun asSet(): NavigableSet<C> =
            applyStream {
                it.collect(
                    Collectors.collectingAndThen(
                        Collectors.toCollection { TreeSet<C>() },
                        Collections::unmodifiableNavigableSet,
                    ),
                )
            }

        override fun getElementsByName(
            name: String,
            ignoreCase: Boolean,
        ): List<C> {
            Checks.notEmpty(name, "Name")
            return applyStream {
                it
                    .filter { channel -> Helpers.equals(name, channel.name, ignoreCase) }
                    .sorted()
                    .collect(Helpers.toUnmodifiableList())
            }
        }

        override fun streamUnordered(): Stream<C> = applyStream { it.filter(type::isInstance).collect(Collectors.toList()) }.stream()

        override fun parallelStreamUnordered(): Stream<C> = stream().parallel()

        override fun <C1 : C> ofType(type: Class<C1>): SortedChannelCacheView<C1> = SortedChannelCacheViewImpl@this.ofType(type)

        override fun forEachUnordered(action: Consumer<in C>) {
            super<FilteredCacheView>.forEach(action)
        }

        override fun forEach(action: Consumer<in C>) {
            stream().forEach(action)
        }
    }
}
