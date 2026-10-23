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
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.utils.ClosableIterator
import net.dv8tion.jda.api.utils.cache.ChannelCacheView
import net.dv8tion.jda.internal.utils.ChainedClosableIterator
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Collections
import java.util.Objects
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream

class UnifiedChannelCacheView<C : Channel>(
    private val supplier: Supplier<Stream<ChannelCacheView<C>>>,
) : ChannelCacheView<C> {
    override fun forEach(action: Consumer<in C>) {
        Objects.requireNonNull(action, "Consumer")
        lockedIterator().use {
            while (it.hasNext()) {
                action.accept(it.next())
            }
        }
    }

    override fun asList(): List<C> = stream().collect(Helpers.toUnmodifiableList())

    override fun asSet(): Set<C> = stream().collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet))

    override fun lockedIterator(): ClosableIterator<C> = ChainedClosableIterator(supplier.get().iterator())

    override fun size(): Long = supplier.get().mapToLong { it.size() }.sum()

    override fun isEmpty(): Boolean = supplier.get().allMatch { it.isEmpty }

    override fun getElementsByName(
        name: String,
        ignoreCase: Boolean,
    ): List<C> =
        supplier
            .get()
            .flatMap { view -> view.getElementsByName(name, ignoreCase).stream() }
            .collect(Helpers.toUnmodifiableList())

    override fun stream(): Stream<C> = supplier.get().flatMap { it.stream() }

    override fun parallelStream(): Stream<C> = supplier.get().parallel().flatMap { it.parallelStream() }

    override fun <T : C> ofType(type: Class<T>): ChannelCacheView<T> {
        Checks.notNull(type, "Type")
        return UnifiedChannelCacheView { supplier.get().map { view -> view.ofType(type) } }
    }

    override fun getElementById(
        type: ChannelType,
        id: Long,
    ): C? =
        supplier
            .get()
            .map { view -> view.getElementById(type, id) }
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null)

    override fun getElementById(id: Long): C? =
        supplier
            .get()
            .map { view -> view.getElementById(id) }
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null)

    override fun iterator(): MutableIterator<C> = stream().iterator()
}
