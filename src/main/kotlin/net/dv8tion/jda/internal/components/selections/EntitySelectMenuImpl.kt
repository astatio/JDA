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
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Collections
import java.util.EnumSet
import java.util.Objects
import java.util.stream.Collectors
import javax.annotation.Nonnull

class EntitySelectMenuImpl :
    SelectMenuImpl,
    EntitySelectMenu,
    LabelChildComponentUnion {
    // Java declared these protected, but the class is final and nothing outside it reads
    // them, so protected only widened the surface. detekt's ProtectedMemberInFinalClass
    // flags exactly that; private keeps the fields and their initializers unchanged.
    private val type: Component.Type
    private val channelTypes: EnumSet<ChannelType>
    private val defaultValues: List<EntitySelectMenu.DefaultValue>

    constructor(data: DataObject) : super(data) {
        this.type = Component.Type.fromKey(data.getInt("type"))
        this.channelTypes =
            Helpers.copyEnumSet(
                ChannelType::class.java,
                data
                    .optArray("channel_types")
                    .map { arr ->
                        arr
                            .stream { a, i -> a.getInt(i) }
                            .map { ChannelType.fromId(it) }
                            .collect(Collectors.toList())
                    }.orElse(null),
            )
        this.defaultValues =
            data
                .optArray("default_values")
                .map { array ->
                    array
                        .stream { a, i -> a.getObject(i) }
                        .map { DefaultValue.fromData(it) }
                        .collect(Helpers.toUnmodifiableList())
                }.orElse(Collections.emptyList())
    }

    constructor(
        id: String,
        uniqueId: Int,
        placeholder: String?,
        minValues: Int,
        maxValues: Int,
        disabled: Boolean,
        type: Component.Type,
        channelTypes: EnumSet<ChannelType>,
        defaultValues: @JvmSuppressWildcards List<DefaultValue>,
        required: Boolean?,
    ) : super(id, uniqueId, placeholder, minValues, maxValues, disabled, required) {
        this.type = type
        this.channelTypes = channelTypes
        this.defaultValues = defaultValues
    }

    @Nonnull
    override fun getType(): Component.Type = type

    @Nonnull
    override fun withUniqueId(uniqueId: Int): EntitySelectMenuImpl = createCopy().setUniqueId(uniqueId).build() as EntitySelectMenuImpl

    @Nonnull
    override fun getEntityTypes(): EnumSet<SelectTarget> =
        when (type) {
            Component.Type.ROLE_SELECT -> EnumSet.of(SelectTarget.ROLE)
            Component.Type.USER_SELECT -> EnumSet.of(SelectTarget.USER)
            Component.Type.CHANNEL_SELECT -> EnumSet.of(SelectTarget.CHANNEL)
            Component.Type.MENTIONABLE_SELECT -> EnumSet.of(SelectTarget.ROLE, SelectTarget.USER)
            // Ideally this never happens, so its undocumented
            else -> throw IllegalStateException("Unsupported type: $type")
        }

    @Nonnull
    override fun getChannelTypes(): EnumSet<ChannelType> = channelTypes

    @Nonnull
    override fun getDefaultValues(): List<DefaultValue> = defaultValues

    @Nonnull
    override fun toData(): DataObject {
        val json = super.toData().put("type", type.key)
        if (type == Component.Type.CHANNEL_SELECT && channelTypes.isNotEmpty()) {
            json.put(
                "channel_types",
                DataArray.fromCollection(
                    channelTypes.stream().map { it.id }.collect(Collectors.toList()),
                ),
            )
        }
        if (defaultValues.isNotEmpty()) {
            json.put("default_values", DataArray.fromCollection(defaultValues))
        }
        return json
    }

    override fun hashCode(): Int = Objects.hash(id, placeholder, minValues, maxValues, disabled, type, channelTypes, defaultValues)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is EntitySelectMenu) {
            return false
        }
        return id == other.customId &&
            placeholder == other.placeholder &&
            minValues == other.minValues &&
            maxValues == other.maxValues &&
            disabled == other.isDisabled &&
            type == other.type &&
            channelTypes == other.channelTypes &&
            defaultValues == other.defaultValues
    }
}
