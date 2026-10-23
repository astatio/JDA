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

import net.dv8tion.jda.api.entities.Message.MessageFlag.EPHEMERAL
import net.dv8tion.jda.api.requests.restaction.interactions.InteractionCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.internal.interactions.InteractionHookImpl
import net.dv8tion.jda.internal.utils.message.MessageCreateBuilderMixin
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull

open class ReplyCallbackActionImpl(
    hook: InteractionHookImpl,
) : DeferrableCallbackActionImpl(hook),
    ReplyCallbackAction,
    MessageCreateBuilderMixin<ReplyCallbackAction> {
    private val builder = MessageCreateBuilder()
    private var flags = 0

    override fun getBuilder(): MessageCreateBuilder = builder

    @Nonnull
    override fun closeResources(): ReplyCallbackActionImpl {
        builder.closeFiles()
        return this
    }

    @Nonnull
    protected override fun finalizeData(): RequestBody {
        val json = DataObject.empty()
        if (builder.isEmpty) {
            json.put("type", InteractionCallbackAction.ResponseType.DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE.raw)
            if (flags != 0) {
                json.put("data", DataObject.empty().put("flags", flags))
            }
            return getRequestBody(json)
        }

        json.put("type", InteractionCallbackAction.ResponseType.CHANNEL_MESSAGE_WITH_SOURCE.raw)
        builder.build().use { data ->
            val msg = data.toData()
            msg.put("flags", msg.getInt("flags", 0) or flags)
            json.put("data", msg)
            return getMultipartBody(data.allDistinctFiles, json)
        }
    }

    @Nonnull
    override fun setEphemeral(ephemeral: Boolean): ReplyCallbackActionImpl {
        val flag = EPHEMERAL.value
        if (ephemeral) {
            this.flags = this.flags or flag
        } else {
            this.flags = this.flags and flag.inv()
        }
        return this
    }

    @Nonnull
    override fun setCheck(checks: BooleanSupplier?): ReplyCallbackAction =
        super<DeferrableCallbackActionImpl>.setCheck(checks) as ReplyCallbackAction

    @Nonnull
    override fun timeout(
        timeout: Long,
        unit: TimeUnit,
    ): ReplyCallbackAction = super<DeferrableCallbackActionImpl>.timeout(timeout, unit) as ReplyCallbackAction

    @Nonnull
    override fun deadline(timestamp: Long): ReplyCallbackAction =
        super<DeferrableCallbackActionImpl>.deadline(timestamp) as ReplyCallbackAction
}
