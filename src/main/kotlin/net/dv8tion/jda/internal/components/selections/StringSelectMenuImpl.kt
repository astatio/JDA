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

package net.dv8tion.jda.internal.components.selections

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.label.LabelChildComponentUnion
import net.dv8tion.jda.api.components.selections.SelectOption
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import java.util.Collections
import java.util.Objects
import javax.annotation.Nonnull

class StringSelectMenuImpl :
    SelectMenuImpl,
    StringSelectMenu,
    LabelChildComponentUnion {
    private val options: List<SelectOption>

    constructor(data: DataObject) : super(data) {
        this.options = parseOptions(data.getArray("options"))
    }

    constructor(
        id: String,
        uniqueId: Int,
        placeholder: String?,
        minValues: Int,
        maxValues: Int,
        disabled: Boolean,
        options: @JvmSuppressWildcards List<SelectOption>,
        required: Boolean?,
    ) : super(id, uniqueId, placeholder, minValues, maxValues, disabled, required) {
        this.options = options
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.STRING_SELECT

    @Nonnull
    override fun withUniqueId(uniqueId: Int): StringSelectMenuImpl = createCopy().setUniqueId(uniqueId).build() as StringSelectMenuImpl

    @Nonnull
    override fun getOptions(): List<SelectOption> = Collections.unmodifiableList(options)

    @Nonnull
    override fun toData(): DataObject =
        super
            .toData()
            .put("type", Component.Type.STRING_SELECT.key)
            .put("options", DataArray.fromCollection(options))

    override fun hashCode(): Int = Objects.hash(id, placeholder, minValues, maxValues, disabled, options)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is StringSelectMenu) {
            return false
        }
        return id == other.customId &&
            placeholder == other.placeholder &&
            minValues == other.minValues &&
            maxValues == other.maxValues &&
            disabled == other.isDisabled &&
            options == other.options &&
            required == other.isRequired
    }

    companion object {
        private fun parseOptions(array: DataArray): List<SelectOption> {
            val options = ArrayList<SelectOption>(array.length())
            array.stream { arr, i -> arr.getObject(i) }.map(SelectOption::fromData).forEach { options.add(it) }
            return options
        }
    }
}
