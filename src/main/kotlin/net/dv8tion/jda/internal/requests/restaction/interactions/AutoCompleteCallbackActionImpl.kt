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

import net.dv8tion.jda.api.interactions.callbacks.IAutoCompleteCallback
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.InteractionCallbackAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.interactions.InteractionImpl
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull

open class AutoCompleteCallbackActionImpl(
    interaction: IAutoCompleteCallback,
    private val type: OptionType,
) : InteractionCallbackImpl<Void>(interaction as InteractionImpl),
    AutoCompleteCallbackAction {
    private val choices: MutableList<Command.Choice> = ArrayList(26)

    @Nonnull
    override fun getOptionType(): OptionType = type

    @Nonnull
    override fun addChoices(choices: Collection<Command.Choice>): AutoCompleteCallbackAction {
        Checks.noneNull(choices, "Choices")
        Checks.check(
            choices.size + this.choices.size <= OptionData.MAX_CHOICES,
            "Can only reply with up to %d choices. Limit your suggestions!",
            OptionData.MAX_CHOICES,
        )
        for (choice in choices) {
            Checks.inRange(choice.name, 1, OptionData.MAX_CHOICE_NAME_LENGTH, "Choice name")

            when (type) {
                OptionType.INTEGER -> {
                    Checks.check(
                        choice.type === OptionType.INTEGER,
                        "Choice of type %s cannot be converted to INTEGER",
                        choice.type,
                    )
                    val valueLong = choice.asLong
                    Checks.check(
                        valueLong <= OptionData.MAX_POSITIVE_NUMBER,
                        "Choice value cannot be larger than %f Provided: %d",
                        OptionData.MAX_POSITIVE_NUMBER,
                        valueLong,
                    )
                    Checks.check(
                        valueLong >= OptionData.MIN_NEGATIVE_NUMBER,
                        "Choice value cannot be smaller than %f. Provided: %d",
                        OptionData.MIN_NEGATIVE_NUMBER,
                        valueLong,
                    )
                }
                OptionType.NUMBER -> {
                    Checks.check(
                        choice.type === OptionType.NUMBER || choice.type === OptionType.INTEGER,
                        "Choice of type %s cannot be converted to NUMBER",
                        choice.type,
                    )
                    val valueDouble = choice.asDouble
                    Checks.check(
                        valueDouble <= OptionData.MAX_POSITIVE_NUMBER,
                        "Choice value cannot be larger than %f Provided: %f",
                        OptionData.MAX_POSITIVE_NUMBER,
                        valueDouble,
                    )
                    Checks.check(
                        valueDouble >= OptionData.MIN_NEGATIVE_NUMBER,
                        "Choice value cannot be smaller than %f. Provided: %f",
                        OptionData.MIN_NEGATIVE_NUMBER,
                        valueDouble,
                    )
                }
                OptionType.STRING -> {
                    // String can be any type, we just toString it
                    val valueString = choice.asString
                    Checks.notLonger(valueString, OptionData.MAX_CHOICE_VALUE_LENGTH, "Choice value")
                }
                else -> {}
            }
        }
        this.choices.addAll(choices)
        return this
    }

    override fun finalizeData(): RequestBody {
        val data = DataObject.empty()
        val array = DataArray.empty()
        choices.forEach { choice -> array.add(choice.toData(type)) }
        data.put("choices", array)
        return getRequestBody(
            DataObject
                .empty()
                .put("type", InteractionCallbackAction.ResponseType.COMMAND_AUTOCOMPLETE_CHOICES.raw)
                .put("data", data),
        )
    }

    @Nonnull
    override fun setCheck(checks: BooleanSupplier?): AutoCompleteCallbackAction =
        super<InteractionCallbackImpl>.setCheck(checks) as AutoCompleteCallbackAction

    @Nonnull
    override fun deadline(timestamp: Long): AutoCompleteCallbackAction =
        super<InteractionCallbackImpl>.deadline(timestamp) as AutoCompleteCallbackAction
}
