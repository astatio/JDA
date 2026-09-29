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
import java.util.function.Consumer
import java.util.function.Function
import javax.annotation.Nonnull
import javax.annotation.Nullable

class MapRestAction<I, O>(
    action: RestAction<I>,
    private val function: Function<in I, out O>,
) : RestActionOperator<I, O>(action) {
    override fun queue(
        @Nullable success: Consumer<in O>?,
        @Nullable failure: Consumer<in Throwable>?,
    ) {
        handle(action, failure) { result -> doSuccess(success, function.apply(result)) }
    }

    @Throws(RateLimitedException::class)
    override fun complete(shouldQueue: Boolean): O = function.apply(action.complete(shouldQueue))

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<O> = action.submit(shouldQueue).thenApply(function)
}
