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
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.RestFuture
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.AttachedFile
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.JDALogger
import okhttp3.RequestBody
import org.apache.commons.collections4.map.CaseInsensitiveMap
import org.slf4j.Logger
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.BiFunction
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.annotation.Nonnull
import javax.annotation.Nullable

@Suppress("DEPRECATION")
private fun jsonBody(data: DataObject?): RequestBody? = data?.let { RequestBody.create(Requester.MEDIA_TYPE_JSON, it.toJson()) }

open class RestActionImpl<T> private constructor(
    api: JDAImpl,
    private val route: Route.CompiledRoute?,
    private val data: RequestBody?,
    private val handler: BiFunction<Response, Request<T>, T>?,
    private var rawData: Any?,
) : RestAction<T> {
    @JvmField
    protected val api: JDAImpl = api

    private var errorMapper: ErrorMapper? = null
    private var priority: Boolean = false
    private var deadline: Long = 0
    private var checks: BooleanSupplier? = null

    constructor(api: JDA, route: Route.CompiledRoute) : this(api as JDAImpl, route, null, null, null)

    constructor(api: JDA, route: Route.CompiledRoute, data: DataObject?) :
        this(api as JDAImpl, route, jsonBody(data), null, data)

    constructor(api: JDA, route: Route.CompiledRoute, data: RequestBody?) :
        this(api as JDAImpl, route, data, null, null)

    constructor(api: JDA, route: Route.CompiledRoute, handler: BiFunction<Response, Request<T>, T>) :
        this(api as JDAImpl, route, null, handler, null)

    constructor(
        api: JDA,
        route: Route.CompiledRoute,
        data: DataObject?,
        handler: BiFunction<Response, Request<T>, T>?,
    ) : this(api as JDAImpl, route, jsonBody(data), handler, data)

    constructor(
        api: JDA,
        route: Route.CompiledRoute,
        data: RequestBody?,
        handler: BiFunction<Response, Request<T>, T>?,
    ) : this(api as JDAImpl, route, data, handler, null)

    init {
        Checks.notNull(api, "api")
    }

    fun setErrorMapper(errorMapper: ErrorMapper?) {
        this.errorMapper = errorMapper
    }

    fun priority(): RestActionImpl<T> {
        priority = true
        return this
    }

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun setCheck(checks: BooleanSupplier?): RestAction<T> {
        this.checks = checks
        return this
    }

    @Nullable
    override fun getCheck(): BooleanSupplier? = checks

    @Nonnull
    override fun deadline(timestamp: Long): RestAction<T> {
        this.deadline = timestamp
        return this
    }

    override fun queue(
        success: Consumer<in T>?,
        failure: Consumer<in Throwable>?,
    ) {
        val route = finalizeRoute()
        Checks.notNull(route, "Route")
        val data = finalizeData()
        val headers = finalizeHeaders()
        val finisher = getFinisher()

        @Suppress("UNCHECKED_CAST")
        val finalSuccess: Consumer<Any> = (success as Consumer<Any>?) ?: defaultSuccess
        val finalFailure: Consumer<in Throwable> = failure ?: defaultFailure
        api.requester.request(
            Request(
                this,
                finalSuccess,
                finalFailure,
                finisher,
                true,
                data,
                rawData,
                getDeadline(),
                priority,
                route,
                headers,
            ),
        )
    }

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<T> {
        val route = finalizeRoute()
        Checks.notNull(route, "Route")
        val data = finalizeData()
        val headers = finalizeHeaders()
        val finisher = getFinisher()
        return RestFuture(this, shouldQueue, finisher, data, rawData, getDeadline(), priority, route, headers)
    }

    @Suppress("SwallowedException") // the cause is rethrown with a refreshed stacktrace
    override fun complete(shouldQueue: Boolean): T =
        try {
            if (CallbackContext.isCallbackContext()) {
                throw IllegalStateException(
                    "Preventing use of complete() in callback threads! This operation can be a deadlock cause",
                )
            }
            submit(shouldQueue).join()
        } catch (e: CompletionException) {
            val cause = e.cause
            if (cause is ErrorResponseException) {
                // this method will update the stacktrace to the current thread stack
                throw cause.fillInStackTrace() as ErrorResponseException
            }
            if (cause is RateLimitedException) {
                throw cause.fillInStackTrace() as RateLimitedException
            }
            if (cause is RuntimeException) {
                throw cause
            }
            if (cause is Error) {
                throw cause
            }
            throw e
        }

    protected open fun finalizeData(): RequestBody? = data

    protected open fun finalizeRoute(): Route.CompiledRoute = route!!

    protected open fun finalizeHeaders(): CaseInsensitiveMap<String, String>? = null

    protected open fun finalizeChecks(): BooleanSupplier? = null

    @Suppress("DEPRECATION")
    protected fun getRequestBody(`object`: DataObject): RequestBody {
        this.rawData = `object`
        return RequestBody.create(Requester.MEDIA_TYPE_JSON, `object`.toJson())
    }

    @Suppress("DEPRECATION")
    protected fun getRequestBody(array: DataArray): RequestBody {
        this.rawData = array
        return RequestBody.create(Requester.MEDIA_TYPE_JSON, array.toJson())
    }

    @Nonnull
    protected fun getMultipartBody(
        files: Set<AttachedFile>,
        json: DataObject,
    ): RequestBody {
        val payloadJson = getRequestBody(json)
        if (files.isEmpty()) {
            return payloadJson
        }
        return AttachedFile.createMultipartBody(files, payloadJson).build()
    }

    private fun getFinisher(): CheckWrapper {
        val pre = finalizeChecks()
        val wrapped = checks
        return if (pre != null || wrapped != null) CheckWrapper(wrapped, pre) else CheckWrapper.EMPTY
    }

    open fun handleResponse(
        response: Response,
        request: Request<T>,
    ) {
        if (response.isOk) {
            handleSuccess(response, request)
        } else if (response.isRateLimit) {
            request.onRateLimited(response)
        } else {
            val exception = request.createErrorResponseException(response)
            val mappedThrowable = errorMapper?.apply(response, request, exception)
            if (mappedThrowable != null) {
                request.onFailure(mappedThrowable)
            } else {
                request.onFailure(exception)
            }
        }
    }

    protected open fun handleSuccess(
        response: Response,
        request: Request<T>,
    ) {
        if (handler == null) {
            request.onSuccess(null)
        } else {
            request.onSuccess(handler.apply(response, request))
        }
    }

    private fun getDeadline(): Long =
        if (deadline > 0) {
            deadline
        } else if (defaultTimeout > 0) {
            System.currentTimeMillis() + defaultTimeout
        } else {
            0
        }

    /*
       useful for final permission checks:

       @Override
       protected BooleanSupplier finalizeChecks()
       {
           // throw exception, if missing perms
           return () -> hasPermission(Permission.MESSAGE_SEND);
       }
     */
    protected open class CheckWrapper(
        protected val wrapped: BooleanSupplier?,
        protected val pre: BooleanSupplier?,
    ) : BooleanSupplier {
        fun pre(): Boolean = pre == null || pre.asBoolean

        fun test(): Boolean = wrapped == null || wrapped.asBoolean

        override fun getAsBoolean(): Boolean = pre() && test()

        companion object {
            @JvmField
            val EMPTY: CheckWrapper =
                object : CheckWrapper(null, null) {
                    override fun getAsBoolean(): Boolean = true
                }
        }
    }

    companion object {
        @JvmField
        val LOG: Logger = JDALogger.getLog(RestAction::class.java)

        private var defaultSuccess: Consumer<Any> = Consumer {}

        private var defaultFailure: Consumer<in Throwable> =
            Consumer { t ->
                if (t is CancellationException || t is TimeoutException) {
                    LOG.debug(t.message)
                } else if (LOG.isDebugEnabled || t !is ErrorResponseException) {
                    LOG.error("RestAction queue returned failure", t)
                } else if (t.cause != null) {
                    LOG.error(
                        "RestAction queue returned failure: [{}] {}",
                        t.javaClass.simpleName,
                        t.message,
                        t.cause,
                    )
                } else {
                    LOG.error("RestAction queue returned failure: [{}] {}", t.javaClass.simpleName, t.message)
                }
            }

        private var passContext: Boolean = true
        private var defaultTimeout: Long = 0

        @JvmStatic
        fun setPassContext(enable: Boolean) {
            passContext = enable
        }

        @JvmStatic
        fun isPassContext(): Boolean = passContext

        @JvmStatic
        fun setDefaultFailure(callback: Consumer<in Throwable>?) {
            defaultFailure = callback ?: Consumer {}
        }

        @JvmStatic
        fun setDefaultSuccess(callback: Consumer<Any>?) {
            defaultSuccess = callback ?: Consumer {}
        }

        @JvmStatic
        fun setDefaultTimeout(
            timeout: Long,
            unit: TimeUnit,
        ) {
            Checks.notNull(unit, "TimeUnit")
            defaultTimeout = unit.toMillis(timeout)
        }

        @JvmStatic
        fun getDefaultTimeout(): Long = defaultTimeout

        @JvmStatic
        fun getDefaultFailure(): Consumer<in Throwable> = defaultFailure

        @JvmStatic
        fun getDefaultSuccess(): Consumer<Any> = defaultSuccess
    }
}
