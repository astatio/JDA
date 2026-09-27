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

import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload
import net.dv8tion.jda.api.interactions.commands.context.ContextInteraction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import javax.annotation.Nonnull

abstract class ContextInteractionImpl<T : Any>(
    jda: JDAImpl,
    data: DataObject,
) : CommandInteractionImpl(jda, data),
    ContextInteraction<T>,
    CommandInteractionPayloadMixin {
    private val target: T
    private val payload: CommandInteractionPayloadImpl

    init {
        payload = CommandInteractionPayloadImpl(jda, data)
        target = parse(data, data.getObject("data").getObject("resolved"))
    }

    protected abstract fun parse(
        interactionData: DataObject,
        resolved: DataObject,
    ): T

    override fun getCommandPayload(): CommandInteractionPayload = payload

    @Nonnull
    override fun getTarget(): T = target
}
