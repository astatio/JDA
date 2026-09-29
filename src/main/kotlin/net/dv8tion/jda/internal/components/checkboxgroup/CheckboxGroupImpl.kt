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

package net.dv8tion.jda.internal.components.checkboxgroup

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroup
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroupOption
import net.dv8tion.jda.api.components.label.LabelChildComponentUnion
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Objects
import javax.annotation.Nonnull

class CheckboxGroupImpl :
    AbstractComponentImpl,
    CheckboxGroup,
    LabelChildComponentUnion {
    private val uniqueId: Int
    private val customId: String
    private val options: List<CheckboxGroupOption>
    private val minValues: Int
    private val maxValues: Int
    private val required: Boolean

    constructor(
        uniqueId: Int,
        customId: String,
        options: List<CheckboxGroupOption>,
        minValues: Int,
        maxValues: Int,
        required: Boolean,
    ) {
        this.uniqueId = uniqueId
        this.customId = customId
        this.options = Helpers.copyAsUnmodifiableList(options)
        this.minValues = minValues
        this.maxValues = maxValues
        this.required = required
    }

    constructor(data: DataObject) {
        this.uniqueId = data.getInt("id", -1)
        this.customId = data.getString("custom_id")
        this.options =
            data
                .getArray("options")
                .stream(DataArray::getObject)
                .map { CheckboxGroupOption.fromData(it) }
                .collect(Helpers.toUnmodifiableList())
        this.minValues = data.getInt("min_values", -1)
        this.maxValues = data.getInt("max_values", -1)
        this.required = data.getBoolean("required", true)
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.CHECKBOX_GROUP

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getCustomId(): String = customId

    @Nonnull
    override fun getOptions(): List<CheckboxGroupOption> = options

    override fun getMinValues(): Int = minValues

    override fun getMaxValues(): Int = maxValues

    override fun isRequired(): Boolean = required

    @Nonnull
    override fun withUniqueId(uniqueId: Int): CheckboxGroupImpl =
        CheckboxGroupImpl(uniqueId, customId, options, minValues, maxValues, required)

    @Nonnull
    override fun toData(): DataObject {
        val obj =
            DataObject
                .empty()
                .put("type", type.key)
                .put("custom_id", customId)
                .put("options", DataArray.fromCollection(options))
        if (uniqueId != -1) {
            obj.put("id", uniqueId)
        }
        if (minValues != -1) {
            obj.put("min_values", minValues)
        }
        if (maxValues != -1) {
            obj.put("max_values", maxValues)
        }
        if (!required) {
            obj.put("required", false)
        }

        return obj
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is CheckboxGroupImpl) {
            return false
        }
        return uniqueId == other.uniqueId &&
            customId == other.customId &&
            options == other.options &&
            minValues == other.minValues &&
            maxValues == other.maxValues &&
            required == other.required
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, customId, options, minValues, maxValues, required)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("custom_id", customId)
            .addMetadata("required", required)
            .toString()
}
