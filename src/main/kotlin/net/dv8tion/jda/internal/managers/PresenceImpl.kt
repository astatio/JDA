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

package net.dv8tion.jda.internal.managers

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.managers.Presence
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.requests.WebSocketCode
import net.dv8tion.jda.internal.utils.Checks
import java.util.Collections
import javax.annotation.Nonnull

/**
 * The Presence associated with the provided JDA instance
 * <br><b>Note that this does not automatically handle the 5/60 second rate limit!</b>
 */
class PresenceImpl(
    private val api: JDAImpl,
) : Presence {
    private var idle = false
    private var activity: Activity? = null
    private var status: OnlineStatus = OnlineStatus.ONLINE

    // -- Public Getters --

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun getStatus(): OnlineStatus = status

    override fun getActivity(): Activity? = activity

    override fun isIdle(): Boolean = idle

    // -- Public Setters --

    override fun setStatus(status: OnlineStatus?) {
        setPresence(status, activity, idle)
    }

    override fun setActivity(game: Activity?) {
        setPresence(status, game)
    }

    override fun setIdle(idle: Boolean) {
        setPresence(status, idle)
    }

    override fun setPresence(
        status: OnlineStatus?,
        activity: Activity?,
        idle: Boolean,
    ) {
        Checks.check(status != OnlineStatus.UNKNOWN, "Cannot set the presence status to an unknown OnlineStatus!")
        val resolved = if (status == OnlineStatus.OFFLINE || status == null) OnlineStatus.INVISIBLE else status

        this.idle = idle
        this.status = resolved
        this.activity = activity
        update()
    }

    override fun setPresence(
        status: OnlineStatus?,
        activity: Activity?,
    ) {
        setPresence(status, activity, idle)
    }

    override fun setPresence(
        status: OnlineStatus?,
        idle: Boolean,
    ) {
        setPresence(status, activity, idle)
    }

    override fun setPresence(
        game: Activity?,
        idle: Boolean,
    ) {
        setPresence(status, game, idle)
    }

    // -- Impl Setters --

    fun setCacheStatus(status: OnlineStatus?): PresenceImpl {
        if (status == null) {
            throw NullPointerException("Null OnlineStatus is not allowed.")
        }
        this.status = if (status == OnlineStatus.OFFLINE) OnlineStatus.INVISIBLE else status
        return this
    }

    fun setCacheActivity(game: Activity?): PresenceImpl {
        this.activity = game
        return this
    }

    fun setCacheIdle(idle: Boolean): PresenceImpl {
        this.idle = idle
        return this
    }

    // -- Internal Methods --

    fun getFullPresence(): DataObject {
        val activity = getGameJson(this.activity)
        return DataObject
            .empty()
            .put("afk", idle)
            .put("since", System.currentTimeMillis())
            .put(
                "activities",
                DataArray.fromCollection(
                    // this is done so that nested DataObject is
                    // converted to a Map
                    if (activity == null) Collections.emptyList() else Collections.singletonList(activity),
                ),
            ).put("status", getStatus().getKey())
    }

    // -- Terminal --

    // Kept protected to match the Java method shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    protected fun update() {
        val data = getFullPresence()
        val status = api.getStatus()
        if (status == JDA.Status.RECONNECT_QUEUED ||
            status == JDA.Status.SHUTDOWN ||
            status == JDA.Status.SHUTTING_DOWN
        ) {
            return
        }
        api.getClient().send(DataObject.empty().put("d", data).put("op", WebSocketCode.PRESENCE))
    }

    companion object {
        @JvmStatic
        @Suppress("SENSELESS_COMPARISON") // the Java original guards against broken Activity implementations
        fun getGameJson(activity: Activity?): DataObject? {
            if (activity == null || activity.getName() == null || activity.getType() == null) {
                return null
            }
            val gameObj = DataObject.empty()

            if (activity.getType() == Activity.ActivityType.CUSTOM_STATUS) {
                gameObj.put("name", "Custom Status")
                gameObj.put("state", activity.getName())
            } else {
                gameObj.put("name", activity.getName())
                val state = activity.getState()
                if (state != null) {
                    gameObj.put("state", state)
                }
            }

            gameObj.put("type", activity.getType().getKey())
            if (activity.getUrl() != null) {
                gameObj.put("url", activity.getUrl())
            }

            return gameObj
        }
    }
}
