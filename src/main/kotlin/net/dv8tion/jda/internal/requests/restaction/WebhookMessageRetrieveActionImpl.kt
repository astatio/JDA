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
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.WebhookMessageRetrieveAction
import java.util.function.BiFunction
import javax.annotation.Nonnull

class WebhookMessageRetrieveActionImpl(
    api: JDA,
    route: Route.CompiledRoute,
    handler: BiFunction<Response, Request<Message>, Message>,
) : AbstractWebhookMessageActionImpl<Message, WebhookMessageRetrieveActionImpl>(api, route, handler),
    WebhookMessageRetrieveAction {
    @Nonnull
    override fun deadline(timestamp: Long): WebhookMessageRetrieveActionImpl = super<AbstractWebhookMessageActionImpl>.deadline(timestamp)

    override fun finalizeRoute(): Route.CompiledRoute {
        var route = super.finalizeRoute()
        if (threadId != null) {
            route = route.withQueryParams("thread_id", threadId)
        }
        return route
    }
}
