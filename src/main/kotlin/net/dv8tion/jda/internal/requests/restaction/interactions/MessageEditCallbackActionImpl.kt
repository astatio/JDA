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

package net.dv8tion.jda.internal.requests.restaction.interactions

import net.dv8tion.jda.api.requests.restaction.interactions.InteractionCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.internal.interactions.InteractionHookImpl
import net.dv8tion.jda.internal.utils.message.MessageEditBuilderMixin
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull

open class MessageEditCallbackActionImpl(
    hook: InteractionHookImpl,
) : DeferrableCallbackActionImpl(hook),
    MessageEditCallbackAction,
    MessageEditBuilderMixin<MessageEditCallbackAction> {
    private val builder = MessageEditBuilder()

    override fun getBuilder(): MessageEditBuilder = builder

    @Nonnull
    override fun setCheck(checks: BooleanSupplier?): MessageEditCallbackActionImpl =
        super<DeferrableCallbackActionImpl>.setCheck(checks) as MessageEditCallbackActionImpl

    @Nonnull
    override fun timeout(
        timeout: Long,
        unit: TimeUnit,
    ): MessageEditCallbackActionImpl = super<DeferrableCallbackActionImpl>.timeout(timeout, unit) as MessageEditCallbackActionImpl

    @Nonnull
    override fun deadline(timestamp: Long): MessageEditCallbackActionImpl =
        super<DeferrableCallbackActionImpl>.deadline(timestamp) as MessageEditCallbackActionImpl

    @Nonnull
    override fun closeResources(): MessageEditCallbackActionImpl {
        builder.closeFiles()
        return this
    }

    private fun isEmpty(): Boolean = builder.isEmpty

    override fun finalizeData(): RequestBody {
        val json = DataObject.empty()
        if (isEmpty()) {
            return getRequestBody(json.put("type", InteractionCallbackAction.ResponseType.DEFERRED_MESSAGE_UPDATE.raw))
        }
        json.put("type", InteractionCallbackAction.ResponseType.MESSAGE_UPDATE.raw)
        builder.build().use { data ->
            json.put("data", data)
            return getMultipartBody(data.allDistinctFiles, json)
        }
    }
}
