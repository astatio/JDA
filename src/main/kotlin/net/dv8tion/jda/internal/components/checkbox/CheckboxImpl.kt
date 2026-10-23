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

package net.dv8tion.jda.internal.components.checkbox

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.checkbox.Checkbox
import net.dv8tion.jda.api.components.checkbox.Checkbox.CUSTOM_ID_MAX_LENGTH
import net.dv8tion.jda.api.components.label.LabelChildComponentUnion
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull

class CheckboxImpl :
    AbstractComponentImpl,
    Checkbox,
    LabelChildComponentUnion {
    private val uniqueId: Int
    private val customId: String
    private val isDefault: Boolean

    constructor(uniqueId: Int, customId: String, isDefault: Boolean) {
        this.uniqueId = uniqueId
        this.customId = customId
        this.isDefault = isDefault
    }

    constructor(data: DataObject) {
        this.uniqueId = data.getInt("id", -1)
        this.customId = data.getString("custom_id")
        this.isDefault = data.getBoolean("default", false)
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.CHECKBOX

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getCustomId(): String = customId

    override fun isDefault(): Boolean = isDefault

    @Nonnull
    override fun withUniqueId(uniqueId: Int): CheckboxImpl {
        Checks.positive(uniqueId, "Unique ID")
        return CheckboxImpl(uniqueId, customId, isDefault)
    }

    @Nonnull
    override fun withCustomId(
        @Nonnull customId: String,
    ): CheckboxImpl {
        Checks.notBlank(customId, "Custom ID")
        Checks.notLonger(customId, CUSTOM_ID_MAX_LENGTH, "Custom ID")
        return CheckboxImpl(uniqueId, customId, isDefault)
    }

    @Nonnull
    override fun withDefault(isDefault: Boolean): CheckboxImpl = CheckboxImpl(uniqueId, customId, isDefault)

    @Nonnull
    override fun toData(): DataObject {
        val `object` = DataObject.empty().put("type", type.key).put("custom_id", customId)
        if (uniqueId >= 0) {
            `object`.put("id", uniqueId)
        }
        if (isDefault) {
            `object`.put("default", true)
        }

        return `object`
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is CheckboxImpl) {
            return false
        }
        return uniqueId == other.uniqueId && customId == other.customId && isDefault == other.isDefault
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, customId, isDefault)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", customId)
            .addMetadata("checked", isDefault)
            .toString()
}
