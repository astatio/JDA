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
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer
import net.dv8tion.jda.api.entities.channel.unions.IWebhookContainerUnion
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.WebhookAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

/**
 * [Webhook] Builder system created as an extension of [net.dv8tion.jda.api.requests.RestAction].
 *
 * Provides an easy way to gather and deliver information to Discord to create [Webhooks][Webhook].
 */
private const val MAX_NAME_LENGTH = 100

open class WebhookActionImpl(
    api: JDA,
    protected val channel: IWebhookContainer,
    name: String,
) : AuditableRestActionImpl<Webhook>(api, Route.Channels.CREATE_WEBHOOK.compile(channel.id)),
    WebhookAction {
    @JvmField
    protected var name: String = name

    @JvmField
    protected var avatar: Icon? = null

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): WebhookActionImpl = super.setCheck(checks) as WebhookActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): WebhookActionImpl = super.timeout(timeout, unit) as WebhookActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): WebhookActionImpl = super.deadline(timestamp) as WebhookActionImpl

    @Nonnull
    override fun getChannel(): IWebhookContainerUnion = channel as IWebhookContainerUnion

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): WebhookActionImpl {
        Checks.notEmpty(name, "Name")
        Checks.notLonger(name, MAX_NAME_LENGTH, "Name")

        this.name = name
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setAvatar(icon: Icon?): WebhookActionImpl {
        this.avatar = icon
        return this
    }

    override fun finalizeData(): RequestBody? {
        val json = DataObject.empty()
        json.put("name", name)
        json.put("avatar", avatar?.encoding)

        return getRequestBody(json)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<Webhook>,
    ) {
        val json = response.getObject()
        val webhook = api.entityBuilder.createWebhook(json)

        request.onSuccess(webhook)
    }
}
