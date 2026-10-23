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

package net.dv8tion.jda.internal.interactions.modal

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.interactions.modals.ModalInteraction
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.interactions.DeferrableInteractionImpl
import net.dv8tion.jda.internal.requests.restaction.interactions.MessageEditCallbackActionImpl
import net.dv8tion.jda.internal.requests.restaction.interactions.ReplyCallbackActionImpl
import net.dv8tion.jda.internal.utils.Helpers
import javax.annotation.Nonnull

class ModalInteractionImpl(
    api: JDAImpl,
    json: DataObject,
) : DeferrableInteractionImpl(api, json),
    ModalInteraction {
    private val modalId: String
    private val mappings: List<ModalMapping>
    private val message: Message?

    init {
        val data = json.getObject("data")
        modalId = data.getString("custom_id")
        val resolved = data.optObject("resolved").orElseGet { DataObject.empty() }
        mappings =
            data
                .optArray("components")
                .orElseGet { DataArray.empty() }
                .stream { a, i -> a.getObject(i) }
                .map { component -> getMapping(component, resolved) }
                .filter { it != null }
                .map { it!! }
                .collect(Helpers.toUnmodifiableList())

        message =
            json
                .optObject("message")
                .map { o -> api.entityBuilder.createMessageWithChannel(o, getMessageChannel(), false) }
                .orElse(null)
    }

    private fun getMapping(
        component: DataObject,
        resolved: DataObject,
    ): ModalMapping? {
        val type = Component.Type.fromKey(component.getInt("type"))

        if (type == Component.Type.LABEL) {
            return ModalMapping(this, resolved, component.getObject("component"))
        }

        return null
    }

    @Nonnull
    override fun getModalId(): String = modalId

    @Nonnull
    override fun getValues(): List<ModalMapping> = mappings

    override fun getMessage(): Message? = message

    @Nonnull
    override fun deferReply(): ReplyCallbackAction = ReplyCallbackActionImpl(hook)

    @Nonnull
    override fun deferEdit(): MessageEditCallbackAction = MessageEditCallbackActionImpl(hook)

    @Suppress("UNCHECKED_CAST")
    override fun getChannel(): MessageChannelUnion = super.getChannel() as MessageChannelUnion
}
