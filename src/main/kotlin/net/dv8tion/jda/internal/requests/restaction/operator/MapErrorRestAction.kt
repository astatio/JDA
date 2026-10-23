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
import java.util.concurrent.CompletionException
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import javax.annotation.Nonnull
import javax.annotation.Nullable

class MapErrorRestAction<T>(
    action: RestAction<T>,
    private val filter: Predicate<in Throwable>,
    private val map: Function<in Throwable, out T>,
) : RestActionOperator<T, T>(action) {
    // Faithful port of the Java error-mapping operator: the filter/map callbacks may throw
    // anything, and the error is re-thrown through the failure handler.
    @Suppress("TooGenericExceptionCaught")
    override fun queue(
        @Nullable success: Consumer<in T>?,
        @Nullable failure: Consumer<in Throwable>?,
    ) {
        action.queue(
            success,
            // Use contextWrap so error has a context cause
            contextWrap { error ->
                try {
                    if (filter.test(error)) {
                        doSuccess(success, map.apply(error))
                    } else {
                        doFailure(failure, error)
                    }
                } catch (e: Throwable) {
                    doFailure(failure, Helpers.appendCause(e, error))
                }
            },
        )
    }

    @Throws(RateLimitedException::class)
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    override fun complete(shouldQueue: Boolean): T {
        try {
            return action.complete(shouldQueue)
        } catch (error: Throwable) {
            try {
                if (filter.test(error)) {
                    return map.apply(error)
                }
            } catch (e: Throwable) {
                fail(Helpers.appendCause(e, error))
            }
            if (error is RateLimitedException) {
                throw error
            } else {
                fail(error)
            }
        }
        throw AssertionError("Unreachable")
    }

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<T> =
        action.submit(shouldQueue).handle { value, error ->
            var result = value
            if (error != null) {
                val unwrapped = if (error is CompletionException && error.cause != null) error.cause!! else error
                if (filter.test(unwrapped)) {
                    result = map.apply(unwrapped)
                } else {
                    fail(unwrapped)
                }
            }
            result
        }

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
