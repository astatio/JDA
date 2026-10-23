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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.ClientType
import java.util.Collections
import java.util.EnumMap

class MemberPresenceImpl {
    private var activities: List<Activity> = Collections.emptyList()
    private var clientStatus: EnumMap<ClientType, OnlineStatus>? = null
    private var status: OnlineStatus = OnlineStatus.OFFLINE

    fun setActivities(activities: List<Activity>) {
        this.activities = activities
    }

    fun setClientStatus(clientStatus: EnumMap<ClientType, OnlineStatus>) {
        this.clientStatus = clientStatus
    }

    fun setOnlineStatus(status: OnlineStatus) {
        this.status = status
    }

    fun getActivities(): List<Activity> = activities

    fun getClientStatus(): EnumMap<ClientType, OnlineStatus> = clientStatus ?: EnumMap(ClientType::class.java)

    fun getOnlineStatus(): OnlineStatus = status

    fun setOnlineStatus(
        type: ClientType,
        clientStatus: OnlineStatus?,
    ) {
        if (this.clientStatus == null) {
            if (clientStatus == null || clientStatus == OnlineStatus.OFFLINE) {
                return
            }
            this.clientStatus = EnumMap(ClientType::class.java)
        }
        if (clientStatus == OnlineStatus.OFFLINE) {
            this.clientStatus!!.remove(type)
        } else {
            this.clientStatus!![type] = clientStatus!!
        }
    }
}
