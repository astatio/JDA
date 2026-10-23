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

package net.dv8tion.jda.internal.modals

import net.dv8tion.jda.api.components.ModalTopLevelComponentUnion
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Collections
import javax.annotation.Nonnull

class ModalImpl : Modal {
    private val id: String
    private val title: String
    private val components: List<ModalTopLevelComponentUnion>

    constructor(objectData: DataObject) {
        id = objectData.getString("custom_id")
        title = objectData.getString("title")
        components =
            objectData
                .optArray("components")
                .map { arr ->
                    EntityBuilder.DEFAULT_COMPONENT_DESERIALIZER
                        .deserializeAs(ModalTopLevelComponentUnion::class.java, arr)
                        .collect(Helpers.toUnmodifiableList())
                }.orElseGet { Collections.emptyList() }
    }

    constructor(
        id: String,
        title: String,
        components: @JvmSuppressWildcards List<ModalTopLevelComponentUnion>,
    ) {
        this.id = id
        this.title = title
        this.components = Collections.unmodifiableList(components)
    }

    @Nonnull
    override fun getId(): String = id

    @Nonnull
    override fun getTitle(): String = title

    @Nonnull
    override fun getComponents(): List<ModalTopLevelComponentUnion> = components

    @Nonnull
    override fun toData(): DataObject {
        val objectData = DataObject.empty().put("custom_id", id).put("title", title)

        objectData.put(
            "components",
            DataArray.fromCollection(
                components
                    .map { it as AbstractComponentImpl }
                    .map(AbstractComponentImpl::toData),
            ),
        )
        return objectData
    }

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", id)
            .addMetadata("title", title)
            .toString()
}
