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
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import javax.annotation.Nonnull
import javax.annotation.Nullable

class FlatMapRestAction<I, O>(
    action: RestAction<I>,
    private val condition: Predicate<in I>?,
    private val function: Function<in I, out RestAction<O>>,
) : RestActionOperator<I, O>(action) {
    private fun supply(input: I): RestAction<O>? = applyContext(function.apply(input))

    override fun queue(
        @Nullable success: Consumer<in O>?,
        @Nullable failure: Consumer<in Throwable>?,
    ) {
        val catcher = contextWrap(failure)
        handle(action, catcher) { result ->
            if (condition != null && !condition.test(result)) {
                return@handle
            }
            val then = supply(result)
            if (then == null) { // caught by handle try/catch abstraction
                throw IllegalStateException("FlatMap operand is null")
            }
            then.queue(success, catcher)
        }
    }

    @Throws(RateLimitedException::class)
    override fun complete(shouldQueue: Boolean): O {
        val complete = action.complete(shouldQueue)

        if (condition != null && !condition.test(complete)) {
            throw CancellationException("FlatMap condition failed")
        }
        return supply(complete)!!.complete(shouldQueue)
    }

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<O> =
        action.submit(shouldQueue).thenCompose { result ->
            if (condition != null && !condition.test(result)) {
                val future = CompletableFuture<O>()
                future.cancel(true)

                future
            } else {
                supply(result)!!.submit(shouldQueue)
            }
        }
}
