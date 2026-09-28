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
import net.dv8tion.jda.api.requests.Method
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.RestConfig
import net.dv8tion.jda.api.requests.RestRateLimiter
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.utils.IOUtil
import net.dv8tion.jda.internal.utils.JDALogger
import net.dv8tion.jda.internal.utils.config.AuthorizationConfig
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.Logger
import org.slf4j.MDC
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.RejectedExecutionException
import java.util.function.Consumer
import javax.annotation.Nonnull
import javax.annotation.Nullable
import javax.net.ssl.SSLPeerUnverifiedException

class Requester(
    api: JDA,
    private val authConfig: AuthorizationConfig,
    config: RestConfig,
    private val rateLimiter: RestRateLimiter,
) {
    private val api: JDAImpl = api as JDAImpl
    private val baseUrl: HttpUrl = config.baseUrl.toHttpUrl()
    private val userAgent: String = config.userAgent
    private val customBuilder: Consumer<in okhttp3.Request.Builder>? = config.customBuilder

    private val httpClient: OkHttpClient = this.api.httpClient

    // when we actually set the shard info we can also set the mdc context map,
    // before it makes no sense
    private var isContextReady = false
    private var contextMap: ConcurrentMap<String, String>? = null

    @Volatile
    private var retryOnTimeout = false

    fun setContextReady(ready: Boolean) {
        isContextReady = ready
    }

    fun setContext() {
        if (!isContextReady) {
            return
        }
        if (contextMap == null) {
            contextMap = api.contextMap
        }
        contextMap!!.forEach { (key, value) -> MDC.put(key, value) }
    }

    fun getJDA(): JDAImpl = api

    fun <T> request(apiRequest: Request<T>) {
        if (rateLimiter.isStopped) {
            throw RejectedExecutionException("The Requester has been stopped! No new requests can be requested!")
        }

        if (apiRequest.shouldQueue()) {
            rateLimiter.enqueue(WorkTask(apiRequest))
        } else {
            execute(WorkTask(apiRequest), true)
        }
    }

    private fun execute(task: WorkTask): okhttp3.Response? = execute(task, false)

    private fun execute(
        task: WorkTask,
        handleOnRateLimit: Boolean,
    ): okhttp3.Response? = execute(task, false, handleOnRateLimit)

    @Suppress("ReturnCount") // mirrors the Java control flow
    private fun execute(
        task: WorkTask,
        retried: Boolean,
        handleOnRatelimit: Boolean,
    ): okhttp3.Response? {
        val route = task.getRoute()

        val builder = okhttp3.Request.Builder()

        val url = route.toHttpUrl(baseUrl)
        builder.url(url)

        val apiRequest = task.request

        applyBody(apiRequest, builder)
        applyHeaders(apiRequest, builder)
        if (customBuilder != null) {
            try {
                customBuilder.accept(builder)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                LOG.error("Custom request builder caused exception", e)
            }
        }

        val request = builder.build()

        val rays = LinkedHashSet<String>()
        val responses = arrayOfNulls<okhttp3.Response>(RESPONSE_BUFFER_SIZE)
        // we have an array of all responses to later close them all at once
        // the response below this comment is used as the first successful response from the server
        var lastResponse: okhttp3.Response? = null
        try {
            LOG.trace("Executing request {} {}", task.getRoute().method, url)
            var code = 0
            @Suppress("LoopWithTooManyJumpStatements") // mirrors the Java retry loop
            for (attempt in responses.indices) {
                if (apiRequest.isSkipped) {
                    return null
                }

                val call: Call = httpClient.newCall(request)
                lastResponse = call.execute()
                code = lastResponse.code
                responses[attempt] = lastResponse
                val cfRay = lastResponse.header("CF-RAY")
                if (cfRay != null) {
                    rays.add(cfRay)
                }

                // Retry a few specific server errors that are related to server issues
                if (!shouldRetry(code)) {
                    break
                }

                LOG.debug(
                    "Requesting {} -> {} returned status {}... retrying (attempt {})",
                    apiRequest.route.method,
                    url,
                    code,
                    attempt + 1,
                )
                try {
                    Thread.sleep(RETRY_BASE_DELAY_MS shl attempt)
                } catch (ignored: InterruptedException) {
                    break
                }
            }

            LOG.trace(
                "Finished Request {} {} with code {}",
                route.method,
                lastResponse!!.request.url,
                code,
            )

            if (shouldRetry(code)) {
                // Epic failure from other end. Attempted 4 times.
                task.handleResponse(lastResponse, -1, rays)
                return null
            }

            if (rays.isNotEmpty()) {
                LOG.debug("Received response with following cf-rays: {}", rays)
            }

            if (handleOnRatelimit && code == TOO_MANY_REQUESTS) {
                val retryAfter = parseRetry(lastResponse)
                task.handleResponse(lastResponse, retryAfter, rays)
            } else if (code != TOO_MANY_REQUESTS) {
                task.handleResponse(lastResponse, rays)
            } else if (getContentType(lastResponse).startsWith("application/json")) { // potentially not json when cloudflare does 429
                // On 429, replace the retry-after header if its wrong (discord moment)
                // We just pick whichever is bigger between body and header
                try {
                    IOUtil.getBody(lastResponse)!!.use { body ->
                        val retryAfterBody =
                            Math
                                .ceil(
                                    DataObject.fromJson(body).getDouble("retry_after", 0.0),
                                ).toLong()
                        val retryAfterHeader =
                            lastResponse.header(RestRateLimiter.RETRY_AFTER_HEADER)!!.toLong()
                        lastResponse =
                            lastResponse
                                .newBuilder()
                                .header(
                                    RestRateLimiter.RETRY_AFTER_HEADER,
                                    Math.max(retryAfterHeader, retryAfterBody).toString(),
                                ).build()
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    LOG.warn("Failed to parse retry-after response body", e)
                }
            }

            return lastResponse
        } catch (e: UnknownHostException) {
            LOG.error("DNS resolution failed: {}", e.message)
            task.handleResponse(e, rays)
            return null
        } catch (e: IOException) {
            if (retryOnTimeout && !retried && isRetry(e)) {
                return execute(task, true, handleOnRatelimit)
            }
            LOG.error("There was an I/O error while executing a REST request: {}", e.message)
            task.handleResponse(e, rays)
            return null
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            LOG.error("There was an unexpected error while executing a REST request", e)
            task.handleResponse(e, rays)
            return null
        } finally {
            for (r in responses) {
                if (r == null) {
                    break
                }
                r.close()
            }
        }
    }

    private fun applyBody(
        apiRequest: Request<*>,
        builder: okhttp3.Request.Builder,
    ) {
        val method: Method = apiRequest.route.method
        var body: RequestBody? = apiRequest.body

        if (body == null && method.requiresRequestBody()) {
            body = EMPTY_BODY
        }

        builder.method(method.toString(), body)

        if (apiRequest.rawBody != null) {
            LOG.trace(
                "Sending request on route {}/{} with body\n{}",
                method,
                apiRequest.route.compiledRoute,
                apiRequest.rawBody,
            )
        }
    }

    private fun applyHeaders(
        apiRequest: Request<*>,
        builder: okhttp3.Request.Builder,
    ) {
        builder
            .header("user-agent", userAgent)
            .header("accept-encoding", "gzip")
            .header("authorization", authConfig.getToken())
            .header("x-ratelimit-precision", "millisecond") // still sending this in case of regressions

        // Apply custom headers like X-Audit-Log-Reason
        // If customHeaders is null this does nothing
        val headers = apiRequest.headers
        if (headers != null) {
            for ((key, value) in headers.entries) {
                builder.header(key, value)
            }
        }
    }

    fun getHttpClient(): OkHttpClient = httpClient

    fun getRateLimiter(): RestRateLimiter = rateLimiter

    fun setRetryOnTimeout(retryOnTimeout: Boolean) {
        this.retryOnTimeout = retryOnTimeout
    }

    fun stop(
        shutdown: Boolean,
        callback: Runnable,
    ) {
        rateLimiter.stop(shutdown, callback)
    }

    private fun parseRetry(response: okhttp3.Response): Long {
        val retryAfter = response.header(RestRateLimiter.RETRY_AFTER_HEADER, "0")
        return (retryAfter!!.toDouble() * MILLIS_PER_SECOND).toLong()
    }

    private inner class WorkTask(
        val request: Request<*>,
    ) : RestRateLimiter.Work {
        private var done = false

        @Nonnull
        override fun getRoute(): Route.CompiledRoute = request.route

        @Nonnull
        override fun getJDA(): JDA = request.jda

        @Nullable
        override fun execute(): okhttp3.Response? = this@Requester.execute(this)

        override fun isSkipped(): Boolean = request.isSkipped

        override fun isDone(): Boolean = isSkipped() || done

        override fun isPriority(): Boolean = request.isPriority

        override fun isCancelled(): Boolean = request.isCancelled

        override fun cancel() {
            request.cancel()
        }

        fun handleResponse(
            response: okhttp3.Response,
            rays: Set<String>,
        ) {
            done = true
            request.handleResponse(Response(response, -1, rays))
        }

        fun handleResponse(
            error: Exception,
            rays: Set<String>,
        ) {
            done = true
            request.handleResponse(Response(error, rays))
        }

        fun handleResponse(
            response: okhttp3.Response,
            retryAfter: Long,
            cfRays: Set<String>,
        ) {
            done = true
            request.handleResponse(Response(response, retryAfter, cfRays))
        }
    }

    companion object {
        private val RETRY_ERROR_CODES =
            intArrayOf(
                502, // bad gateway
                503, // service temporarily unavailable
                504, // gateway timeout
                520, // web server returns an unknown error
                521, // web server is down
                522, // connection timed out
                523, // origin is unreachable
                524, // a timeout occurred
                529, // The service is overloaded
            )

        private const val RESPONSE_BUFFER_SIZE = 4
        private const val TOO_MANY_REQUESTS = 429
        private const val RETRY_BASE_DELAY_MS = 500L
        private const val MILLIS_PER_SECOND = 1000L

        @JvmField
        val LOG: Logger = JDALogger.getLog(Requester::class.java)

        @JvmField
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)

        @JvmField
        val MEDIA_TYPE_JSON: MediaType = "application/json; charset=utf-8".toMediaType()

        @JvmField
        val MEDIA_TYPE_OCTET: MediaType = "application/octet-stream; charset=utf-8".toMediaType()

        @JvmField
        val MEDIA_TYPE_PNG: MediaType = "image/png".toMediaType()

        @JvmField
        val MEDIA_TYPE_GIF: MediaType = "image/gif".toMediaType()

        private fun isRetry(e: Throwable): Boolean =
            e is SocketException ||
                // Socket couldn't be created or access failed
                e is SocketTimeoutException ||
                // Connection timed out
                e is SSLPeerUnverifiedException // SSL Certificate was wrong

        @Suppress("ReturnCount") // mirrors the Java predicate
        private fun shouldRetry(code: Int): Boolean {
            if (code < RETRY_ERROR_CODES[0] || code > RETRY_ERROR_CODES[RETRY_ERROR_CODES.size - 1]) {
                return false
            }
            for (retryCode in RETRY_ERROR_CODES) {
                if (retryCode == code) {
                    return true
                }
            }
            return false
        }

        private fun getContentType(response: okhttp3.Response): String {
            val type = response.header("content-type")
            return type?.lowercase(Locale.ROOT) ?: ""
        }
    }
}
