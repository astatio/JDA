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

package net.dv8tion.jda.internal.components.container

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.container.ContainerChildComponent
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
import java.util.Objects
import java.util.stream.Collectors
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ContainerImpl :
    AbstractComponentImpl,
    Container,
    MessageTopLevelComponentUnion {
    private val uniqueId: Int
    private val components: List<ContainerChildComponentUnion>
    private val spoiler: Boolean
    private val accentColor: Int?

    constructor(deserializer: ComponentDeserializer, data: DataObject) : this(
        data.getInt("id", -1),
        deserializer
            .deserializeAs(ContainerChildComponentUnion::class.java, data.getArray("components"))
            .collect(Collectors.toList()),
        data.getBoolean("spoiler", false),
        if (data.isNull("accent_color")) null else data.getInt("accent_color"),
    )

    private constructor(components: Collection<ContainerChildComponentUnion>) : this(-1, components, false, null)

    constructor(
        uniqueId: Int,
        components: Collection<ContainerChildComponentUnion>,
        spoiler: Boolean,
        accentColor: Int?,
    ) {
        this.uniqueId = uniqueId
        this.components = Helpers.copyAsUnmodifiableList(components)
        this.spoiler = spoiler
        this.accentColor = accentColor
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.CONTAINER

    @Nonnull
    override fun withUniqueId(uniqueId: Int): ContainerImpl {
        Checks.positive(uniqueId, "Unique ID")
        return ContainerImpl(uniqueId, components, spoiler, accentColor)
    }

    @Nonnull
    override fun withSpoiler(spoiler: Boolean): Container = ContainerImpl(uniqueId, components, spoiler, accentColor)

    @Nonnull
    override fun withAccentColor(
        @Nullable accentColor: Int?,
    ): Container = ContainerImpl(uniqueId, components, spoiler, accentColor)

    @Nonnull
    override fun withComponents(
        @Nonnull components: Collection<ContainerChildComponent>,
    ): Container =
        ContainerImpl(
            uniqueId,
            ComponentsUtil.membersToUnion(components, ContainerChildComponentUnion::class.java),
            spoiler,
            accentColor,
        )

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun replace(
        @Nonnull replacer: ComponentReplacer,
    ): Container {
        Checks.notNull(replacer, "ComponentReplacer")

        return ComponentsUtil.doReplace(
            ContainerChildComponent::class.java,
            components,
            replacer,
        ) { newComponents -> validated(uniqueId, newComponents, spoiler, accentColor) }
    }

    @Nonnull
    override fun getComponents(): List<ContainerChildComponentUnion> = components

    @Nullable
    override fun getAccentColorRaw(): Int? = accentColor

    override fun isSpoiler(): Boolean = spoiler

    @Nonnull
    override fun toData(): DataObject {
        val json =
            DataObject
                .empty()
                .put("type", type.key)
                .put("components", DataArray.fromCollection(components))
                .put("spoiler", spoiler)
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        if (accentColor != null) {
            json.put("accent_color", accentColor and ACCENT_COLOR_MASK)
        }
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ContainerImpl) {
            return false
        }
        return uniqueId == other.uniqueId &&
            spoiler == other.spoiler &&
            Objects.equals(components, other.components) &&
            Objects.equals(accentColor, other.accentColor)
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, components, spoiler, accentColor)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("accentColor", accentColor)
            .addMetadata("spoiler", spoiler)
            .addMetadata("components", components)
            .toString()

    companion object {
        // Discord only accepts a 24-bit RGB value for accent_color.
        private const val ACCENT_COLOR_MASK = 0xFFFFFF

        @JvmStatic
        fun validated(components: Collection<ContainerChildComponent>): Container = validated(-1, components, false, null)

        @JvmStatic
        fun validated(
            uniqueId: Int,
            components: Collection<ContainerChildComponent>,
            spoiler: Boolean,
            accentColor: Int?,
        ): Container {
            Checks.noneNull(components, "Components")
            Checks.notEmpty(components, "Components")

            // Don't allow unknown components in user-called methods
            val componentUnions = ComponentsUtil.membersToUnion(components, ContainerChildComponentUnion::class.java)
            return ContainerImpl(uniqueId, componentUnions, spoiler, accentColor)
        }
    }
}
