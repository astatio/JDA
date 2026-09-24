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

package net.dv8tion.jda.internal.utils.config.sharding

import net.dv8tion.jda.api.sharding.ThreadPoolProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory

class ThreadingProviderConfig(
    private val rateLimitSchedulerProvider: ThreadPoolProvider<out ScheduledExecutorService>?,
    private val rateLimitElasticProvider: ThreadPoolProvider<out ExecutorService>?,
    private val gatewayPoolProvider: ThreadPoolProvider<out ScheduledExecutorService>?,
    private val callbackPoolProvider: ThreadPoolProvider<out ExecutorService>?,
    private val eventPoolProvider: ThreadPoolProvider<out ExecutorService>?,
    private val audioPoolProvider: ThreadPoolProvider<out ScheduledExecutorService>?,
    private val threadFactory: ThreadFactory?,
) {
    fun getThreadFactory(): ThreadFactory? = threadFactory

    private fun init(
        provider: ThreadPoolProvider<*>?,
        shardTotal: Int,
    ) {
        if (provider is ThreadPoolProvider.LazySharedProvider<*>) {
            provider.init(shardTotal)
        }
    }

    private fun shutdown(provider: ThreadPoolProvider<*>?) {
        if (provider is ThreadPoolProvider.LazySharedProvider<*>) {
            provider.shutdown()
        }
    }

    fun init(shardTotal: Int) {
        init(rateLimitSchedulerProvider, shardTotal)
        init(rateLimitElasticProvider, shardTotal)
        init(gatewayPoolProvider, shardTotal)
        init(callbackPoolProvider, shardTotal)
        init(eventPoolProvider, shardTotal)
        init(audioPoolProvider, shardTotal)
    }

    fun shutdown() {
        shutdown(rateLimitSchedulerProvider)
        shutdown(rateLimitElasticProvider)
        shutdown(gatewayPoolProvider)
        shutdown(callbackPoolProvider)
        shutdown(eventPoolProvider)
        shutdown(audioPoolProvider)
    }

    fun getRateLimitSchedulerProvider(): ThreadPoolProvider<out ScheduledExecutorService>? = rateLimitSchedulerProvider

    fun getRateLimitElasticProvider(): ThreadPoolProvider<out ExecutorService>? = rateLimitElasticProvider

    fun getGatewayPoolProvider(): ThreadPoolProvider<out ScheduledExecutorService>? = gatewayPoolProvider

    fun getCallbackPoolProvider(): ThreadPoolProvider<out ExecutorService>? = callbackPoolProvider

    fun getEventPoolProvider(): ThreadPoolProvider<out ExecutorService>? = eventPoolProvider

    fun getAudioPoolProvider(): ThreadPoolProvider<out ScheduledExecutorService>? = audioPoolProvider

    companion object {
        @JvmStatic
        fun getDefault(): ThreadingProviderConfig = ThreadingProviderConfig(null, null, null, null, null, null, null)
    }
}
