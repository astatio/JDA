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

import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion
import net.dv8tion.jda.api.components.selections.SelectMenu
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.utils.EntityString
import javax.annotation.Nonnull
import javax.annotation.Nullable

abstract class SelectMenuImpl :
    AbstractComponentImpl,
    SelectMenu,
    ActionRowChildComponentUnion {
    @JvmField
    protected val id: String

    @JvmField
    protected val placeholder: String?

    @JvmField
    protected val uniqueId: Int

    @JvmField
    protected val minValues: Int

    @JvmField
    protected val maxValues: Int

    @JvmField
    protected val disabled: Boolean

    @JvmField
    protected val required: Boolean?

    constructor(data: DataObject) : this(
        data.getString("custom_id"),
        data.getInt("id", -1),
        data.getString("placeholder", null),
        data.getInt("min_values", 1),
        data.getInt("max_values", 1),
        data.getBoolean("disabled"),
        if (data.isNull("required")) null else data.getBoolean("required"),
    )

    constructor(
        id: String,
        uniqueId: Int,
        placeholder: String?,
        minValues: Int,
        maxValues: Int,
        disabled: Boolean,
        required: Boolean?,
    ) {
        this.id = id
        this.uniqueId = uniqueId
        this.placeholder = placeholder
        this.minValues = minValues
        this.maxValues = maxValues
        this.disabled = disabled
        this.required = required
    }

    @Nonnull
    abstract override fun withUniqueId(uniqueId: Int): SelectMenuImpl

    @Nonnull
    override fun getCustomId(): String = id

    override fun getUniqueId(): Int = uniqueId

    @Nullable
    override fun getPlaceholder(): String? = placeholder

    override fun getMinValues(): Int = minValues

    override fun getMaxValues(): Int = maxValues

    override fun isDisabled(): Boolean = disabled

    override fun isRequired(): Boolean? = required

    @Nonnull
    override fun toData(): DataObject {
        val data = DataObject.empty()
        data.put("custom_id", id)
        if (uniqueId >= 0) {
            data.put("id", uniqueId)
        }
        data.put("min_values", minValues)
        data.put("max_values", maxValues)
        data.put("disabled", disabled)
        if (placeholder != null) {
            data.put("placeholder", placeholder)
        }
        if (required != null) {
            data.put("required", required)
        }
        return data
    }

    override fun toString(): String =
        EntityString(SelectMenu::class.java)
            .setType(type)
            .addMetadata("id", uniqueId)
            .addMetadata("custom id", id)
            .addMetadata("placeholder", placeholder)
            .toString()
}
