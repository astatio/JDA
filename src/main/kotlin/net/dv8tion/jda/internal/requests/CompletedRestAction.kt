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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.annotation.Nonnull
import javax.annotation.Nullable

class CompletedRestAction<T> : AuditableRestAction<T> {
    private val api: JDA
    private val value: T
    private val error: Throwable?

    @Suppress("UNCHECKED_CAST")
    constructor(api: JDA, value: T?, error: Throwable?) {
        this.api = api
        this.value = value as T
        this.error = error
    }

    constructor(api: JDA, value: T?) : this(api, value, null)

    constructor(api: JDA, error: Throwable) : this(api, null, error)

    @Nonnull
    override fun reason(
        @Nullable reason: String?,
    ): AuditableRestAction<T> = this

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun setCheck(
        @Nullable checks: BooleanSupplier?,
    ): AuditableRestAction<T> = this

    @Nonnull
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): AuditableRestAction<T> = this

    @Nonnull
    override fun deadline(timestamp: Long): AuditableRestAction<T> = this

    override fun queue(
        @Nullable success: Consumer<in T>?,
        @Nullable failure: Consumer<in Throwable>?,
    ) {
        if (error == null) {
            if (success == null) {
                RestAction.getDefaultSuccess().accept(value)
            } else {
                success.accept(value)
            }
        } else {
            if (failure == null) {
                RestAction.getDefaultFailure().accept(error)
            } else {
                failure.accept(error)
            }
        }
    }

    @Suppress("ThrowsCount") // mirrors the Java rethrow ladder
    @Throws(RateLimitedException::class)
    override fun complete(shouldQueue: Boolean): T {
        if (error != null) {
            if (error is RateLimitedException) {
                throw error
            }
            if (error is RuntimeException) {
                throw error
            }
            if (error is Error) {
                throw error
            }
            throw IllegalStateException(error)
        }
        return value
    }

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (error != null) {
            future.completeExceptionally(error)
        } else {
            future.complete(value)
        }
        return future
    }
}
