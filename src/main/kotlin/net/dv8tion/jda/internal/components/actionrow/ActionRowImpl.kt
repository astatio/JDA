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

package net.dv8tion.jda.internal.components.actionrow

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion
import net.dv8tion.jda.api.components.container.ContainerChildComponentUnion
import net.dv8tion.jda.api.components.replacer.ComponentReplacer
import net.dv8tion.jda.api.components.utils.ComponentDeserializer
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.components.utils.ComponentsUtil
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.util.stream.Collectors
import javax.annotation.Nonnull

class ActionRowImpl :
    AbstractComponentImpl,
    ActionRow,
    MessageTopLevelComponentUnion,
    ContainerChildComponentUnion {
    private val uniqueId: Int
    private val components: List<ActionRowChildComponentUnion>

    constructor(deserializer: ComponentDeserializer, data: DataObject) : this(
        deserializer
            .deserializeAs(ActionRowChildComponentUnion::class.java, data.getArray("components"))
            .collect(Collectors.toList()),
        data.getInt("id", -1),
    )

    constructor(components: Collection<ActionRowChildComponentUnion>, uniqueId: Int) {
        this.uniqueId = uniqueId
        this.components = Helpers.copyAsUnmodifiableList(components)
    }

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getComponents(): List<ActionRowChildComponentUnion> = components

    @Nonnull
    override fun replace(
        @Nonnull replacer: ComponentReplacer,
    ): ActionRow {
        Checks.notNull(replacer, "ComponentReplacer")

        return ComponentsUtil.doReplace(
            ActionRowChildComponent::class.java,
            components,
            replacer,
        ) { newComponents -> validated(newComponents, uniqueId) }
    }

    @Nonnull
    override fun withUniqueId(uniqueId: Int): ActionRowImpl {
        Checks.positive(uniqueId, "Unique ID")
        return ActionRowImpl(components, uniqueId)
    }

    @Nonnull
    override fun withComponents(
        @Nonnull components: Collection<ActionRowChildComponent>,
    ): ActionRow =
        ActionRowImpl(
            ComponentsUtil.membersToUnion(components, ActionRowChildComponentUnion::class.java),
            uniqueId,
        )

    @Nonnull
    override fun getType(): Component.Type = Component.Type.ACTION_ROW

    @Nonnull
    override fun toData(): DataObject {
        val json = DataObject.empty().put("type", 1).put("components", DataArray.fromCollection(components))
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        return json
    }

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("components", components)
            .toString()

    override fun hashCode(): Int = components.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ActionRowImpl) {
            return false
        }

        return components == other.components
    }

    companion object {
        @Nonnull
        @JvmStatic
        fun validated(
            @Nonnull components: Collection<ActionRowChildComponent>,
        ): ActionRow = validated(components, -1)

        @Nonnull
        @JvmStatic
        fun validated(
            @Nonnull components: Collection<ActionRowChildComponent>,
            uniqueId: Int,
        ): ActionRow {
            Checks.notEmpty(components, "Row")
            Checks.noneNull(components, "Components")
            checkIsValid(components)

            // Don't allow unknown components in user-called methods
            val componentUnions = ComponentsUtil.membersToUnion(components, ActionRowChildComponentUnion::class.java)
            return ActionRowImpl(componentUnions, uniqueId)
        }

        @Nonnull
        @JvmStatic
        fun partitionOf(
            @Nonnull components: Collection<ActionRowChildComponent>,
        ): List<ActionRow> {
            Checks.noneNull(components, "Components")
            Checks.notEmpty(components, "Components")
            // Don't allow unknown components in user-called methods
            val componentUnions = ComponentsUtil.membersToUnion(components, ActionRowChildComponentUnion::class.java)

            val rows = ArrayList<ActionRow>()
            // The current action row we are building
            val currentRow = ArrayList<ActionRowChildComponentUnion>()
            // The component types contained in that row (for now it can't have mixed types)
            var type: Component.Type? = null

            for (current in componentUnions) {
                if ((type != null && type != current.type) ||
                    currentRow.size == ActionRow.getMaxAllowed(current.type)
                ) {
                    rows.add(ActionRow.of(currentRow))
                    currentRow.clear()
                }

                type = current.type
                currentRow.add(current)
            }

            rows.add(ActionRow.of(currentRow))

            return rows
        }

        // The TODO below is ported verbatim from the Java original; it records a real upstream follow-up.
        @Suppress("ForbiddenComment")
        private fun checkIsValid(components: Collection<ActionRowChildComponent>) {
            val groups = components.stream().collect(Collectors.groupingBy(Component::getType))
            // TODO: You can't mix components right now but maybe in the future, we need to check back
            // on this when that happens
            if (groups.size > 1) {
                throw IllegalArgumentException(
                    "Cannot create action row containing different component types! Provided: " + groups.keys,
                )
            }

            for ((type, list) in groups) {
                val maxAllowed = ActionRow.getMaxAllowed(type)
                Checks.check(
                    list.size <= maxAllowed,
                    "Cannot create an action row with more than %d %s! Provided: %d",
                    maxAllowed,
                    type.name,
                    list.size,
                )
            }
        }
    }
}
