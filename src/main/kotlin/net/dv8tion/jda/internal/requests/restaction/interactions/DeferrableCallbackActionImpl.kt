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

import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.internal.interactions.InteractionHookImpl
import net.dv8tion.jda.internal.interactions.response.InteractionCallbackResponseImpl

abstract class DeferrableCallbackActionImpl protected constructor(
    @JvmField protected val hook: InteractionHookImpl,
) : InteractionCallbackImpl<InteractionHook>(hook.interaction) {
    // Here we intercept the responses and forward this information to our followup hook
    // Using this, we can make nice cascading exceptions which fail all followup messages
    // simultaneously.

    override fun handleSuccess(
        response: Response,
        request: Request<InteractionHook>,
    ) {
        // releaseHook would be called by the super class's handling of this success, however
        // we also need to provide the hook itself to the success callback of the RestAction,
        // so we override this functionality
        interaction.releaseHook(true)
        parseOptionalBody(response)
        request.onSuccess(hook)
    }

    private fun parseOptionalBody(response: Response) {
        val rawResponse = response.rawResponse ?: return
        val body = rawResponse.body

        val mediaType = body.contentType()
        if (mediaType != null && mediaType.toString().startsWith("application/json")) {
            response
                .optObject()
                .flatMap { obj -> obj.optObject("resource") }
                .ifPresent { resource ->
                    hook.setCallbackResponse(InteractionCallbackResponseImpl(hook, resource))
                }
        }
    }
}
