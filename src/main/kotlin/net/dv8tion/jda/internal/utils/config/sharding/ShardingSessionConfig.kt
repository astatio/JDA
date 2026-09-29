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

import com.neovisionaries.ws.client.WebSocketFactory
import net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor
import net.dv8tion.jda.api.utils.SessionController
import net.dv8tion.jda.internal.utils.IOUtil
import net.dv8tion.jda.internal.utils.config.SessionConfig
import net.dv8tion.jda.internal.utils.config.flags.ConfigFlag
import net.dv8tion.jda.internal.utils.config.flags.ShardingConfigFlag
import okhttp3.OkHttpClient
import java.util.EnumSet

private const val DEFAULT_MAX_RECONNECT_DELAY = 900
private const val DEFAULT_LARGE_THRESHOLD = 250

class ShardingSessionConfig(
    sessionController: SessionController?,
    interceptor: VoiceDispatchInterceptor?,
    httpClient: OkHttpClient?,
    httpClientBuilder: OkHttpClient.Builder?,
    webSocketFactory: WebSocketFactory?,
    flags: EnumSet<ConfigFlag>,
    private val shardingFlags: EnumSet<ShardingConfigFlag>,
    maxReconnectDelay: Int,
    largeThreshold: Int,
) : SessionConfig(
        sessionController,
        httpClient,
        webSocketFactory,
        interceptor,
        flags,
        maxReconnectDelay,
        largeThreshold,
    ) {
    private val builder: OkHttpClient.Builder?

    init {
        this.builder = if (httpClient == null) httpClientBuilder ?: IOUtil.newHttpClientBuilder() else null
    }

    fun toSessionConfig(client: OkHttpClient): SessionConfig =
        SessionConfig(
            getSessionController(),
            client,
            getWebSocketFactory(),
            getVoiceDispatchInterceptor(),
            getFlags(),
            getMaxReconnectDelay(),
            getLargeThreshold(),
        )

    fun getShardingFlags(): EnumSet<ShardingConfigFlag> = shardingFlags

    fun getHttpBuilder(): OkHttpClient.Builder? = builder

    companion object {
        @JvmStatic
        fun getDefault(): ShardingSessionConfig =
            ShardingSessionConfig(
                null,
                null,
                OkHttpClient(),
                null,
                null,
                ConfigFlag.getDefault(),
                ShardingConfigFlag.getDefault(),
                DEFAULT_MAX_RECONNECT_DELAY,
                DEFAULT_LARGE_THRESHOLD,
            )
    }
}
