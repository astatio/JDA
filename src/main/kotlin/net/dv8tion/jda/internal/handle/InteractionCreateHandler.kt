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

package net.dv8tion.jda.internal.handle

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.interactions.InteractionImpl
import net.dv8tion.jda.internal.interactions.command.CommandAutoCompleteInteractionImpl
import net.dv8tion.jda.internal.interactions.command.MessageContextInteractionImpl
import net.dv8tion.jda.internal.interactions.command.SlashCommandInteractionImpl
import net.dv8tion.jda.internal.interactions.command.UserContextInteractionImpl
import net.dv8tion.jda.internal.interactions.components.buttons.ButtonInteractionImpl
import net.dv8tion.jda.internal.interactions.components.selections.EntitySelectInteractionImpl
import net.dv8tion.jda.internal.interactions.components.selections.StringSelectInteractionImpl
import net.dv8tion.jda.internal.interactions.modal.ModalInteractionImpl
import net.dv8tion.jda.internal.requests.WebSocketClient

class InteractionCreateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val type = content.getInt("type")
        val version = content.getInt("version", 1)
        if (version != 1) {
            WebSocketClient.LOG.debug(
                "Received interaction with version {}. This version is currently unsupported by this version of JDA. " +
                    "Consider updating!",
                version,
            )
            return null
        }

        val guildId = content.getUnsignedLong("guild_id", 0)
        if (api.getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        // Check channel type
        val channelJson = content.getObject("channel")
        val channelType = ChannelType.fromId(channelJson.getInt("type"))
        if (!channelType.isMessage()) {
            WebSocketClient.LOG.debug(
                "Discarding INTERACTION_CREATE event from unexpected channel type. Channel: {}",
                channelJson,
            )
            return null
        }

        when (InteractionType.fromKey(type)) {
            InteractionType.COMMAND -> handleCommand(content)
            InteractionType.COMPONENT -> handleAction(content)
            InteractionType.COMMAND_AUTOCOMPLETE ->
                api.handleEvent(
                    CommandAutoCompleteInteractionEvent(
                        api,
                        responseNumber,
                        CommandAutoCompleteInteractionImpl(api, content),
                    ),
                )
            InteractionType.MODAL_SUBMIT ->
                api.handleEvent(
                    ModalInteractionEvent(api, responseNumber, ModalInteractionImpl(api, content)),
                )
            else ->
                api.handleEvent(
                    GenericInteractionCreateEvent(api, responseNumber, InteractionImpl(api, content)),
                )
        }

        return null
    }

    private fun handleCommand(content: DataObject) {
        val type = content.getObject("data").getInt("type")
        when (Command.Type.fromId(type)) {
            Command.Type.SLASH ->
                api.handleEvent(
                    SlashCommandInteractionEvent(
                        api,
                        responseNumber,
                        SlashCommandInteractionImpl(api, content),
                    ),
                )
            Command.Type.MESSAGE ->
                api.handleEvent(
                    MessageContextInteractionEvent(
                        api,
                        responseNumber,
                        MessageContextInteractionImpl(api, content),
                    ),
                )
            Command.Type.USER ->
                api.handleEvent(
                    UserContextInteractionEvent(
                        api,
                        responseNumber,
                        UserContextInteractionImpl(api, content),
                    ),
                )
            Command.Type.UNKNOWN ->
                WebSocketClient.LOG.debug(
                    "Received interaction with unknown command type {}",
                    type,
                )
        }
    }

    private fun handleAction(content: DataObject) {
        val type = content.getObject("data").getInt("component_type")
        when (Component.Type.fromKey(type)) {
            Component.Type.BUTTON ->
                api.handleEvent(
                    ButtonInteractionEvent(api, responseNumber, ButtonInteractionImpl(api, content)),
                )
            Component.Type.STRING_SELECT ->
                api.handleEvent(
                    StringSelectInteractionEvent(
                        api,
                        responseNumber,
                        StringSelectInteractionImpl(api, content),
                    ),
                )
            Component.Type.USER_SELECT,
            Component.Type.ROLE_SELECT,
            Component.Type.MENTIONABLE_SELECT,
            Component.Type.CHANNEL_SELECT,
            ->
                api.handleEvent(
                    EntitySelectInteractionEvent(
                        api,
                        responseNumber,
                        EntitySelectInteractionImpl(api, content),
                    ),
                )
            else -> WebSocketClient.LOG.debug("Received interaction with unknown component type {}", type)
        }
    }
}
