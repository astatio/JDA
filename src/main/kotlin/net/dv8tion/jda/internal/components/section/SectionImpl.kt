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

package net.dv8tion.jda.internal.components.section

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.components.container.ContainerChildComponentUnion
import net.dv8tion.jda.api.components.replacer.ComponentReplacer
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.section.Section.MAX_COMPONENTS
import net.dv8tion.jda.api.components.section.SectionAccessoryComponent
import net.dv8tion.jda.api.components.section.SectionAccessoryComponentUnion
import net.dv8tion.jda.api.components.section.SectionContentComponent
import net.dv8tion.jda.api.components.section.SectionContentComponentUnion
import net.dv8tion.jda.api.components.utils.ComponentDeserializer
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.components.utils.ComponentsUtil
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Collections
import java.util.Objects
import java.util.stream.Collectors
import javax.annotation.Nonnull

class SectionImpl :
    AbstractComponentImpl,
    Section,
    MessageTopLevelComponentUnion,
    ContainerChildComponentUnion {
    private val uniqueId: Int
    private val components: List<SectionContentComponentUnion>
    private val accessory: SectionAccessoryComponentUnion

    constructor(deserializer: ComponentDeserializer, data: DataObject) : this(
        data.getInt("id", -1),
        deserializer
            .deserializeAs(SectionContentComponentUnion::class.java, data.getArray("components"))
            .collect(Collectors.toList()),
        deserializer.deserializeAs(SectionAccessoryComponentUnion::class.java, data.getObject("accessory")),
    )

    constructor(
        components: Collection<SectionContentComponentUnion>,
        accessory: SectionAccessoryComponentUnion,
    ) : this(-1, components, accessory)

    constructor(
        uniqueId: Int,
        components: Collection<SectionContentComponentUnion>,
        accessory: SectionAccessoryComponentUnion,
    ) {
        Checks.notNull(accessory, "Accessory")
        this.uniqueId = uniqueId
        this.components = Helpers.copyAsUnmodifiableList(components)
        this.accessory = accessory
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.SECTION

    @Nonnull
    override fun withUniqueId(uniqueId: Int): SectionImpl {
        Checks.positive(uniqueId, "Unique ID")
        return SectionImpl(uniqueId, components, accessory)
    }

    @Nonnull
    override fun withContentComponents(
        @Nonnull components: Collection<SectionContentComponent>,
    ): Section =
        SectionImpl(
            uniqueId,
            ComponentsUtil.membersToUnion(components, SectionContentComponentUnion::class.java),
            accessory,
        )

    @Nonnull
    override fun withAccessory(
        @Nonnull accessory: SectionAccessoryComponent,
    ): Section =
        SectionImpl(
            uniqueId,
            components,
            ComponentsUtil.safeUnionCast("accessory", accessory, SectionAccessoryComponentUnion::class.java),
        )

    @Nonnull
    override fun replace(
        @Nonnull replacer: ComponentReplacer,
    ): Section {
        Checks.notNull(replacer, "ComponentReplacer")

        val newContent =
            ComponentsUtil.doReplace<List<SectionContentComponentUnion>, SectionContentComponentUnion>(
                SectionContentComponent::class.java,
                components,
                replacer,
            ) { it }

        val newAccessory =
            ComponentsUtil.doReplace<SectionAccessoryComponentUnion?, SectionAccessoryComponentUnion>(
                SectionAccessoryComponent::class.java,
                Collections.singletonList<SectionAccessoryComponentUnion>(accessory),
                replacer,
            ) { newAccessories -> newAccessories.firstOrNull() }

        Checks.notNull(newAccessory, "Accessory")
        return validated(newAccessory!!, newContent, uniqueId)
    }

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getContentComponents(): List<SectionContentComponentUnion> = components

    @Nonnull
    override fun getAccessory(): SectionAccessoryComponentUnion = accessory

    @Nonnull
    override fun toData(): DataObject {
        val json = DataObject.empty()
        json.put("type", type.key)
        json.put("accessory", accessory)
        json.put("components", DataArray.fromCollection(components))
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is SectionImpl) {
            return false
        }
        return uniqueId == other.uniqueId &&
            Objects.equals(components, other.components) &&
            Objects.equals(accessory, other.accessory)
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, components, accessory)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("components", components)
            .toString()

    companion object {
        @JvmStatic
        fun validated(
            accessory: SectionAccessoryComponent,
            components: Collection<SectionContentComponent>,
        ): Section = validated(accessory, components, -1)

        @JvmStatic
        fun validated(
            accessory: SectionAccessoryComponent,
            components: Collection<SectionContentComponent>,
            uniqueId: Int,
        ): Section {
            Checks.notNull(accessory, "Accessory")
            Checks.noneNull(components, "Components")
            Checks.notEmpty(components, "Components")
            Checks.check(
                components.size <= MAX_COMPONENTS,
                "A section can only contain %d components, provided: %d",
                MAX_COMPONENTS,
                components.size,
            )

            // Don't allow unknown components in user-called methods
            val componentUnions = ComponentsUtil.membersToUnion(components, SectionContentComponentUnion::class.java)
            val accessoryUnion =
                ComponentsUtil.safeUnionCast("accessory", accessory, SectionAccessoryComponentUnion::class.java)

            return SectionImpl(uniqueId, componentUnions, accessoryUnion)
        }
    }
}
