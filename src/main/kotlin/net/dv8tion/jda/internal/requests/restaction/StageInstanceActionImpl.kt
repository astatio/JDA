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

package net.dv8tion.jda.internal.requests.restaction

import net.dv8tion.jda.api.entities.StageInstance
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.StageInstanceAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull

private const val MAX_TOPIC_LENGTH = 120

class StageInstanceActionImpl(
    private val channel: StageChannel,
) : RestActionImpl<StageInstance>(channel.jda, Route.StageInstances.CREATE_INSTANCE.compile()),
    StageInstanceAction {
    private var topic: String? = null

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): StageInstanceAction = super.setCheck(checks) as StageInstanceAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): StageInstanceAction = super.timeout(timeout, unit) as StageInstanceAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): StageInstanceAction = super.deadline(timestamp) as StageInstanceAction

    @Nonnull
    override fun setTopic(
        @Nonnull topic: String,
    ): StageInstanceAction {
        Checks.notBlank(topic, "Topic")
        Checks.notLonger(topic, MAX_TOPIC_LENGTH, "Topic")
        this.topic = topic
        return this
    }

    override fun finalizeData(): RequestBody? {
        val body = DataObject.empty()
        body.put("channel_id", channel.id)
        body.put("topic", topic)
        return getRequestBody(body)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<StageInstance>,
    ) {
        val instance = api.entityBuilder.createStageInstance(channel.guild as GuildImpl, response.getObject())
        request.onSuccess(instance)
    }
}
