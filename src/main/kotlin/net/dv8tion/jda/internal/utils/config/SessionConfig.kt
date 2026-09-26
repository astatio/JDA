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

import com.neovisionaries.ws.client.WebSocketFactory
import net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor
import net.dv8tion.jda.api.utils.ConcurrentSessionController
import net.dv8tion.jda.api.utils.SessionController
import net.dv8tion.jda.internal.utils.config.flags.ConfigFlag
import okhttp3.OkHttpClient
import java.util.EnumSet

private const val CONNECTION_TIMEOUT_MS = 10_000
private const val DEFAULT_MAX_RECONNECT_DELAY = 900
private const val DEFAULT_LARGE_THRESHOLD = 250

open class SessionConfig(
    sessionController: SessionController?,
    private val httpClient: OkHttpClient?,
    webSocketFactory: WebSocketFactory?,
    private val interceptor: VoiceDispatchInterceptor?,
    private val flags: EnumSet<ConfigFlag>,
    private val maxReconnectDelay: Int,
    private val largeThreshold: Int,
) {
    private val sessionController: SessionController = sessionController ?: ConcurrentSessionController()
    private val webSocketFactory: WebSocketFactory = webSocketFactory ?: newWebSocketFactory()

    fun setAutoReconnect(autoReconnect: Boolean) {
        if (autoReconnect) {
            flags.add(ConfigFlag.AUTO_RECONNECT)
        } else {
            flags.remove(ConfigFlag.AUTO_RECONNECT)
        }
    }

    fun getSessionController(): SessionController = sessionController

    fun getHttpClient(): OkHttpClient? = httpClient

    fun getWebSocketFactory(): WebSocketFactory = webSocketFactory

    fun getVoiceDispatchInterceptor(): VoiceDispatchInterceptor? = interceptor

    fun isAutoReconnect(): Boolean = flags.contains(ConfigFlag.AUTO_RECONNECT)

    fun isRetryOnTimeout(): Boolean = flags.contains(ConfigFlag.RETRY_TIMEOUT)

    fun isBulkDeleteSplittingEnabled(): Boolean = flags.contains(ConfigFlag.BULK_DELETE_SPLIT)

    fun isRawEvents(): Boolean = flags.contains(ConfigFlag.RAW_EVENTS)

    fun isEventPassthrough(): Boolean = flags.contains(ConfigFlag.EVENT_PASSTHROUGH)

    fun isRelativeRateLimit(): Boolean = flags.contains(ConfigFlag.USE_RELATIVE_RATELIMIT)

    fun getMaxReconnectDelay(): Int = maxReconnectDelay

    fun getLargeThreshold(): Int = largeThreshold

    fun getFlags(): EnumSet<ConfigFlag> = flags

    companion object {
        private fun newWebSocketFactory(): WebSocketFactory = WebSocketFactory().setConnectionTimeout(CONNECTION_TIMEOUT_MS)

        @JvmStatic
        fun getDefault(): SessionConfig =
            SessionConfig(
                null,
                OkHttpClient(),
                null,
                null,
                ConfigFlag.getDefault(),
                DEFAULT_MAX_RECONNECT_DELAY,
                DEFAULT_LARGE_THRESHOLD,
            )
    }
}
