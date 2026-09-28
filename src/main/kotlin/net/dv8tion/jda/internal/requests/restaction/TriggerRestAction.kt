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

package net.dv8tion.jda.internal.requests.restaction

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.exceptions.ContextException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.requests.RestActionImpl
import okhttp3.RequestBody
import java.util.ArrayList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiFunction
import java.util.function.Consumer
import javax.annotation.Nonnull

open class TriggerRestAction<T> : RestActionImpl<T> {
    private val mutex = ReentrantLock()
    private val callbacks: MutableList<Runnable> = ArrayList()

    @Volatile
    private var isReady = false

    @Volatile
    private var exception: Throwable? = null

    constructor(api: JDA, route: Route.CompiledRoute) : super(api, route)

    constructor(api: JDA, route: Route.CompiledRoute, data: DataObject) : super(api, route, data)

    constructor(api: JDA, route: Route.CompiledRoute, data: RequestBody) : super(api, route, data)

    constructor(api: JDA, route: Route.CompiledRoute, handler: BiFunction<Response, Request<T>, T>) :
        super(api, route, handler)

    constructor(
        api: JDA,
        route: Route.CompiledRoute,
        data: DataObject,
        handler: BiFunction<Response, Request<T>, T>,
    ) : super(api, route, data, handler)

    constructor(
        api: JDA,
        route: Route.CompiledRoute,
        data: RequestBody,
        handler: BiFunction<Response, Request<T>, T>,
    ) : super(api, route, data, handler)

    fun run() {
        MiscUtil.locked(
            mutex,
            Runnable {
                isReady = true
                callbacks.forEach(Runnable::run)
            },
        )
    }

    fun fail(throwable: Throwable?) {
        MiscUtil.locked(
            mutex,
            Runnable {
                exception = throwable
                callbacks.forEach(Runnable::run)
            },
        )
    }

    fun onReady(callback: Runnable) {
        MiscUtil.locked(
            mutex,
            Runnable {
                if (isReady || exception != null) {
                    callback.run()
                } else {
                    callbacks.add(callback)
                }
            },
        )
    }

    override fun queue(
        success: Consumer<in T>?,
        failure: Consumer<in Throwable>?,
    ) {
        if (isReady) {
            super.queue(success, failure)
            return
        }

        val onFailure = wrapContext(failure)
        onReady(
            Runnable {
                if (exception != null) {
                    onFailure.accept(exception!!)
                } else {
                    super.queue(success, onFailure)
                }
            },
        )
    }

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<T> {
        if (isReady) {
            return super.submit(shouldQueue)
        }
        val future = CompletableFuture<T>()
        val onFailure = wrapContext(Consumer(future::completeExceptionally))

        onReady(
            Runnable {
                if (exception != null) {
                    onFailure.accept(exception!!)
                    return@Runnable
                }

                val handle = super.submit(shouldQueue)
                handle.whenComplete { success, error ->
                    if (error != null) {
                        onFailure.accept(error)
                    } else {
                        future.complete(success)
                    }
                }

                // Handle cancel forwarding
                future.whenComplete { _, _ ->
                    if (future.isCancelled) {
                        handle.cancel(false)
                    }
                }
            },
        )
        return future
    }

    private fun wrapContext(failure: Consumer<in Throwable>?): Consumer<in Throwable> {
        val actual: Consumer<in Throwable> = failure ?: RestActionImpl.getDefaultFailure()
        if (!RestActionImpl.isPassContext() || actual is ContextException.ContextConsumer) {
            return actual
        }
        return ContextException.here(actual)
    }
}
