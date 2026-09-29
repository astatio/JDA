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
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.api.utils.cache.MemberCacheView
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView
import net.dv8tion.jda.api.utils.cache.UnifiedMemberCacheView
import net.dv8tion.jda.internal.utils.ChainedClosableIterator
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Collections
import java.util.Objects
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Stream

open class UnifiedCacheViewImpl<T, E : CacheView<T>>(
    protected val generator: Supplier<out Stream<out E>>,
) : CacheView<T> {
    override fun size(): Long = distinctStream().mapToLong { it.size() }.sum()

    override fun isEmpty(): Boolean = distinctStream().allMatch { it.isEmpty }

    override fun forEach(action: Consumer<in T>) {
        Objects.requireNonNull(action)
        lockedIterator().use {
            while (it.hasNext()) {
                action.accept(it.next())
            }
        }
    }

    override fun asList(): List<T> {
        val list = ArrayList<T>()
        forEach { list.add(it) }
        return Collections.unmodifiableList(list)
    }

    override fun asSet(): Set<T> =
        lockedIterator().use {
            // because the iterator needs to retain elements to avoid duplicates,
            // we can use the resulting HashSet as our return value!
            while (it.hasNext()) {
                it.next()
            }
            Collections.unmodifiableSet(it.getItems())
        }

    override fun lockedIterator(): ChainedClosableIterator<T> {
        val gen = generator.get().iterator()
        return ChainedClosableIterator(gen)
    }

    override fun getElementsByName(
        name: String,
        ignoreCase: Boolean,
    ): List<T> =
        distinctStream()
            .flatMap { view -> view.getElementsByName(name, ignoreCase).stream() }
            .distinct()
            .collect(Helpers.toUnmodifiableList())

    override fun stream(): Stream<T> = distinctStream().flatMap { it.stream() }.distinct()

    override fun parallelStream(): Stream<T> = distinctStream().flatMap { it.parallelStream() }.distinct()

    override fun iterator(): MutableIterator<T> = stream().iterator()

    protected fun distinctStream(): Stream<out E> = generator.get().distinct()

    class UnifiedSnowflakeCacheView<T : ISnowflake>(
        generator: Supplier<out Stream<out SnowflakeCacheView<T>>>,
    ) : UnifiedCacheViewImpl<T, SnowflakeCacheView<T>>(generator),
        SnowflakeCacheView<T> {
        override fun getElementById(id: Long): T? =
            generator
                .get()
                .map { view -> view.getElementById(id) }
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null)
    }

    class UnifiedMemberCacheViewImpl(
        generator: Supplier<out Stream<out MemberCacheView>>,
    ) : UnifiedCacheViewImpl<Member, MemberCacheView>(generator),
        UnifiedMemberCacheView {
        override fun getElementsById(id: Long): List<Member> =
            distinctStream()
                .map { view -> view.getElementById(id) }
                .filter { it != null }
                .map { it!! }
                .collect(Helpers.toUnmodifiableList())

        override fun getElementsByUsername(
            name: String,
            ignoreCase: Boolean,
        ): List<Member> =
            distinctStream()
                .flatMap { view -> view.getElementsByUsername(name, ignoreCase).stream() }
                .collect(Helpers.toUnmodifiableList())

        override fun getElementsByNickname(
            name: String?,
            ignoreCase: Boolean,
        ): List<Member> =
            distinctStream()
                .flatMap { view -> view.getElementsByNickname(name, ignoreCase).stream() }
                .collect(Helpers.toUnmodifiableList())

        override fun getElementsWithRoles(vararg roles: Role): List<Member> =
            distinctStream()
                .flatMap { view -> view.getElementsWithRoles(*roles).stream() }
                .collect(Helpers.toUnmodifiableList())

        override fun getElementsWithRoles(roles: Collection<Role>): List<Member> =
            distinctStream()
                .flatMap { view -> view.getElementsWithRoles(roles).stream() }
                .collect(Helpers.toUnmodifiableList())
    }
}
