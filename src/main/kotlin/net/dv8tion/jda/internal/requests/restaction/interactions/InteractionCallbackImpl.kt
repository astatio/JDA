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

import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.interactions.InteractionCallbackAction
import net.dv8tion.jda.internal.interactions.InteractionImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import javax.annotation.Nonnull
import javax.annotation.Nullable

abstract class InteractionCallbackImpl<T> :
    RestActionImpl<T>,
    InteractionCallbackAction<T> {
    @JvmField
    protected val interaction: InteractionImpl

    constructor(interaction: InteractionImpl) :
        super(
            interaction.jda,
            Route.Interactions.CALLBACK
                .compile(interaction.id, interaction.token)
                .withQueryParams("with_response", "true"),
        ) {
        this.interaction = interaction
        setErrorMapper(this::handleUnknownInteraction)
    }

    // response/request are unused but required by the ErrorMapper functional interface signature.
    @Suppress("ReturnCount", "UnusedParameter")
    @Nullable
    private fun handleUnknownInteraction(
        response: Response,
        request: Request<*>,
        exception: ErrorResponseException,
    ): Throwable? {
        if (exception.errorResponse === ErrorResponse.INTERACTION_ALREADY_ACKNOWLEDGED) {
            return ErrorResponseException.create(
                "This interaction was acknowledged by another process running for the same bot.\n" +
                    "To resolve this, try stopping all current processes for the bot that could be responsible, " +
                    "or resetting your bot token.\n" +
                    "You can reset your token at https://discord.com/developers/applications/" +
                    jda.selfUser.applicationId + "/bot",
                exception,
            )
        }

        if (exception.errorResponse === ErrorResponse.UNKNOWN_INTERACTION) {
            return ErrorResponseException.create(
                "Failed to acknowledge this interaction, this can be due to 2 reasons:\n" +
                    "1. This interaction took longer than 3 seconds to be acknowledged, see " +
                    "https://jda.wiki/using-jda/troubleshooting/" +
                    "#the-interaction-took-longer-than-3-seconds-to-be-acknowledged\n" +
                    "2. This interaction could have been acknowledged by another process running for the same bot\n" +
                    "You can confirm this by checking if your bot replied, or the three dots in a button disappeared " +
                    "without saying 'This interaction failed', or you see '[Bot] is thinking...' for more than 3 seconds.\n" +
                    "To resolve this, try stopping all current processes for the bot that could be responsible, " +
                    "or resetting your bot token.\n" +
                    "You can reset your token at https://discord.com/developers/applications/" +
                    jda.selfUser.applicationId + "/bot",
                exception,
            )
        }

        return null
    }

    @Nonnull
    override fun closeResources(): InteractionCallbackAction<T> = this

    protected fun tryAck(): IllegalStateException? =
        if (interaction.ack()) {
            IllegalStateException(
                "This interaction has already been acknowledged or replied to. You can only reply or acknowledge an interaction once!",
            )
        } else {
            null
        }

    override fun queue(
        success: Consumer<in T>?,
        failure: Consumer<in Throwable>?,
    ) {
        val exception = tryAck()
        if (exception != null) {
            if (failure != null) {
                failure.accept(exception)
            } else {
                RestActionImpl.getDefaultFailure().accept(exception)
            }
            return
        }

        super<RestActionImpl>.queue(success, failure)
    }

    @Nonnull
    override fun submit(shouldQueue: Boolean): CompletableFuture<T> {
        val exception = tryAck()
        if (exception != null) {
            val future = CompletableFuture<T>()
            future.completeExceptionally(exception)
            return future
        }

        return super<RestActionImpl>.submit(shouldQueue)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<T>,
    ) {
        interaction.releaseHook(true) // sends followup messages
        super.handleSuccess(response, request)
    }

    override fun handleResponse(
        response: Response,
        request: Request<T>,
    ) {
        if (!response.isOk) {
            interaction.releaseHook(false) // cancels followup messages with an exception
        }
        super.handleResponse(response, request)
    }
}
