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

import net.dv8tion.jda.api.interactions.callbacks.IModalCallback
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.requests.restaction.interactions.InteractionCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.interactions.InteractionImpl
import okhttp3.RequestBody
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull

open class ModalCallbackActionImpl(
    interaction: IModalCallback,
    private val modal: Modal,
) : InteractionCallbackImpl<Void>(interaction as InteractionImpl),
    ModalCallbackAction {
    override fun finalizeData(): RequestBody =
        getRequestBody(
            DataObject
                .empty()
                .put("type", InteractionCallbackAction.ResponseType.MODAL.raw)
                .put("data", modal),
        )

    @Nonnull
    override fun setCheck(checks: BooleanSupplier?): ModalCallbackAction =
        super<InteractionCallbackImpl>.setCheck(checks) as ModalCallbackAction

    @Nonnull
    override fun deadline(timestamp: Long): ModalCallbackAction = super<InteractionCallbackImpl>.deadline(timestamp) as ModalCallbackAction
}
