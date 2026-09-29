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

package net.dv8tion.jda.internal.handle

import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl

abstract class SocketHandler(
    @JvmField protected val api: JDAImpl,
) {
    @JvmField
    protected var responseNumber: Long = 0

    // The Java field is assigned and cleared by handle(), and only ever read
    // while handleInternally() is running. Exposing it as a non-null property
    // preserves those reads (and the reference being released afterwards)
    // without forcing a `!!` onto every handler call site.
    private var currentContent: DataObject? = null
    protected val allContent: DataObject
        get() = currentContent!!

    @Synchronized
    fun handle(
        responseTotal: Long,
        o: DataObject,
    ) {
        currentContent = o
        this.responseNumber = responseTotal
        if (getJDA().isEventPassthrough()) {
            CURRENT_EVENT.set(o)
        }
        val guildId = handleInternally(o.getObject("d"))
        if (guildId != null) {
            getJDA().getGuildSetupController().cacheEvent(guildId, o)
        }
        currentContent = null
        if (getJDA().isEventPassthrough()) {
            CURRENT_EVENT.set(null)
        }
    }

    protected open fun getJDA(): JDAImpl = api

    /**
     * Handles a given data-json of the Event handled by this Handler.
     *
     * @param content
     *         the content of the event to handle
     *
     * @return
     *         Guild-id if that guild has a lock, or null if successful
     */
    protected abstract fun handleInternally(content: DataObject): Long?

    class NOPHandler(
        api: JDAImpl,
    ) : SocketHandler(api) {
        override fun handleInternally(content: DataObject): Long? = null
    }

    companion object {
        @JvmField
        val CURRENT_EVENT: ThreadLocal<DataObject> = ThreadLocal()
    }
}
