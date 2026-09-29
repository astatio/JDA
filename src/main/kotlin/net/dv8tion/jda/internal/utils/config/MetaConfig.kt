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

import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.internal.utils.config.flags.ConfigFlag
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

open class MetaConfig(
    private val maxBufferSize: Int,
    mdcContextMap: ConcurrentMap<String, String>?,
    cacheFlags: EnumSet<CacheFlag>?,
    flags: EnumSet<ConfigFlag>,
) {
    private val mdcContextMap: ConcurrentMap<String, String>?
    private val cacheFlags: EnumSet<CacheFlag>
    private val enableMDC: Boolean
    private val useShutdownHook: Boolean

    init {
        this.cacheFlags = cacheFlags ?: EnumSet.allOf(CacheFlag::class.java)
        this.enableMDC = flags.contains(ConfigFlag.MDC_CONTEXT)
        this.mdcContextMap = if (enableMDC) mdcContextMap ?: ConcurrentHashMap() else null
        this.useShutdownHook = flags.contains(ConfigFlag.SHUTDOWN_HOOK)
    }

    fun getMdcContextMap(): ConcurrentMap<String, String>? = mdcContextMap

    fun getCacheFlags(): EnumSet<CacheFlag> = cacheFlags

    fun isEnableMDC(): Boolean = enableMDC

    fun isUseShutdownHook(): Boolean = useShutdownHook

    fun getMaxBufferSize(): Int = maxBufferSize

    companion object {
        private val defaultConfig = MetaConfig(2048, null, EnumSet.allOf(CacheFlag::class.java), ConfigFlag.getDefault())

        @JvmStatic
        fun getDefault(): MetaConfig = defaultConfig
    }
}
