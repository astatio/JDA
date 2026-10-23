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
class ThumbnailImpl :
    AbstractComponentImpl,
    Thumbnail,
    SectionAccessoryComponentUnion,
    FileContainerMixin {
    private val uniqueId: Int
    private val url: String
    private val media: ResolvedMedia?
    private val description: String?
    private val spoiler: Boolean

    constructor(data: DataObject) : this(
        data.getInt("id", -1),
        data.getObject("media").getString("url"),
        if (data.getObject("media").isNull("proxy_url")) null else ResolvedMediaImpl(data.getObject("media")),
        data.getString("description", null),
        data.getBoolean("spoiler", false),
    )

    constructor(url: String) : this(-1, url, null, null, false)

    private constructor(uniqueId: Int, url: String, media: ResolvedMedia?, description: String?, spoiler: Boolean) {
        this.uniqueId = uniqueId
        this.url = url
        this.media = media
        this.description = description
        this.spoiler = spoiler
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.THUMBNAIL

    @Nonnull
    override fun withUniqueId(uniqueId: Int): ThumbnailImpl {
        Checks.positive(uniqueId, "Unique ID")
        return ThumbnailImpl(uniqueId, url, media, description, spoiler)
    }

    @Nonnull
    override fun withDescription(
        @Nullable description: String?,
    ): Thumbnail {
        if (description != null) {
            Checks.notBlank(description, "Description")
            Checks.notLonger(description, MAX_DESCRIPTION_LENGTH, "Description")
        }
        return ThumbnailImpl(uniqueId, url, media, description, spoiler)
    }

    @Nonnull
    override fun withSpoiler(spoiler: Boolean): Thumbnail = ThumbnailImpl(uniqueId, url, media, description, spoiler)

    override fun getUniqueId(): Int = uniqueId

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
        val json =
            DataObject
                .empty()
                .put("type", type.key)
                .put("media", DataObject.empty().put("url", outputUrl))
                .put("spoiler", spoiler)
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        if (description != null) {
            json.put("description", description)
        }
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ThumbnailImpl) {
            return false
        }
        return uniqueId == other.uniqueId &&
            spoiler == other.spoiler &&
            Objects.equals(url, other.url) &&
            Objects.equals(description, other.description)
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, url, description, spoiler)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("url", url)
            .addMetadata("media", media)
            .addMetadata("spoiler", spoiler)
            .addMetadata("description", description)
            .toString()
}
