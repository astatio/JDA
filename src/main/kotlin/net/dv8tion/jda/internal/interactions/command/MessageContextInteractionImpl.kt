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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import javax.annotation.Nonnull

class MessageContextInteractionImpl(
    jda: JDAImpl,
    data: DataObject,
) : ContextInteractionImpl<Message>(jda, data),
    MessageContextInteraction {
    override fun parse(
        interactionData: DataObject,
        resolved: DataObject,
    ): Message {
        val messages = resolved.getObject("messages")
        val message = messages.getObject(messages.keys().iterator().next())

        val guild: Guild? = getGuild()
        val channel: MessageChannel = getChannelChannel() as MessageChannel

        return api.entityBuilder.createMessageBestEffort(message, channel, guild)
    }

    @Nonnull
    override fun getChannel(): MessageChannelUnion = getChannelChannel() as MessageChannelUnion
}
