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

package net.dv8tion.jda.internal.components.mediagallery

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.components.container.ContainerChildComponentUnion
import net.dv8tion.jda.api.components.mediagallery.MediaGallery
import net.dv8tion.jda.api.components.mediagallery.MediaGallery.MAX_ITEMS
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.entities.FileContainerMixin
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Objects
import java.util.stream.Stream
import javax.annotation.Nonnull

class MediaGalleryImpl :
    AbstractComponentImpl,
    MediaGallery,
    MessageTopLevelComponentUnion,
    ContainerChildComponentUnion,
    FileContainerMixin {
    private val uniqueId: Int
    private val items: List<MediaGalleryItem>

    private constructor(items: Collection<MediaGalleryItem>) : this(-1, items)

    constructor(uniqueId: Int, items: Collection<MediaGalleryItem>) {
        this.uniqueId = uniqueId
        this.items = Helpers.copyAsUnmodifiableList(items)
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.MEDIA_GALLERY

    @Nonnull
    override fun withUniqueId(uniqueId: Int): MediaGalleryImpl {
        Checks.positive(uniqueId, "Unique ID")
        return MediaGalleryImpl(uniqueId, items)
    }

    @Nonnull
    override fun withItems(
        @Nonnull items: Collection<MediaGalleryItem>,
    ): MediaGalleryImpl {
        Checks.noneNull(items, "Items")
        return MediaGalleryImpl(uniqueId, items)
    }

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getItems(): List<MediaGalleryItem> = items

    override fun getFiles(): Stream<FileUpload> =
        items
            .stream()
            .filter { FileContainerMixin::class.java.isInstance(it) }
            .map { FileContainerMixin::class.java.cast(it) }
            .flatMap { it.files }

    @Nonnull
    override fun toData(): DataObject {
        val json =
            DataObject.empty().put("type", type.key).put("items", DataArray.fromCollection(items))
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is MediaGalleryImpl) {
            return false
        }
        return uniqueId == other.uniqueId && Objects.equals(items, other.items)
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, items)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("items", items)
            .toString()

    companion object {
        @Nonnull
        @JvmStatic
        fun validated(
            @Nonnull items: Collection<MediaGalleryItem>,
        ): MediaGallery {
            Checks.noneNull(items, "Items")
            Checks.notEmpty(items, "Items")
            Checks.check(
                items.size <= MAX_ITEMS,
                "A media gallery can only contain %d items, provided: %d",
                MAX_ITEMS,
                items.size,
            )
            return MediaGalleryImpl(items)
        }
    }
}
