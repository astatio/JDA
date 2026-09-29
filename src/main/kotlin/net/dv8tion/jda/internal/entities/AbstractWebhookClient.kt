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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.WebhookClient
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageDeleteAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.utils.AttachedFile
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData
import net.dv8tion.jda.api.utils.messages.MessagePollData
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageCreateActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageDeleteActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageEditActionImpl
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull

abstract class AbstractWebhookClient<T> protected constructor(
    @JvmField protected val id: Long,
    @JvmField protected var token: String?,
    @JvmField protected val api: JDA,
) : WebhookClient<T> {
    override fun getIdLong(): Long = id

    override fun getToken(): String? = token

    @Nonnull
    override fun getJDA(): JDA = api

    abstract fun sendRequest(): WebhookMessageCreateActionImpl<T>

    abstract fun editRequest(messageId: String): WebhookMessageEditActionImpl<T>

    @Nonnull
    override fun sendMessage(content: String): WebhookMessageCreateAction<T> = sendRequest().setContent(content)

    @Nonnull
    override fun sendMessageEmbeds(embeds: Collection<MessageEmbed>): WebhookMessageCreateAction<T> = sendRequest().addEmbeds(embeds)

    @Nonnull
    override fun sendMessageComponents(components: Collection<MessageTopLevelComponent>): WebhookMessageCreateAction<T> =
        sendRequest().setComponents(components)

    @Nonnull
    override fun sendMessage(message: MessageCreateData): WebhookMessageCreateAction<T> = sendRequest().applyData(message)

    @Nonnull
    override fun sendMessagePoll(poll: MessagePollData): WebhookMessageCreateAction<T> {
        Checks.notNull(poll, "Message Poll")
        return sendRequest().setPoll(poll)
    }

    @Nonnull
    override fun sendFiles(files: Collection<FileUpload>): WebhookMessageCreateAction<T> = sendRequest().addFiles(files)

    @Nonnull
    override fun editMessageById(
        messageId: String,
        content: String,
    ): WebhookMessageEditActionImpl<T> = editRequest(messageId).setContent(content) as WebhookMessageEditActionImpl<T>

    @Nonnull
    override fun editMessageComponentsById(
        messageId: String,
        components: Collection<MessageTopLevelComponent>,
    ): WebhookMessageEditAction<T> {
        Checks.noneNull(components, "Components")
        return editRequest(messageId).setComponents(components)
    }

    @Nonnull
    override fun editMessageEmbedsById(
        messageId: String,
        embeds: Collection<MessageEmbed>,
    ): WebhookMessageEditActionImpl<T> = editRequest(messageId).setEmbeds(embeds) as WebhookMessageEditActionImpl<T>

    @Nonnull
    override fun editMessageById(
        messageId: String,
        message: MessageEditData,
    ): WebhookMessageEditActionImpl<T> = editRequest(messageId).applyData(message) as WebhookMessageEditActionImpl<T>

    @Nonnull
    override fun editMessageAttachmentsById(
        messageId: String,
        attachments: Collection<AttachedFile>,
    ): WebhookMessageEditActionImpl<T> = editRequest(messageId).setAttachments(attachments) as WebhookMessageEditActionImpl<T>

    @Nonnull
    override fun deleteMessageById(messageId: String): WebhookMessageDeleteAction {
        if ("@original" != messageId) {
            Checks.isSnowflake(messageId)
        }
        val route = Route.Webhooks.EXECUTE_WEBHOOK_DELETE.compile(java.lang.Long.toUnsignedString(id), token, messageId)
        return WebhookMessageDeleteActionImpl(api, route)
    }
}
