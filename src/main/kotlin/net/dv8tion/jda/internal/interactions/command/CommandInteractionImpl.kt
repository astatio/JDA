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

package net.dv8tion.jda.internal.interactions.command

import net.dv8tion.jda.api.interactions.commands.CommandInteraction
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.interactions.DeferrableInteractionImpl
import net.dv8tion.jda.internal.requests.restaction.interactions.ModalCallbackActionImpl
import net.dv8tion.jda.internal.requests.restaction.interactions.ReplyCallbackActionImpl
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull

open class CommandInteractionImpl(
    jda: JDAImpl,
    data: DataObject,
) : DeferrableInteractionImpl(jda, data),
    CommandInteraction,
    CommandInteractionPayloadMixin {
    private val payload: CommandInteractionPayloadImpl = CommandInteractionPayloadImpl(jda, data)

    override fun getCommandPayload(): CommandInteractionPayload = payload

    @Nonnull
    override fun deferReply(): ReplyCallbackAction = ReplyCallbackActionImpl(hook)

    @Nonnull
    override fun replyModal(
        @Nonnull modal: Modal,
    ): ModalCallbackAction {
        Checks.notNull(modal, "Modal")
        return ModalCallbackActionImpl(this, modal)
    }
}
