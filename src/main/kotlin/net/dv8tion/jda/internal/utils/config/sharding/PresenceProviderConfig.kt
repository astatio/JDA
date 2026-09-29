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

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import java.util.function.IntFunction

class PresenceProviderConfig {
    private var activityProvider: IntFunction<out Activity>? = null
    private var statusProvider: IntFunction<OnlineStatus>? = null
    private var idleProvider: IntFunction<Boolean>? = null

    fun getActivityProvider(): IntFunction<out Activity>? = activityProvider

    fun setActivityProvider(activityProvider: IntFunction<out Activity>?) {
        this.activityProvider = activityProvider
    }

    fun getStatusProvider(): IntFunction<OnlineStatus>? = statusProvider

    fun setStatusProvider(statusProvider: IntFunction<OnlineStatus>?) {
        this.statusProvider = statusProvider
    }

    fun getIdleProvider(): IntFunction<Boolean>? = idleProvider

    fun setIdleProvider(idleProvider: IntFunction<Boolean>?) {
        this.idleProvider = idleProvider
    }

    companion object {
        @JvmStatic
        fun getDefault(): PresenceProviderConfig = PresenceProviderConfig()
    }
}
