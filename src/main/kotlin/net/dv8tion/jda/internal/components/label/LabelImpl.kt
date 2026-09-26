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

package net.dv8tion.jda.internal.components.label

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.ModalTopLevelComponentUnion
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.label.Label.DESCRIPTION_MAX_LENGTH
import net.dv8tion.jda.api.components.label.Label.LABEL_MAX_LENGTH
import net.dv8tion.jda.api.components.label.LabelChildComponent
import net.dv8tion.jda.api.components.label.LabelChildComponentUnion
import net.dv8tion.jda.api.components.replacer.ComponentReplacer
import net.dv8tion.jda.api.components.utils.ComponentDeserializer
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.components.utils.ComponentsUtil
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Collections
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class LabelImpl :
    AbstractComponentImpl,
    Label,
    ModalTopLevelComponentUnion {
    private val uniqueId: Int
    private val label: String
    private val description: String?
    private val child: LabelChildComponentUnion

    constructor(
        @Nonnull deserializer: ComponentDeserializer,
        @Nonnull obj: DataObject,
    ) : this(
        obj.getInt("id", -1),
        obj.getString("label"),
        obj.getString("description", null),
        deserializer.deserializeAs(LabelChildComponentUnion::class.java, obj.getObject("component")),
    )

    constructor(
        @Nonnull label: String,
        @Nullable description: String?,
        @Nonnull child: LabelChildComponentUnion,
    ) : this(-1, label, description, child)

    private constructor(
        uniqueId: Int,
        @Nonnull label: String,
        @Nullable description: String?,
        @Nonnull child: LabelChildComponentUnion,
    ) {
        this.uniqueId = uniqueId
        this.label = label
        this.description = description
        this.child = child
    }

    @Nonnull
    override fun withLabel(
        @Nonnull label: String,
    ): Label = validated(label, description, child)

    @Nonnull
    override fun withDescription(
        @Nullable description: String?,
    ): Label = validated(label, description, child)

    @Nonnull
    override fun withChild(
        @Nonnull child: LabelChildComponent,
    ): Label = validated(label, description, child)

    @Nonnull
    override fun withUniqueId(uniqueId: Int): LabelImpl = LabelImpl(uniqueId, label, description, child)

    @Nonnull
    override fun getType(): Component.Type = Component.Type.LABEL

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getLabel(): String = label

    @Nullable
    override fun getDescription(): String? = description

    @Nonnull
    override fun getChild(): LabelChildComponentUnion = child

    @Nonnull
    override fun toData(): DataObject {
        val obj =
            DataObject
                .empty()
                .put("type", type.key)
                .put("label", label)
                .put("description", description)
                .put("component", child)
        if (uniqueId >= 0) {
            obj.put("id", uniqueId)
        }

        return obj
    }

    @Nonnull
    override fun replace(
        @Nonnull replacer: ComponentReplacer,
    ): Label {
        Checks.notNull(replacer, "ComponentReplacer")

        val newChild =
            ComponentsUtil.doReplace<LabelChildComponentUnion?, LabelChildComponentUnion>(
                LabelChildComponent::class.java,
                Collections.singletonList(child),
                replacer,
            ) { newChildren -> newChildren.firstOrNull() }

        Checks.notNull(newChild, "Child")
        return validated(label, description, newChild!!)
    }

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("label", label)
            .toString()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is LabelImpl) {
            return false
        }
        return uniqueId == other.uniqueId &&
            Objects.equals(label, other.label) &&
            Objects.equals(description, other.description) &&
            Objects.equals(child, other.child)
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, label, description, child)

    companion object {
        @JvmStatic
        fun validated(
            @Nonnull label: String,
            @Nullable description: String?,
            @Nonnull child: LabelChildComponent,
        ): Label {
            Checks.notBlank(label, "Label")
            Checks.notLonger(label, LABEL_MAX_LENGTH, "Label")
            Checks.notNull(child, "Child")
            if (description != null) {
                Checks.notBlank(description, "Description")
                Checks.notLonger(description, DESCRIPTION_MAX_LENGTH, "Description")
            }

            val childUnion = ComponentsUtil.safeUnionCast("child", child, LabelChildComponentUnion::class.java)
            return LabelImpl(label, description, childUnion)
        }
    }
}
