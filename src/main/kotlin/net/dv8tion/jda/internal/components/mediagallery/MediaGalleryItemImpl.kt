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
import net.dv8tion.jda.internal.components.ResolvedMediaImpl
import net.dv8tion.jda.internal.components.utils.ComponentsUtil
import net.dv8tion.jda.internal.entities.FileContainerMixin
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import java.util.stream.Stream
import javax.annotation.Nonnull
import javax.annotation.Nullable

/** Represents either an external link, an attachment:// link, or an existing item (which is also a link) */
class MediaGalleryItemImpl :
    MediaGalleryItem,
    FileContainerMixin,
    SerializableData {
    private val url: String
    private val description: String?
    private val media: ResolvedMedia?
    private val spoiler: Boolean

    constructor(obj: DataObject) : this(
        obj.getObject("media").getString("url"),
        obj.getString("description", null),
        if (obj.getObject("media").isNull("proxy_url")) null else ResolvedMediaImpl(obj.getObject("media")),
        obj.getBoolean("spoiler", false),
    )

    constructor(url: String) : this(url, null, null, false)

    constructor(url: String, description: String?, media: ResolvedMedia?, spoiler: Boolean) {
        this.url = url
        this.media = media
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
        return MediaGalleryItemImpl(url, description, media, spoiler)
    }

    @Nonnull
    override fun withSpoiler(spoiler: Boolean): MediaGalleryItem = MediaGalleryItemImpl(url, description, media, spoiler)

    @Nonnull
    override fun getUrl(): String = url

    @Nullable
    override fun getResolvedMedia(): ResolvedMedia? = media

    override fun getFiles(): Stream<FileUpload> = ComponentsUtil.getFilesFromMedia(media)

    @Nullable
    override fun getDescription(): String? = description

    override fun isSpoiler(): Boolean = spoiler

    @Nonnull
    override fun toData(): DataObject {
        val outputUrl = ComponentsUtil.getMediaUrl(media, url)
        return DataObject
            .empty()
            .put("media", DataObject.empty().put("url", outputUrl))
            .put("description", description)
            .put("spoiler", spoiler)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is MediaGalleryItemImpl) {
            return false
        }
        return spoiler == other.spoiler &&
            Objects.equals(url, other.url) &&
            Objects.equals(description, other.description)
    }

    override fun hashCode(): Int = Objects.hash(url, description, spoiler)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("url", url)
            .addMetadata("media", media)
            .addMetadata("spoiler", spoiler)
            .addMetadata("description", description)
            .toString()
}
