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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.internal.utils.Checks
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DeferredRestAction<T, R : RestAction<T>> :
    AuditableRestAction<T>,
    CacheRestAction<T> {
    private val api: JDA
    private val type: Class<T>?
    private val valueSupplier: Supplier<T?>?
    private val actionSupplier: Supplier<R>

    private var useCache: Boolean = true
    private var reason: String? = null
    private var deadline: Long = -1
    private var isAction: BooleanSupplier? = null
    private var transitiveChecks: BooleanSupplier? = null

    constructor(api: JDA, actionSupplier: Supplier<R>) : this(api, null, null, actionSupplier)

    constructor(
        api: JDA,
        type: Class<T>?,
        valueSupplier: Supplier<T?>?,
        actionSupplier: Supplier<R>,
    ) {
        this.api = api
        this.type = type
        this.valueSupplier = valueSupplier
        this.actionSupplier = actionSupplier
    }

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun reason(reason: String?): AuditableRestAction<T> {
        this.reason = reason
        return this
    }

    @Nonnull
    override fun setCheck(checks: BooleanSupplier?): DeferredRestAction<T, R> {
        this.transitiveChecks = checks
        return this
    }

    @Nullable
    override fun getCheck(): BooleanSupplier? = transitiveChecks

    @Nonnull
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): DeferredRestAction<T, R> {
        Checks.notNull(unit, "TimeUnit")
        return deadline(if (timeout <= 0) 0 else System.currentTimeMillis() + unit.toMillis(timeout))
    }

    @Nonnull
    override fun deadline(timestamp: Long): DeferredRestAction<T, R> {
        this.deadline = timestamp
        return this
    }

    @Nonnull
    override fun useCache(useCache: Boolean): CacheRestAction<T> {
        this.useCache = useCache
        return this
    }

    fun setCacheCheck(checks: BooleanSupplier?): AuditableRestAction<T> {
        this.isAction = checks
        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun queue(
        success: Consumer<in T>?,
        failure: Consumer<in Throwable>?,
    ) {
        val finalSuccess: Consumer<Any?> = (success as Consumer<Any?>?) ?: RestAction.getDefaultSuccess()

        if (type == null) {
            val checks = this.isAction
            if (checks != null && checks.asBoolean) {
                getAction().queue(success, failure)
            } else {
                finalSuccess.accept(null)
            }
            return
        }

        val value = valueSupplier!!.get()
        if (!useCache || value == null) {
            getAction().queue(success, failure)
        } else {
            finalSuccess.accept(value)
        }
    }

    @Suppress("ReturnCount") // mirrors the Java branching
    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<T> {
        if (type == null) {
            val checks = this.isAction
            if (checks != null && checks.asBoolean) {
                return getAction().submit(shouldQueue)
            }
            return CompletableFuture.completedFuture(null)
        }
        val value = valueSupplier!!.get()
        if (useCache && value != null) {
            return CompletableFuture.completedFuture(value)
        }
        return getAction().submit(shouldQueue)
    }

    @Suppress("ReturnCount") // mirrors the Java branching
    @Throws(RateLimitedException::class)
    override fun complete(shouldQueue: Boolean): T {
        if (type == null) {
            val checks = this.isAction
            if (checks != null && checks.asBoolean) {
                return getAction().complete(shouldQueue)
            }
            @Suppress("UNCHECKED_CAST")
            return null as T
        }
        val value = valueSupplier!!.get()
        if (useCache && value != null) {
            return value
        }
        return getAction().complete(shouldQueue)
    }

    private fun getAction(): R {
        val action = actionSupplier.get()
        action.setCheck(transitiveChecks)
        if (deadline >= 0) {
            action.deadline(deadline)
        }
        if (action is AuditableRestAction<*> && reason != null) {
            (action as AuditableRestAction<*>).reason(reason)
        }
        return action
    }
}
