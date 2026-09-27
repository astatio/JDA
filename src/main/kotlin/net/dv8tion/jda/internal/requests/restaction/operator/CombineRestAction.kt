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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.internal.utils.Checks
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.annotation.Nonnull
import javax.annotation.Nullable

class CombineRestAction<I1, I2, O>(
    private val action1: RestAction<I1>,
    private val action2: RestAction<I2>,
    private val accumulator: BiFunction<in I1, in I2, out O>,
) : RestAction<O> {
    @Volatile
    private var failed = false

    init {
        Checks.check(action1 !== action2, "Cannot combine a RestAction with itself!")
        val checks = BooleanSupplier { !failed }
        action1.addCheck(checks)
        action2.addCheck(checks)
    }

    @Nonnull
    override fun getJDA(): JDA = action1.jda

    @Nonnull
    override fun setCheck(
        @Nullable checks: BooleanSupplier?,
    ): RestAction<O> {
        val check = BooleanSupplier { !failed && (checks == null || checks.asBoolean) }
        action1.setCheck(check)
        action2.setCheck(check)
        return this
    }

    @Nonnull
    override fun addCheck(
        @Nonnull checks: BooleanSupplier,
    ): RestAction<O> {
        action1.addCheck(checks)
        action2.addCheck(checks)
        return this
    }

    @Nullable
    override fun getCheck(): BooleanSupplier {
        val check1 = action1.check
        val check2 = action2.check
        return BooleanSupplier {
            (check1 == null || check1.asBoolean) && (check2 == null || check2.asBoolean) && !failed
        }
    }

    @Nonnull
    override fun deadline(timestamp: Long): RestAction<O> {
        action1.deadline(timestamp)
        action2.deadline(timestamp)
        return this
    }

    // Faithful port of the Java original: both action callbacks run the accumulator and route
    // any failure — including an unchecked one — through the shared failure handler.
    @Suppress("TooGenericExceptionCaught")
    override fun queue(
        @Nullable success: Consumer<in O>?,
        @Nullable failure: Consumer<in Throwable>?,
    ) {
        val count = AtomicInteger(0)
        val result1 = AtomicReference<I1>()
        val result2 = AtomicReference<I2>()
        val failureCallback =
            Consumer<Throwable> { e ->
                if (failed) {
                    return@Consumer
                }
                failed = true
                RestActionOperator.doFailure(failure, e)
            }
        action1.queue(
            { s ->
                try {
                    result1.set(s)
                    if (count.incrementAndGet() == COMBINED_ACTION_COUNT) {
                        RestActionOperator.doSuccess(success, accumulator.apply(result1.get(), result2.get()))
                    }
                } catch (e: Exception) {
                    failureCallback.accept(e)
                }
            },
            failureCallback,
        )
        action2.queue(
            { s ->
                try {
                    result2.set(s)
                    if (count.incrementAndGet() == COMBINED_ACTION_COUNT) {
                        RestActionOperator.doSuccess(success, accumulator.apply(result1.get(), result2.get()))
                    }
                } catch (e: Exception) {
                    failureCallback.accept(e)
                }
            },
            failureCallback,
        )
    }

    @Throws(RateLimitedException::class)
    @Suppress("ThrowsCount", "SwallowedException")
    override fun complete(shouldQueue: Boolean): O {
        if (!shouldQueue) {
            return accumulator.apply(action1.complete(false), action2.complete(false))
        }
        try {
            return submit(true).join()
        } catch (e: CompletionException) {
            if (e.cause is RuntimeException) {
                throw e.cause as RuntimeException
            } else if (e.cause is RateLimitedException) {
                throw e.cause as RateLimitedException
            }
            throw e
        }
    }

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<O> =
        action1.submit(shouldQueue).thenCombine(action2.submit(shouldQueue), accumulator)

    private companion object {
        private const val COMBINED_ACTION_COUNT = 2
    }
}
