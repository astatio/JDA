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

package net.dv8tion.jda.internal.requests.restaction.pagination

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.utils.Procedure
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections
import java.util.NoSuchElementException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiFunction
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.annotation.Nonnull

abstract class PaginationActionImpl<T : Any, M : PaginationAction<T, M>> protected constructor(
    api: JDA,
    route: Route.CompiledRoute?,
    maxLimit: Int,
    minLimit: Int,
    initialLimit: Int,
) : RestActionImpl<@JvmSuppressWildcards List<T>>(api, route),
    PaginationAction<T, M> {
    @JvmField
    protected val cached: MutableList<T> = CopyOnWriteArrayList()

    @JvmField
    protected val maxLimit: Int = maxLimit

    @JvmField
    protected val minLimit: Int = minLimit

    @JvmField
    protected val limit: AtomicInteger = AtomicInteger(initialLimit)

    @JvmField
    protected var order: PaginationAction.PaginationOrder = PaginationAction.PaginationOrder.BACKWARD

    @Volatile
    @JvmField
    protected var iteratorIndex: Long = 0

    @Volatile
    @JvmField
    protected var lastKey: Long = 0

    @Volatile
    @JvmField
    protected var last: T? = null

    @Volatile
    @JvmField
    protected var useCache: Boolean = true

    /**
     * Creates a new PaginationAction instance
     * <br>This is used for PaginationActions that should not deal with
     * [limit][PaginationAction.limit]
     *
     * @param api
     *        The current JDA instance
     */
    constructor(api: JDA) : this(api, null, 0, 0, 0)

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun skipTo(id: Long): M {
        if (cached.isNotEmpty()) {
            val cmp = java.lang.Long.compareUnsigned(lastKey, id)
            if (cmp < 0) { // old - new < 0 => old < new
                throw IllegalArgumentException("Cannot jump to that id, it is newer than the current oldest element.")
            }
        }
        if (lastKey != id) {
            last = null
        }
        iteratorIndex = id
        lastKey = id
        return this as M
    }

    override fun getLastKey(): Long = lastKey

    @Nonnull
    override fun getOrder(): PaginationAction.PaginationOrder = order

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun order(
        @Nonnull order: PaginationAction.PaginationOrder,
    ): M {
        Checks.notNull(order, "PaginationOrder")
        if (order !== this.order) {
            if (!isEmpty()) {
                throw IllegalStateException("Cannot change pagination order after retrieving.")
            }
            if (!getSupportedOrders().contains(order)) {
                throw IllegalArgumentException("Cannot use PaginationOrder.$order for this pagination endpoint.")
            }
        }
        this.order = order
        return this as M
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): M = super.setCheck(checks) as M

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): M = super.timeout(timeout, unit) as M

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): M = super.deadline(timestamp) as M

    override fun cacheSize(): Int = cached.size

    override fun isEmpty(): Boolean = cached.isEmpty()

    @Nonnull
    override fun getCached(): List<T> = Collections.unmodifiableList(cached)

    @Nonnull
    override fun getLast(): T {
        val last = this.last
        if (last == null) {
            throw NoSuchElementException("No entities have been retrieved yet.")
        }
        return last
    }

    @Nonnull
    override fun getFirst(): T {
        if (cached.isEmpty()) {
            throw NoSuchElementException("No entities have been retrieved yet.")
        }
        return cached[0]
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun limit(limit: Int): M {
        Checks.check(maxLimit == 0 || limit <= maxLimit, "Limit must not exceed %d!", maxLimit)
        Checks.check(minLimit == 0 || limit >= minLimit, "Limit must be greater or equal to %d", minLimit)
        this.limit.set(limit)
        return this as M
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun cache(enableCache: Boolean): M {
        this.useCache = enableCache
        return this as M
    }

    override fun isCacheEnabled(): Boolean = useCache

    final override fun getMaxLimit(): Int = maxLimit

    final override fun getMinLimit(): Int = minLimit

    final override fun getLimit(): Int = limit.get()

    @Nonnull
    override fun takeAsync(amount: Int): CompletableFuture<List<T>> =
        takeAsync0(amount) { task, list ->
            forEachAsync(
                { value ->
                    list.add(value)
                    list.size < amount
                },
                Consumer { task.completeExceptionally(it) },
            )
        }

    @Nonnull
    override fun takeRemainingAsync(amount: Int): CompletableFuture<List<T>> =
        takeAsync0(amount) { task, list ->
            forEachRemainingAsync(
                { value ->
                    list.add(value)
                    list.size < amount
                },
                Consumer { task.completeExceptionally(it) },
            )
        }

    private fun takeAsync0(
        amount: Int,
        converter: BiFunction<CompletableFuture<*>, MutableList<T>, CompletableFuture<*>>,
    ): CompletableFuture<List<T>> {
        val task = CompletableFuture<List<T>>()
        val list: MutableList<T> = ArrayList(amount)
        val promise: CompletableFuture<*> = converter.apply(task, list)
        promise.thenRun { task.complete(list) }
        return task
    }

    @Nonnull
    override fun iterator(): PaginationAction.PaginationIterator<T> = PaginationAction.PaginationIterator(cached) { getNextChunk() }

    @Nonnull
    @Suppress("TooGenericExceptionCaught")
    override fun forEachAsync(
        @Nonnull action: Procedure<in T>,
        @Nonnull failure: Consumer<in Throwable>,
    ): CompletableFuture<*> {
        Checks.notNull(action, "Procedure")
        Checks.notNull(failure, "Failure Consumer")

        val task = CompletableFuture<Any?>()
        val acceptor: Consumer<List<T>> =
            ChainedConsumer(task, action) { throwable ->
                task.completeExceptionally(throwable)
                failure.accept(throwable)
            }
        try {
            acceptor.accept(cached)
        } catch (ex: Exception) {
            failure.accept(ex)
            task.completeExceptionally(ex)
        }
        return task
    }

    @Nonnull
    @Suppress("TooGenericExceptionCaught")
    override fun forEachRemainingAsync(
        @Nonnull action: Procedure<in T>,
        @Nonnull failure: Consumer<in Throwable>,
    ): CompletableFuture<*> {
        Checks.notNull(action, "Procedure")
        Checks.notNull(failure, "Failure Consumer")

        val task = CompletableFuture<Any?>()
        val acceptor: Consumer<List<T>> =
            ChainedConsumer(task, action) { throwable ->
                task.completeExceptionally(throwable)
                failure.accept(throwable)
            }
        try {
            acceptor.accept(getRemainingCache())
        } catch (ex: Exception) {
            failure.accept(ex)
            task.completeExceptionally(ex)
        }
        return task
    }

    @Suppress("ReturnCount")
    override fun forEachRemaining(
        @Nonnull action: Procedure<in T>,
    ) {
        Checks.notNull(action, "Procedure")
        val queue = ArrayDeque<T>()
        while (queue.addAll(getNextChunk())) {
            while (!queue.isEmpty()) {
                val it = queue.poll()
                if (!action.execute(it)) {
                    // set the iterator index for next call of remaining
                    updateIndex(it)
                    return
                }
            }
        }
    }

    // Introduced for paginating archived threads, because two endpoints require a different request
    // parameter value format.
    // May become more useful if discord introduces more pagination endpoints not using ids.
    // Check ThreadChannelPaginationActionImpl
    // Background of #getPaginationLastEvaluatedKey:
    //     Archived thread channel pagination (example:
    // TextChannel#retrieveArchivedPublicThreadChannels) would throw an exception,
    //     where Discord complained about receiving a snowflake instead of an ISO8601 date.
    //     The snowflake originated from this class (creating initial & subsequent requests),
    //     while the correct value was set in ThreadChannelPaginationActionImpl for the initial
    // request
    //     and appended as a second value for subsequent requests.
    //     However, withQueryParams is a simple string append and Discord only reads the first
    // parameter.
    //     If you debugged, you would see some duplicated fields on the compiled route.
    //     The fix here is to let the implementor supply the "last" string value, which could be
    // anything,
    //     while the default implementation still would provide snowflakes
    @Nonnull
    protected open fun getPaginationLastEvaluatedKey(
        lastId: Long,
        last: T?,
    ): String = java.lang.Long.toUnsignedString(lastId)

    override fun finalizeRoute(): Route.CompiledRoute {
        var route = super.finalizeRoute()

        val limit = getLimit().toString()
        val localLastKey = lastKey

        route = route.withQueryParams("limit", limit)

        if (localLastKey != 0L) {
            route = route.withQueryParams(order.key, getPaginationLastEvaluatedKey(localLastKey, last))
        } else if (order === PaginationAction.PaginationOrder.FORWARD) {
            route = route.withQueryParams("after", getPaginationLastEvaluatedKey(0, last))
        }

        return route
    }

    protected fun getRemainingCache(): List<T> {
        val index = getIteratorIndex()
        if (useCache && index > -1 && index < cached.size) {
            return cached.subList(index, cached.size)
        }
        return Collections.emptyList()
    }

    fun getNextChunk(): List<T> {
        var list = getRemainingCache()
        if (list.isNotEmpty()) {
            return list
        }

        val current = limit.getAndSet(getMaxLimit())
        list = complete()
        limit.set(current)
        return list
    }

    protected abstract fun getKey(it: T): Long

    protected fun getIteratorIndex(): Int {
        for (i in cached.indices) {
            if (getKey(cached[i]) == iteratorIndex) {
                return i + 1
            }
        }
        return -1
    }

    protected fun updateIndex(it: T) {
        val key = getKey(it)
        iteratorIndex = key
        if (!useCache) {
            lastKey = key
            last = it
        }
    }

    protected inner class ChainedConsumer(
        @JvmField protected val task: CompletableFuture<Any?>,
        @JvmField protected val action: Procedure<in T>,
        @JvmField protected val throwableConsumer: Consumer<Throwable>,
    ) : Consumer<List<T>> {
        // Kept protected to match the Java field shape, even though this class is final
        @Suppress("ProtectedMemberInFinalClass")
        @JvmField
        protected var initial: Boolean = true

        @Suppress("ReturnCount")
        override fun accept(list: List<T>) {
            if (list.isEmpty() && !initial) {
                task.complete(null)
                return
            }
            initial = false

            var previous: T? = null
            for (it in list) {
                if (task.isCancelled) {
                    if (previous != null) {
                        updateIndex(previous)
                    }
                    return
                }
                if (action.execute(it)) {
                    previous = it
                    continue
                }
                // set the iterator index for next call of remaining
                updateIndex(it)
                task.complete(null)
                return
            }

            val currentLimit = limit.getAndSet(maxLimit)
            this@PaginationActionImpl.queue(this, throwableConsumer)
            limit.set(currentLimit)
        }
    }
}
