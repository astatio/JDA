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

package net.dv8tion.jda.internal.components.textinput

import net.dv8tion.jda.api.components.label.LabelChildComponentUnion
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class TextInputImpl :
    AbstractComponentImpl,
    TextInput,
    LabelChildComponentUnion {
    private val id: String
    private val uniqueId: Int
    private val style: TextInputStyle
    private val minLength: Int
    private val maxLength: Int
    private val required: Boolean
    private val value: String?
    private val placeholder: String?

    constructor(obj: DataObject) : this(
        obj.getString("custom_id"),
        obj.getInt("id", -1),
        TextInputStyle.fromKey(obj.getInt("style", -1)),
        obj.getInt("min_length", -1),
        obj.getInt("max_length", -1),
        obj.getBoolean("required", true),
        obj.getString("value", null),
        obj.getString("placeholder", null),
    )

    constructor(
        id: String,
        uniqueId: Int,
        style: TextInputStyle,
        minLength: Int,
        maxLength: Int,
        required: Boolean,
        value: String?,
        placeholder: String?,
    ) {
        this.id = id
        this.uniqueId = uniqueId
        this.style = style
        this.minLength = minLength
        this.maxLength = maxLength
        this.required = required
        this.value = value
        this.placeholder = placeholder
    }

    @Nonnull
    override fun withUniqueId(uniqueId: Int): TextInputImpl {
        Checks.positive(uniqueId, "Unique ID")
        return TextInputImpl(id, uniqueId, style, minLength, maxLength, required, value, placeholder)
    }

    @Nonnull
    override fun getStyle(): TextInputStyle = style

    @Nonnull
    override fun getCustomId(): String = id

    override fun getUniqueId(): Int = uniqueId

    override fun getMinLength(): Int = minLength

    override fun getMaxLength(): Int = maxLength

    override fun isRequired(): Boolean = required

    @Nullable
    override fun getValue(): String? = value

    @Nullable
    override fun getPlaceHolder(): String? = placeholder

    @Nonnull
    override fun toData(): DataObject {
        val obj =
            DataObject
                .empty()
                .put("type", type.key)
                .put("custom_id", id)
                .put("style", style.raw)
                .put("required", required)
        if (uniqueId >= 0) {
            obj.put("id", uniqueId)
        }
        if (minLength != -1) {
            obj.put("min_length", minLength)
        }
        if (maxLength != -1) {
            obj.put("max_length", maxLength)
        }
        if (value != null) {
            obj.put("value", value)
        }
        if (placeholder != null) {
            obj.put("placeholder", placeholder)
        }
        return obj
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is TextInputImpl) {
            return false
        }
        return uniqueId == other.uniqueId &&
            minLength == other.minLength &&
            maxLength == other.maxLength &&
            required == other.required &&
            id == other.id &&
            style == other.style &&
            Objects.equals(value, other.value) &&
            Objects.equals(placeholder, other.placeholder)
    }

    override fun hashCode(): Int = Objects.hash(id, uniqueId, style, minLength, maxLength, required, value, placeholder)

    override fun toString(): String =
        EntityString(this)
            .setType(style)
            .addMetadata("id", id)
            .addMetadata("value", value)
            .toString()
}
