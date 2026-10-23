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

import net.dv8tion.jda.api.hooks.IEventManager
import net.dv8tion.jda.internal.utils.Checks
import java.util.function.IntFunction

class EventConfig(
    private val eventManagerProvider: IntFunction<out IEventManager>?,
) {
    private val listeners: MutableList<Any> = ArrayList()
    private val listenerProviders: MutableList<IntFunction<Any>> = ArrayList()

    fun addEventListener(listener: Any) {
        Checks.notNull(listener, "Listener")
        listeners.add(listener)
    }

    fun removeEventListener(listener: Any) {
        Checks.notNull(listener, "Listener")
        listeners.remove(listener)
    }

    fun addEventListenerProvider(provider: IntFunction<Any>) {
        Checks.notNull(provider, "Provider")
        listenerProviders.add(provider)
    }

    fun removeEventListenerProvider(provider: IntFunction<Any>) {
        Checks.notNull(provider, "Provider")
        listenerProviders.remove(provider)
    }

    fun getListeners(): List<Any> = listeners

    fun getListenerProviders(): List<IntFunction<Any>> = listenerProviders

    fun getEventManagerProvider(): IntFunction<out IEventManager>? = eventManagerProvider

    companion object {
        @JvmStatic
        fun getDefault(): EventConfig = EventConfig(null)
    }
}
