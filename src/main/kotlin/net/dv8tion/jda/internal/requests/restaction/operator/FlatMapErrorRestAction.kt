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
import net.dv8tion.jda.internal.utils.Helpers
import org.jetbrains.annotations.Contract
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import javax.annotation.Nonnull
import javax.annotation.Nullable

class FlatMapErrorRestAction<T>(
    action: RestAction<T>,
    private val filter: Predicate<in Throwable>,
    private val map: Function<in Throwable, out RestAction<out T>>,
) : RestActionOperator<T, T>(action) {
    // Faithful port of the Java error-mapping operator: the filter/map callbacks may throw
    // anything, and the error is re-thrown through the failure handler.
    @Suppress("TooGenericExceptionCaught")
    override fun queue(
        @Nullable success: Consumer<in T>?,
        @Nullable failure: Consumer<in Throwable>?,
    ) {
        val contextFailure = contextWrap(failure)
        action.queue(
            success,
            contextWrap { error ->
                try {
                    if (filter.test(error)) {
                        // If check passed we can apply the fallback function and flatten it
                        val then: RestAction<out T>? = map.apply(error)
                        if (then == null) {
                            doFailure(failure, IllegalStateException("FlatMapError operand is null", error))
                            // No contextFailure because error already has context
                        } else {
                            // Use contextFailure here to apply new context to new errors
                            then.queue(success, contextFailure)
                        }
                    } else {
                        // No contextFailure because error already has context
                        doFailure(failure, error)
                    }
                } catch (e: Throwable) {
                    // No contextFailure because error already has context
                    doFailure(failure, Helpers.appendCause(e, error))
                }
            },
        )
    }

    @Throws(RateLimitedException::class)
    @Suppress("TooGenericExceptionCaught", "ThrowsCount", "ReferenceEquality")
    override fun complete(shouldQueue: Boolean): T {
        try {
            return action.complete(shouldQueue)
        } catch (error: Throwable) {
            try {
                if (filter.test(error)) {
                    val then: RestAction<out T>? = map.apply(error)
                    if (then == null) {
                        throw IllegalStateException("FlatMapError operand is null", error)
                    }
                    return then.complete(shouldQueue)
                }
            } catch (e: Throwable) {
                // Rethrow known if error mapping threw another error
                if (e is IllegalStateException && e.cause === error) {
                    throw e
                } else if (e is RateLimitedException) {
                    throw Helpers.appendCause(e, error)
                } else {
                    fail(Helpers.appendCause(e, error))
                }
            }
            fail(error)
        }
        throw AssertionError("Unreachable")
    }

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<T> =
        action
            .submit(shouldQueue)
            .handle { result, error ->
                if (filter.test(error)) {
                    map.apply(error).submit(shouldQueue).thenApply { it }
                } else {
                    CompletableFuture.completedFuture(result)
                }
            }.thenCompose(Function.identity())

    @Contract("_ -> fail")
    @Suppress("TooGenericExceptionThrown", "ThrowsCount")
    private fun fail(error: Throwable) {
        when (error) {
            is RuntimeException -> throw error
            is Error -> throw error
            else -> throw RuntimeException(error)
        }
    }
}
