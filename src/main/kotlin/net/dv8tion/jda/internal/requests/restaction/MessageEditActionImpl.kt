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
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.WebhookClient
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.message.MessageEditBuilderMixin
import okhttp3.RequestBody
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull

class MessageEditActionImpl :
    RestActionImpl<Message>,
    MessageEditAction,
    MessageEditBuilderMixin<MessageEditAction> {
    private val messageId: String
    private val guild: Guild?
    private val channel: MessageChannel?
    private val builder = MessageEditBuilder()
    private var webhook: WebhookClient<Message>? = null
    private var threadId: String? = null

    constructor(
        @Nonnull jda: JDA,
        guild: Guild?,
        @Nonnull channelId: String,
        @Nonnull messageId: String,
    ) :
        super(jda, Route.Messages.EDIT_MESSAGE.compile(channelId, messageId)) {
        this.channel = null
        this.guild = guild
        this.messageId = messageId
    }

    constructor(
        @Nonnull channel: MessageChannel,
        @Nonnull messageId: String,
    ) :
        super(channel.jda, Route.Messages.EDIT_MESSAGE.compile(channel.id, messageId)) {
        this.channel = channel
        this.guild = if (channel is GuildChannel) channel.guild else null
        this.messageId = messageId
    }

    fun withHook(
        hook: WebhookClient<Message>,
        channelType: ChannelType,
        channelId: Long,
    ): MessageEditActionImpl {
        this.webhook = hook
        if (hook !is InteractionHook && channelType.isThread) {
            this.threadId = java.lang.Long.toUnsignedString(channelId)
        }
        return this
    }

    override fun getBuilder(): MessageEditBuilder = builder

    override fun finalizeRoute(): Route.CompiledRoute {
        if (webhook != null && (webhook !is InteractionHook || !(webhook as InteractionHook).isExpired)) {
            var route =
                Route.Webhooks.EXECUTE_WEBHOOK_EDIT.compile(webhook!!.id, webhook!!.token, messageId)
            if (this.threadId != null) {
                route = route.withQueryParams("thread_id", threadId)
            }

            return route
        }

        return super.finalizeRoute()
    }

    override fun finalizeData(): RequestBody? = builder.build().use { data -> getMultipartBody(data.allDistinctFiles, data.toData()) }

    override fun handleSuccess(
        response: Response,
        request: Request<Message>,
    ) {
        val entityBuilder: EntityBuilder = api.entityBuilder
        val json = response.getObject()
        val message = entityBuilder.createMessageBestEffort(json, channel, guild)
        request.onSuccess(message.withHook(webhook!!))
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): MessageEditAction = super.setCheck(checks) as MessageEditAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): MessageEditAction = super<RestActionImpl>.deadline(timestamp) as MessageEditAction
}
