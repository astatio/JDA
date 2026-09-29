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

package net.dv8tion.jda.internal.requests.restaction.operator

import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.requests.RestAction
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DelayRestAction<T>(
    action: RestAction<T>,
    private val unit: TimeUnit,
    private val delay: Long,
    scheduler: ScheduledExecutorService?,
) : RestActionOperator<T, T>(action) {
    private val scheduler: ScheduledExecutorService = scheduler ?: action.jda.rateLimitPool

    override fun queue(
        @Nullable success: Consumer<in T>?,
        @Nullable failure: Consumer<in Throwable>?,
    ) {
        handle(action, failure) { result -> scheduler.schedule({ doSuccess(success, result) }, delay, unit) }
    }

    @Throws(RateLimitedException::class)
    @Suppress("TooGenericExceptionThrown")
    override fun complete(shouldQueue: Boolean): T {
        val result = action.complete(shouldQueue)
        try {
            unit.sleep(delay)
            return result
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        queue({ future.complete(it) }, { future.completeExceptionally(it) })
        return future
    }
}
