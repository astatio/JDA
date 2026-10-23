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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.internal.utils.message.MessageEditBuilderMixin
import okhttp3.RequestBody
import java.util.function.Function
import javax.annotation.Nonnull

class WebhookMessageEditActionImpl<T>(
    api: JDA,
    route: Route.CompiledRoute,
    private val transformer: Function<DataObject, T>,
) : AbstractWebhookMessageActionImpl<T, WebhookMessageEditActionImpl<T>>(api, route),
    WebhookMessageEditAction<T>,
    MessageEditBuilderMixin<WebhookMessageEditAction<T>> {
    @Nonnull
    override fun deadline(timestamp: Long): WebhookMessageEditActionImpl<T> = super<AbstractWebhookMessageActionImpl>.deadline(timestamp)

    private val builder = MessageEditBuilder()

    override fun getBuilder(): MessageEditBuilder = builder

    override fun finalizeData(): RequestBody? =
        builder.build().use { data ->
            val payload = data.toData()
            getMultipartBody(data.allDistinctFiles, payload)
        }

    override fun finalizeRoute(): Route.CompiledRoute {
        var route = super.finalizeRoute()
        if (threadId != null) {
            route = route.withQueryParams("thread_id", threadId)
        }
        return route
    }

    override fun handleSuccess(
        response: Response,
        request: Request<T>,
    ) {
        val message = transformer.apply(response.getObject())
        request.onSuccess(message)
    }
}
