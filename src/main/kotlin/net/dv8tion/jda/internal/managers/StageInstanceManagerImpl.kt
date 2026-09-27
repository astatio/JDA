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

import net.dv8tion.jda.api.entities.StageInstance
import net.dv8tion.jda.api.managers.StageInstanceManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val TOPIC_MAX_LENGTH = 120

class StageInstanceManagerImpl(
    private val instance: StageInstance,
) : ManagerBase<StageInstanceManager>(
        instance.getChannel().getJDA(),
        Route.StageInstances.UPDATE_INSTANCE.compile(instance.getChannel().getId()),
    ),
    StageInstanceManager {
    private var topic: String? = null

    @Nonnull
    override fun getStageInstance(): StageInstance = instance

    @Nonnull
    override fun setTopic(
        @Nullable topic: String?,
    ): StageInstanceManager {
        var value = topic
        if (value != null) {
            value = value.trim()
            Checks.notLonger(value, TOPIC_MAX_LENGTH, "Topic")
            if (value.isEmpty()) {
                value = null
            }
        }
        this.topic = value
        set = set or StageInstanceManager.TOPIC
        return this
    }

    override fun finalizeData(): RequestBody {
        val body = DataObject.empty()
        if (shouldUpdate(StageInstanceManager.TOPIC) && topic != null) {
            body.put("topic", topic)
        }
        return getRequestBody(body)
    }
}
