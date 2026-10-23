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
import net.dv8tion.jda.api.entities.Message.MessageFlag
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.ThreadCreateMetadata
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import net.dv8tion.jda.internal.utils.message.MessageCreateBuilderMixin
import okhttp3.RequestBody
import java.util.function.Function
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val MAX_USERNAME_LENGTH = 80

class WebhookMessageCreateActionImpl<T>(
    api: JDA,
    route: Route.CompiledRoute,
    private val transformer: Function<DataObject, T>,
) : AbstractWebhookMessageActionImpl<T, WebhookMessageCreateActionImpl<T>>(api, route),
    WebhookMessageCreateAction<T>,
    MessageCreateBuilderMixin<WebhookMessageCreateAction<T>> {
    @Nonnull
    override fun deadline(timestamp: Long): WebhookMessageCreateActionImpl<T> = super<AbstractWebhookMessageActionImpl>.deadline(timestamp)

    private val builder = MessageCreateBuilder()

    private var isInteraction = true

    // Interactions only
    private var ephemeral = false

    // Incoming webhooks only

    private var username: String? = null
    private var avatar: String? = null
    private var threadMetadata: ThreadCreateMetadata? = null

    fun setInteraction(isInteraction: Boolean): WebhookMessageCreateActionImpl<T> {
        this.isInteraction = isInteraction
        return this
    }

    override fun getBuilder(): MessageCreateBuilder = builder

    @Nonnull
    override fun setEphemeral(ephemeral: Boolean): WebhookMessageCreateActionImpl<T> {
        if (!isInteraction && ephemeral) {
            throw IllegalStateException(
                "Cannot create ephemeral messages with webhooks. Use InteractionHook instead!",
            )
        }

        this.ephemeral = ephemeral
        return this
    }

    @Nonnull
    override fun setUsername(
        @Nullable name: String?,
    ): WebhookMessageCreateAction<T> {
        var name = name
        if (isInteraction && username != null) {
            throw IllegalStateException("Cannot set username on interaction messages.")
        }

        if (name != null) {
            name = name.trim()
            Checks.inRange(name, 1, MAX_USERNAME_LENGTH, "Name")
        }

        this.username = name
        return this
    }

    @Nonnull
    override fun setAvatarUrl(
        @Nullable iconUrl: String?,
    ): WebhookMessageCreateAction<T> {
        if (isInteraction && iconUrl != null) {
            throw IllegalStateException("Cannot set avatar on interaction messages.")
        }

        if (iconUrl != null) {
            Checks.noWhitespace(iconUrl, "Avatar URL")
            Checks.check(
                iconUrl.startsWith("https://") || iconUrl.startsWith("http://"),
                "Invalid URL format. Must start with 'https://' or 'http://'. Provided %s",
                iconUrl,
            )
        }

        this.avatar = iconUrl
        return this
    }

    @Nonnull
    override fun createThread(
        @Nonnull threadMetadata: ThreadCreateMetadata,
    ): WebhookMessageCreateAction<T> {
        if (isInteraction) {
            throw IllegalStateException("Cannot create a thread through an interaction hook.")
        }

        Checks.notNull(threadMetadata, "Thread Metadata")
        this.threadMetadata = threadMetadata

        return this
    }

    override fun finalizeData(): RequestBody? =
        builder.build().use { data ->
            val json = data.toData()
            if (ephemeral) {
                json.put("flags", json.getInt("flags", 0) or MessageFlag.EPHEMERAL.value)
            }

            if (username != null) {
                json.put("username", username)
            }
            if (avatar != null) {
                json.put("avatar_url", avatar)
            }

            val metadata = threadMetadata
            if (threadId == null && metadata != null) {
                json.put("thread_name", metadata.name)
                val tags = metadata.appliedTags
                if (!tags.isEmpty()) {
                    json.put(
                        "applied_tags",
                        tags.stream().map { it.id }.collect(Helpers.toDataArray<String>()),
                    )
                }
            }

            getMultipartBody(data.allDistinctFiles, json)
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
