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

package net.dv8tion.jda.internal.utils.config

import net.dv8tion.jda.internal.utils.concurrent.CountingThreadFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class ThreadingConfig {
    private val audioLock = Any()

    private var rateLimitScheduler: ScheduledExecutorService? = null
    private var rateLimitElastic: ExecutorService? = null
    private var gatewayPool: ScheduledExecutorService? = null
    private var callbackPool: ExecutorService? = ForkJoinPool.commonPool()
    private var eventPool: ExecutorService? = null
    private var audioPool: ScheduledExecutorService? = null

    private var shutdownRateLimitScheduler = true
    private var shutdownRateLimitElastic = true
    private var shutdownGatewayPool = true
    private var shutdownCallbackPool = false
    private var shutdownEventPool = false
    private var shutdownAudioPool = true

    fun setRateLimitScheduler(
        executor: ScheduledExecutorService?,
        shutdown: Boolean,
    ) {
        this.rateLimitScheduler = executor
        this.shutdownRateLimitScheduler = shutdown
    }

    fun setRateLimitElastic(
        executor: ExecutorService?,
        shutdown: Boolean,
    ) {
        this.rateLimitElastic = executor
        this.shutdownRateLimitElastic = shutdown
    }

    fun setGatewayPool(
        executor: ScheduledExecutorService?,
        shutdown: Boolean,
    ) {
        this.gatewayPool = executor
        this.shutdownGatewayPool = shutdown
    }

    fun setCallbackPool(
        executor: ExecutorService?,
        shutdown: Boolean,
    ) {
        this.callbackPool = executor ?: ForkJoinPool.commonPool()
        this.shutdownCallbackPool = shutdown
    }

    fun setEventPool(
        executor: ExecutorService?,
        shutdown: Boolean,
    ) {
        this.eventPool = executor
        this.shutdownEventPool = shutdown
    }

    fun setAudioPool(
        executor: ScheduledExecutorService?,
        shutdown: Boolean,
    ) {
        this.audioPool = executor
        this.shutdownAudioPool = shutdown
    }

    fun init(identifier: Supplier<String>) {
        if (this.rateLimitScheduler == null) {
            this.rateLimitScheduler = newScheduler(2, identifier, "RateLimit-Scheduler", false)
        }
        if (this.gatewayPool == null) {
            this.gatewayPool = newScheduler(1, identifier, "Gateway")
        }
        if (this.rateLimitElastic == null) {
            val elastic: ExecutorService =
                Executors.newCachedThreadPool(
                    CountingThreadFactory(identifier, "RateLimit-Elastic", false),
                )
            this.rateLimitElastic = elastic
            if (elastic is ThreadPoolExecutor) {
                elastic.corePoolSize = 1
                elastic.setKeepAliveTime(2, TimeUnit.MINUTES)
            }
        }
    }

    fun shutdown() {
        if (shutdownCallbackPool) {
            callbackPool!!.shutdown()
        }
        if (shutdownGatewayPool) {
            gatewayPool!!.shutdown()
        }
        if (shutdownEventPool && eventPool != null) {
            eventPool!!.shutdown()
        }
        if (shutdownAudioPool && audioPool != null) {
            audioPool!!.shutdown()
        }
    }

    fun shutdownRequester() {
        if (shutdownRateLimitScheduler) {
            rateLimitScheduler!!.shutdown()
        }
        if (shutdownRateLimitElastic) {
            rateLimitElastic!!.shutdown()
        }
    }

    fun shutdownNow() {
        if (shutdownCallbackPool) {
            callbackPool!!.shutdownNow()
        }
        if (shutdownGatewayPool) {
            gatewayPool!!.shutdownNow()
        }
        if (shutdownRateLimitScheduler) {
            rateLimitScheduler!!.shutdownNow()
        }
        if (shutdownRateLimitElastic) {
            rateLimitElastic!!.shutdownNow()
        }
        if (shutdownEventPool && eventPool != null) {
            eventPool!!.shutdownNow()
        }
        if (shutdownAudioPool && audioPool != null) {
            audioPool!!.shutdownNow()
        }
    }

    fun getRateLimitScheduler(): ScheduledExecutorService = rateLimitScheduler!!

    fun getRateLimitElastic(): ExecutorService = rateLimitElastic!!

    fun getGatewayPool(): ScheduledExecutorService = gatewayPool!!

    fun getCallbackPool(): ExecutorService = callbackPool!!

    fun getEventPool(): ExecutorService? = eventPool

    fun getAudioPool(identifier: Supplier<String>): ScheduledExecutorService {
        var pool = audioPool
        if (pool == null) {
            synchronized(audioLock) {
                pool = audioPool
                if (pool == null) {
                    pool = newScheduler(1, identifier, "AudioLifeCycle")
                    audioPool = pool
                }
            }
        }
        return pool!!
    }

    fun isShutdownRateLimitScheduler(): Boolean = shutdownRateLimitScheduler

    fun isShutdownRateLimitElastic(): Boolean = shutdownRateLimitElastic

    fun isShutdownGatewayPool(): Boolean = shutdownGatewayPool

    fun isShutdownCallbackPool(): Boolean = shutdownCallbackPool

    fun isShutdownEventPool(): Boolean = shutdownEventPool

    fun isShutdownAudioPool(): Boolean = shutdownAudioPool

    companion object {
        @JvmStatic
        fun newScheduler(
            coreSize: Int,
            identifier: Supplier<String>,
            baseName: String,
        ): ScheduledThreadPoolExecutor = newScheduler(coreSize, identifier, baseName, true)

        @JvmStatic
        fun newScheduler(
            coreSize: Int,
            identifier: Supplier<String>,
            baseName: String,
            daemon: Boolean,
        ): ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(coreSize, CountingThreadFactory(identifier, baseName, daemon))

        @JvmStatic
        fun getDefault(): ThreadingConfig = ThreadingConfig()
    }
}
