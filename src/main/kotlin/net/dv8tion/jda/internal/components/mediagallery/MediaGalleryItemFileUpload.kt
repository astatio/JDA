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

import net.dv8tion.jda.api.components.ResolvedMedia
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem.MAX_DESCRIPTION_LENGTH
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.data.SerializableData
import net.dv8tion.jda.internal.entities.FileContainerMixin
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import java.util.stream.Stream
import javax.annotation.Nonnull
import javax.annotation.Nullable

class MediaGalleryItemFileUpload :
    MediaGalleryItem,
    FileContainerMixin,
    SerializableData {
    private val file: FileUpload // Contains name and description
    private val description: String?
    private val spoiler: Boolean

    constructor(upload: FileUpload) : this(upload, null, false)

    constructor(file: FileUpload, description: String?, spoiler: Boolean) {
        this.file = file
        this.description = description
        this.spoiler = spoiler
    }

    @Nonnull
    override fun withDescription(
        @Nullable description: String?,
    ): MediaGalleryItem {
        if (description != null) {
            Checks.notBlank(description, "Description")
            Checks.notLonger(description, MAX_DESCRIPTION_LENGTH, "Description")
        }
        return MediaGalleryItemFileUpload(file, description, spoiler)
    }

    @Nonnull
    override fun withSpoiler(spoiler: Boolean): MediaGalleryItem = MediaGalleryItemFileUpload(file, description, spoiler)

    @Nonnull
    override fun getUrl(): String =
        // FileUpload is mutable unfortunately
        "attachment://" + file.name

    @Nullable
    override fun getResolvedMedia(): ResolvedMedia? = null

    override fun getFiles(): Stream<FileUpload> = Stream.of(file)

    @Nullable
    override fun getDescription(): String? =
        if (description != null) {
            description
        } else {
            file.description // FileUpload is mutable
        }

    override fun isSpoiler(): Boolean = spoiler

    @Nonnull
    override fun toData(): DataObject =
        DataObject
            .empty()
            .put("media", DataObject.empty().put("url", url))
            .put("description", getDescription())
            .put("spoiler", isSpoiler)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is MediaGalleryItemFileUpload) {
            return false
        }
        return spoiler == other.spoiler &&
            Objects.equals(file, other.file) &&
            Objects.equals(description, other.description)
    }

    override fun hashCode(): Int = Objects.hash(file, description, spoiler)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("file", file)
            .addMetadata("spoiler", spoiler)
            .addMetadata("description", description)
            .toString()
}
