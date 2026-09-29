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

package net.dv8tion.jda.internal.interactions.components

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.interactions.components.ComponentInteraction
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.ReceivedMessage
import net.dv8tion.jda.internal.interactions.DeferrableInteractionImpl
import net.dv8tion.jda.internal.requests.restaction.interactions.MessageEditCallbackActionImpl
import net.dv8tion.jda.internal.requests.restaction.interactions.ModalCallbackActionImpl
import net.dv8tion.jda.internal.requests.restaction.interactions.ReplyCallbackActionImpl
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull

abstract class ComponentInteractionImpl(
    jda: JDAImpl,
    data: DataObject,
) : DeferrableInteractionImpl(jda, data),
    ComponentInteraction {
    @JvmField
    protected val customId: String = data.getObject("data").getString("custom_id")

    // message might be just id and flags for ephemeral messages
    // in which case our "message" is null
    @JvmField
    protected val message: Message?

    @JvmField
    protected val messageId: Long

    init {
        val messageJson = data.getObject("message")
        messageId = messageJson.getUnsignedLong("id")

        message =
            if (messageJson.isNull("type")) {
                null
            } else {
                val guild = getGuild()
                val channel = getChannelChannel() as MessageChannel
                val created = jda.entityBuilder.createMessageBestEffort(messageJson, channel, guild)
                // We assume that component interactions come from messages the bot sent
                (created as ReceivedMessage).withHook(getHook())
            }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getChannel(): MessageChannelUnion = getChannelChannel() as MessageChannelUnion

    @Nonnull
    override fun getComponentId(): String = customId

    @Nonnull
    override fun getMessage(): Message = message!!

    override fun getMessageIdLong(): Long = messageId

    @Nonnull
    override fun deferEdit(): MessageEditCallbackActionImpl = MessageEditCallbackActionImpl(this.hook)

    @Nonnull
    override fun deferReply(): ReplyCallbackAction = ReplyCallbackActionImpl(this.hook)

    @Nonnull
    override fun replyModal(
        @Nonnull modal: Modal,
    ): ModalCallbackAction {
        Checks.notNull(modal, "Modal")

        return ModalCallbackActionImpl(this, modal)
    }
}
