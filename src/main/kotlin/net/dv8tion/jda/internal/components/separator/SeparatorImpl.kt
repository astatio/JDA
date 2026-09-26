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

package net.dv8tion.jda.internal.components.separator

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.components.container.ContainerChildComponentUnion
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.separator.Separator.Spacing
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull

class SeparatorImpl :
    AbstractComponentImpl,
    Separator,
    MessageTopLevelComponentUnion,
    ContainerChildComponentUnion {
    private val uniqueId: Int
    private val spacing: Spacing
    private val isDivider: Boolean

    constructor(obj: DataObject) : this(
        obj.getInt("id", -1),
        Spacing.fromKey(obj.getInt("spacing", 1)),
        obj.getBoolean("divider", true),
    )

    constructor(spacing: Spacing, isDivider: Boolean) : this(-1, spacing, isDivider) {
        Checks.check(spacing != Spacing.UNKNOWN, "Spacing cannot be unknown")
    }

    private constructor(uniqueId: Int, spacing: Spacing, isDivider: Boolean) {
        this.uniqueId = uniqueId
        this.spacing = spacing
        this.isDivider = isDivider
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.SEPARATOR

    @Nonnull
    override fun withUniqueId(uniqueId: Int): SeparatorImpl {
        Checks.positive(uniqueId, "Unique ID")
        return SeparatorImpl(uniqueId, spacing, isDivider)
    }

    @Nonnull
    override fun withDivider(divider: Boolean): Separator = SeparatorImpl(uniqueId, spacing, divider)

    @Nonnull
    override fun withSpacing(
        @Nonnull spacing: Spacing,
    ): Separator {
        Checks.notNull(spacing, "Spacing")
        Checks.check(spacing != Spacing.UNKNOWN, "Spacing cannot be unknown")
        return SeparatorImpl(uniqueId, spacing, isDivider)
    }

    override fun getUniqueId(): Int = uniqueId

    override fun isDivider(): Boolean = isDivider

    @Nonnull
    override fun getSpacing(): Spacing = spacing

    @Nonnull
    override fun toData(): DataObject {
        val json =
            DataObject
                .empty()
                .put("type", type.key)
                .put("divider", isDivider)
                .put("spacing", spacing.key)
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is SeparatorImpl) {
            return false
        }
        return uniqueId == other.uniqueId && isDivider == other.isDivider && spacing == other.spacing
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, spacing, isDivider)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("divider", isDivider)
            .addMetadata("spacing", spacing)
            .toString()
}
