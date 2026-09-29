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

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.interactions.AutoCompleteQuery
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.interactions.InteractionImpl
import net.dv8tion.jda.internal.requests.restaction.interactions.AutoCompleteCallbackActionImpl
import javax.annotation.Nonnull

class CommandAutoCompleteInteractionImpl(
    jda: JDAImpl,
    data: DataObject,
) : InteractionImpl(jda, data),
    CommandInteractionPayloadMixin,
    CommandAutoCompleteInteraction {
    private val payload: CommandInteractionPayload
    private var focused: AutoCompleteQuery? = null

    init {
        payload = CommandInteractionPayloadImpl(jda, data)

        val options = data.getObject("data").getArray("options")
        findFocused(options)

        if (focused == null) {
            throw IllegalStateException("Failed to get focused option for auto complete interaction")
        }
    }

    private fun findFocused(options: DataArray) {
        for (i in 0 until options.length()) {
            val option = options.getObject(i)
            when (OptionType.fromKey(option.getInt("type"))) {
                OptionType.SUB_COMMAND, OptionType.SUB_COMMAND_GROUP -> findFocused(option.getArray("options"))
                else ->
                    if (option.getBoolean("focused")) {
                        val opt: OptionMapping = getOption(option.getString("name"))!!
                        focused = AutoCompleteQuery(opt)
                    }
            }
        }
    }

    @Nonnull
    override fun getFocusedOption(): AutoCompleteQuery = focused!!

    @Nonnull
    override fun getChannel(): MessageChannelUnion = getChannelChannel() as MessageChannelUnion

    override fun getCommandPayload(): CommandInteractionPayload = payload

    @Nonnull
    override fun replyChoices(
        @Nonnull choices: Collection<Command.Choice>,
    ): AutoCompleteCallbackAction = AutoCompleteCallbackActionImpl(this, focused!!.type).addChoices(choices)
}
