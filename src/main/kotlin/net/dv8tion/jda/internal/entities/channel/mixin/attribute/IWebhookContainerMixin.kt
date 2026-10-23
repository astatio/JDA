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

package net.dv8tion.jda.internal.entities.channel.mixin.attribute

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer
import net.dv8tion.jda.api.entities.channel.unions.IWebhookContainerUnion
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.WebhookAction
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.GuildChannelMixin
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookActionImpl
import net.dv8tion.jda.internal.utils.Checks
import java.io.UncheckedIOException
import javax.annotation.Nonnull

interface IWebhookContainerMixin<T : IWebhookContainerMixin<T>> :
    IWebhookContainer,
    IWebhookContainerUnion,
    GuildChannelMixin<T> {
    // ---- Default implementations of interface ----
    @Nonnull
    @Suppress("TooGenericExceptionCaught") // the Java original catches UncheckedIOException | NullPointerException here
    override fun retrieveWebhooks(): RestAction<List<Webhook>> {
        checkAttached()
        checkPermission(Permission.MANAGE_WEBHOOKS)

        val route = Route.Channels.GET_WEBHOOKS.compile(id)
        val jda = jda as JDAImpl
        return RestActionImpl(jda, route) { response, _ ->
            val array = response.array
            val webhooks = ArrayList<Webhook>(array.length())
            val builder = jda.entityBuilder

            for (i in 0 until array.length()) {
                try {
                    webhooks.add(builder.createWebhook(array.getObject(i)))
                } catch (e: UncheckedIOException) {
                    JDAImpl.LOG.error("Error while creating websocket from json", e)
                } catch (e: NullPointerException) {
                    JDAImpl.LOG.error("Error while creating websocket from json", e)
                }
            }

            java.util.Collections.unmodifiableList(webhooks)
        }
    }

    @Nonnull
    override fun createWebhook(
        @Nonnull name: String,
    ): WebhookAction {
        Checks.notBlank(name, "Webhook name")
        val trimmed = name.trim()
        Checks.notEmpty(trimmed, "Name")
        Checks.notLonger(trimmed, Channel.MAX_NAME_LENGTH, "Name")

        checkAttached()
        checkPermission(Permission.MANAGE_WEBHOOKS)

        return WebhookActionImpl(jda, this, trimmed)
    }

    @Nonnull
    override fun deleteWebhookById(
        @Nonnull id: String,
    ): AuditableRestAction<Void> {
        Checks.isSnowflake(id, "Webhook ID")

        checkAttached()
        checkPermission(Permission.MANAGE_WEBHOOKS)

        val route = Route.Webhooks.DELETE_WEBHOOK.compile(id)
        return AuditableRestActionImpl(jda, route)
    }
}
