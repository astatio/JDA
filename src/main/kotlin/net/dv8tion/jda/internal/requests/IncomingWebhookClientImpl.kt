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

package net.dv8tion.jda.internal.requests

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.IncomingWebhookClient
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.WebhookMessageDeleteAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageRetrieveAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.AbstractWebhookClient
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageCreateActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageDeleteActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageEditActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageRetrieveActionImpl
import net.dv8tion.jda.internal.utils.Checks
import java.util.function.Function
import javax.annotation.Nonnull

class IncomingWebhookClientImpl(
    webhookId: Long,
    webhookToken: String,
    api: JDA,
) : AbstractWebhookClient<Message>(webhookId, webhookToken, api),
    IncomingWebhookClient {
    override fun sendRequest(): WebhookMessageCreateActionImpl<Message> {
        var route = Route.Webhooks.EXECUTE_WEBHOOK.compile(java.lang.Long.toUnsignedString(id), token)
        route = route.withQueryParams("wait", "true")
        route = route.withQueryParams("with_components", "true")
        val action = WebhookMessageCreateActionImpl(api, route, builder())
        action.run()
        action.setInteraction(false)
        return action
    }

    override fun editRequest(
        @Nonnull messageId: String,
    ): WebhookMessageEditActionImpl<Message> {
        if ("@original" != messageId) {
            Checks.isSnowflake(messageId)
        }
        var route = Route.Webhooks.EXECUTE_WEBHOOK_EDIT.compile(java.lang.Long.toUnsignedString(id), token, messageId)
        route = route.withQueryParams("wait", "true")
        route = route.withQueryParams("with_components", "true")
        val action = WebhookMessageEditActionImpl(api, route, builder())
        action.run()
        return action
    }

    @Nonnull
    override fun retrieveMessageById(
        @Nonnull messageId: String,
    ): WebhookMessageRetrieveAction {
        if ("@original" != messageId) {
            Checks.isSnowflake(messageId)
        }
        val route =
            Route.Webhooks.EXECUTE_WEBHOOK_FETCH.compile(
                java.lang.Long.toUnsignedString(id),
                token,
                messageId,
            )
        val action = WebhookMessageRetrieveActionImpl(api, route) { response, _ -> builder().apply(response.`object`) }
        action.run()
        return action
    }

    @Nonnull
    override fun deleteMessageById(
        @Nonnull messageId: String,
    ): WebhookMessageDeleteAction {
        val action = super<AbstractWebhookClient>.deleteMessageById(messageId) as WebhookMessageDeleteActionImpl
        action.run()
        return action
    }

    private fun builder(): Function<DataObject, Message> =
        Function { data ->
            val jda = api as JDAImpl
            val channelId = data.getUnsignedLong("channel_id")
            val channel = api.getChannelById(MessageChannel::class.java, channelId)
            val entityBuilder: EntityBuilder = jda.entityBuilder
            val message = entityBuilder.createMessageBestEffort(data, channel, null)
            message.withHook(this)
            message
        }
}
