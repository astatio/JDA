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

import net.dv8tion.jda.api.GatewayEncoding
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.internal.utils.config.MetaConfig
import net.dv8tion.jda.internal.utils.config.flags.ConfigFlag
import java.util.EnumSet
import java.util.concurrent.ConcurrentMap
import java.util.function.IntFunction

class ShardingMetaConfig(
    maxBufferSize: Int,
    private val contextProvider: IntFunction<out ConcurrentMap<String, String>>?,
    cacheFlags: EnumSet<CacheFlag>?,
    flags: EnumSet<ConfigFlag>,
    private val compression: Compression,
    private val encoding: GatewayEncoding,
) : MetaConfig(maxBufferSize, null, cacheFlags, flags) {
    fun getContextMap(shardId: Int): ConcurrentMap<String, String>? = contextProvider?.apply(shardId)

    fun getCompression(): Compression = compression

    fun getEncoding(): GatewayEncoding = encoding

    fun getContextProvider(): IntFunction<out ConcurrentMap<String, String>>? = contextProvider

    companion object {
        private val defaultConfig =
            ShardingMetaConfig(2048, null, null, ConfigFlag.getDefault(), Compression.ZLIB, GatewayEncoding.JSON)

        @JvmStatic
        fun getDefault(): ShardingMetaConfig = defaultConfig
    }
}
