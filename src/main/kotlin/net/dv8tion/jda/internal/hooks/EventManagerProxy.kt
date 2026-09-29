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

package net.dv8tion.jda.internal.hooks

import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.IEventManager
import net.dv8tion.jda.api.hooks.InterfacedEventManager
import net.dv8tion.jda.internal.JDAImpl
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import javax.annotation.Nonnull

class EventManagerProxy(
    subject: IEventManager,
    private val executor: ExecutorService?,
) : IEventManager {
    private var subject: IEventManager = subject

    fun setSubject(subject: IEventManager?) {
        this.subject = subject ?: InterfacedEventManager()
    }

    fun getSubject(): IEventManager = subject

    override fun register(
        @Nonnull listener: Any,
    ) {
        subject.register(listener)
    }

    override fun unregister(
        @Nonnull listener: Any,
    ) {
        subject.unregister(listener)
    }

    // The Java original caught RejectedExecutionException without logging it (the warning message
    // is the whole report) and caught broad Exception/RuntimeException on purpose, so the event
    // pool can never obstruct the socket handler. Narrowing either would change behavior.
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    override fun handle(
        @Nonnull event: GenericEvent,
    ) {
        try {
            if (executor != null && !executor.isShutdown) {
                executor.execute { handleInternally(event) }
            } else {
                handleInternally(event)
            }
        } catch (ex: RejectedExecutionException) {
            JDAImpl.LOG.warn("Event-Pool rejected event execution! Running on handling thread instead...")
            handleInternally(event)
        } catch (ex: Exception) {
            JDAImpl.LOG.error("Encountered exception trying to schedule event", ex)
        }
    }

    // don't allow mere exceptions to obstruct the socket handler
    @Suppress("TooGenericExceptionCaught")
    private fun handleInternally(
        @Nonnull event: GenericEvent,
    ) {
        try {
            subject.handle(event)
        } catch (e: RuntimeException) {
            JDAImpl.LOG.error("The EventManager.handle() call had an uncaught exception", e)
        }
    }

    @Nonnull
    override fun getRegisteredListeners(): List<Any> = subject.registeredListeners
}
