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

package net.dv8tion.jda.internal.components.thumbnail

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.ResolvedMedia
import net.dv8tion.jda.api.components.section.SectionAccessoryComponentUnion
import net.dv8tion.jda.api.components.thumbnail.Thumbnail
import net.dv8tion.jda.api.components.thumbnail.Thumbnail.MAX_DESCRIPTION_LENGTH
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.entities.FileContainerMixin
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import java.util.stream.Stream
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ThumbnailFileUpload :
    AbstractComponentImpl,
    Thumbnail,
    SectionAccessoryComponentUnion,
    FileContainerMixin {
    private val uniqueId: Int
    private val file: FileUpload
    private val description: String?
    private val spoiler: Boolean

    constructor(file: FileUpload) : this(-1, file, null, false)

    constructor(uniqueId: Int, file: FileUpload, description: String?, spoiler: Boolean) {
        this.uniqueId = uniqueId
        this.file = file
        this.description = description
        this.spoiler = spoiler
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.THUMBNAIL

    @Nonnull
    override fun withUniqueId(uniqueId: Int): ThumbnailFileUpload {
        Checks.positive(uniqueId, "Unique ID")
        return ThumbnailFileUpload(uniqueId, file, description, spoiler)
    }

    @Nonnull
    override fun withDescription(
        @Nullable description: String?,
    ): Thumbnail {
        if (description != null) {
            Checks.notBlank(description, "Description")
            Checks.notLonger(description, MAX_DESCRIPTION_LENGTH, "Description")
        }
        return ThumbnailFileUpload(uniqueId, file, description, spoiler)
    }

    @Nonnull
    override fun withSpoiler(spoiler: Boolean): Thumbnail = ThumbnailFileUpload(uniqueId, file, description, spoiler)

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getUrl(): String = "attachment://" + file.name

    override fun getFiles(): Stream<FileUpload> = Stream.of(file)

    @Nullable
    override fun getResolvedMedia(): ResolvedMedia? = null

    @Nullable
    override fun getDescription(): String? = description ?: file.description

    override fun isSpoiler(): Boolean = spoiler

    @Nonnull
    override fun toData(): DataObject {
        val json =
            DataObject
                .empty()
                .put("type", type.key)
                .put("media", DataObject.empty().put("url", url))
                .put("spoiler", spoiler)
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        if (getDescription() != null) {
            json.put("description", getDescription())
        }
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ThumbnailFileUpload) {
            return false
        }
        return uniqueId == other.uniqueId &&
            spoiler == other.spoiler &&
            Objects.equals(file, other.file) &&
            Objects.equals(description, other.description)
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, file, description, spoiler)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("file", file)
            .addMetadata("spoiler", spoiler)
            .addMetadata("description", description)
            .toString()
}
