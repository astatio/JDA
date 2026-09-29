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

package net.dv8tion.jda.internal.interactions

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.exceptions.InteractionExpiredException
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.response.InteractionCallbackResponse
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.WebhookMessageDeleteAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageRetrieveAction
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.AbstractWebhookClient
import net.dv8tion.jda.internal.interactions.response.InteractionCallbackResponseImpl
import net.dv8tion.jda.internal.requests.restaction.TriggerRestAction
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageCreateActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageDeleteActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageEditActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageRetrieveActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.JDALogger
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import javax.annotation.Nonnull

class InteractionHookImpl :
    AbstractWebhookClient<Message>,
    InteractionHook {
    private val interaction: DeferrableInteractionImpl?
    private val readyCallbacks: MutableList<TriggerRestAction<*>> = ArrayList()
    private val timeoutHandle: Future<*>?
    private val mutex = ReentrantLock()
    private var exception: Exception? = null
    private var isReady = false
    private var ephemeral = false
    private var callbackResponse: InteractionCallbackResponseImpl? = null

    constructor(
        @Nonnull interaction: DeferrableInteractionImpl,
        @Nonnull api: JDA,
    ) :
        super(api.selfUser.applicationIdLong, interaction.token, api) {
        this.interaction = interaction
        // 10 second timeout for our failure
        this.timeoutHandle =
            api.gatewayPool.schedule(
                { this.fail(TimeoutException(TIMEOUT_MESSAGE)) },
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
    }

    constructor(
        @Nonnull api: JDA,
        @Nonnull token: String,
    ) : super(api.selfUser.applicationIdLong, token, api) {
        this.interaction = null
        this.timeoutHandle = null
        this.isReady = true
    }

    fun ack(): Boolean = interaction?.ack() ?: true

    fun isAck(): Boolean = interaction?.isAcknowledged ?: true

    fun ready() {
        MiscUtil.locked(
            mutex,
            Runnable {
                timeoutHandle?.cancel(false)
                isReady = true
                readyCallbacks.forEach { it.run() }
            },
        )
    }

    fun fail(exception: Exception) {
        MiscUtil.locked(
            mutex,
            Runnable {
                if (!isReady && this.exception == null) {
                    this.exception = exception
                    // only log this if we even tried any responses
                    if (readyCallbacks.isNotEmpty()) {
                        if (exception is TimeoutException) {
                            JDALogger
                                .getLog(InteractionHook::class.java)
                                .warn(
                                    "Up to {} Interaction Followup Messages Timed out! Did you forget to acknowledge the interaction?",
                                    readyCallbacks.size,
                                )
                        }
                        readyCallbacks.forEach { callback -> callback.fail(exception) }
                    }
                }
            },
        )
    }

    private fun <T : TriggerRestAction<R>, R> onReady(runnable: T): T =
        MiscUtil.locked(
            mutex,
            Supplier {
                if (isReady) {
                    runnable.run()
                } else if (exception != null) {
                    runnable.fail(exception!!)
                } else {
                    readyCallbacks.add(runnable)
                }
                runnable
            },
        )

    fun setCallbackResponse(callbackResponse: InteractionCallbackResponseImpl): InteractionHookImpl {
        this.callbackResponse = callbackResponse
        return this
    }

    @Nonnull
    override fun getInteraction(): InteractionImpl =
        interaction ?: throw IllegalStateException("Cannot get interaction instance from this webhook.")

    @Nonnull
    override fun getCallbackResponse(): InteractionCallbackResponse {
        if (!hasCallbackResponse()) {
            throw IllegalStateException("Cannot get callback response. Has this interaction been acknowledged yet?")
        }
        return callbackResponse!!
    }

    override fun hasCallbackResponse(): Boolean = callbackResponse != null

    override fun getExpirationTimestamp(): Long {
        val creationTime = if (interaction == null) OffsetDateTime.now() else interaction.getTimeCreated()
        return creationTime.plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES).toEpochSecond() * MILLIS_PER_SECOND
    }

    @Nonnull
    override fun setEphemeral(ephemeral: Boolean): InteractionHook {
        this.ephemeral = ephemeral
        return this
    }

    @Nonnull
    override fun sendRequest(): WebhookMessageCreateActionImpl<Message> {
        var route = Route.Interactions.CREATE_FOLLOWUP.compile(api.selfUser.applicationId, token)
        route = route.withQueryParams("wait", "true")
        val action =
            WebhookMessageCreateActionImpl<Message>(api, route, { buildMessage(it) }).setEphemeral(ephemeral)
        action.setCheck { checkExpired() }
        return onReady(action)
    }

    @Nonnull
    override fun editRequest(messageId: String): WebhookMessageEditActionImpl<Message> {
        if ("@original" != messageId) {
            Checks.isSnowflake(messageId)
        }

        var route = Route.Interactions.EDIT_FOLLOWUP.compile(api.selfUser.applicationId, token, messageId)
        route = route.withQueryParams("wait", "true")
        val action = WebhookMessageEditActionImpl<Message>(api, route, { buildMessage(it) })
        action.setCheck { checkExpired() }
        return onReady(action)
    }

    @Nonnull
    override fun deleteMessageById(
        @Nonnull messageId: String,
    ): WebhookMessageDeleteAction {
        if ("@original" != messageId) {
            Checks.isSnowflake(messageId)
        }
        val route = Route.Interactions.DELETE_FOLLOWUP.compile(api.selfUser.applicationId, token, messageId)
        val action = WebhookMessageDeleteActionImpl(api, route)
        action.setCheck { checkExpired() }
        return onReady(action)
    }

    @Nonnull
    override fun retrieveMessageById(
        @Nonnull messageId: String,
    ): WebhookMessageRetrieveAction {
        if ("@original" != messageId) {
            Checks.isSnowflake(messageId)
        }
        val route = Route.Interactions.GET_MESSAGE.compile(api.selfUser.applicationId, token, messageId)
        val action = WebhookMessageRetrieveActionImpl(api, route) { response, _ -> buildMessage(response.getObject()) }
        action.setCheck { checkExpired() }
        return onReady(action)
    }

    private fun checkExpired(): Boolean {
        if (isExpired) {
            throw InteractionExpiredException()
        }
        return true
    }

    // Creates a message with the resolved channel context from the interaction
    // Sometimes we can't resolve the channel and report an unknown type
    // Currently known cases where channels can't be resolved:
    //  - InteractionHook created using id/token factory,
    //    has no interaction object to use as context
    fun buildMessage(json: DataObject): Message {
        val jda = api as JDAImpl
        var channel: MessageChannel? = null
        var guild: Guild? = null

        // Try getting context from interaction if available
        // This might not be present if the hook was created from id/token instead of an event
        if (interaction != null) {
            channel = interaction.getChannel() as MessageChannel?
            guild = interaction.guild
        }

        // Try finding the channel in cache through the id in the message
        val channelId = json.getUnsignedLong("channel_id")
        if (channel == null) {
            channel = api.getChannelById(MessageChannel::class.java, channelId)
        }

        // Then build the message with the information we have
        val message = jda.entityBuilder.createMessageBestEffort(json, channel, guild)
        return message.withHook(this)
    }

    companion object {
        const val TIMEOUT_MESSAGE: String = "Timed out waiting for interaction acknowledgement"

        private const val TIMEOUT_SECONDS: Long = 10
        private const val EXPIRATION_MINUTES: Long = 15
        private const val MILLIS_PER_SECOND: Long = 1000
    }
}
