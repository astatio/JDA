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

package net.dv8tion.jda.internal.components

import net.dv8tion.jda.api.components.ResolvedMedia
import net.dv8tion.jda.api.components.ResolvedMedia.ResolvedMediaFlag
import net.dv8tion.jda.api.entities.ThumbHashPlaceholder
import net.dv8tion.jda.api.utils.AttachmentProxy
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.ThumbHashPlaceholderImpl
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Collections
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ResolvedMediaImpl : ResolvedMedia {
    private val attachmentId: String?
    private val url: String
    private val proxyUrl: String
    private val width: Int
    private val height: Int
    private val contentType: String?
    private val placeholder: ThumbHashPlaceholder?
    private val flags: Int

    constructor(data: DataObject) : this(
        data.getString("attachment_id", null),
        data.getString("url"),
        data.getString("proxy_url"),
        data.getInt("width", 0),
        data.getInt("height", 0),
        data.getString("content_type", null),
        data.getInt("flags", 0),
        ThumbHashPlaceholderImpl.tryFromContainer(data),
    )

    constructor(
        attachmentId: String?,
        url: String,
        proxyUrl: String,
        width: Int,
        height: Int,
        contentType: String?,
        flags: Int,
        placeholder: ThumbHashPlaceholder?,
    ) {
        this.attachmentId = attachmentId
        this.url = url
        this.proxyUrl = proxyUrl
        this.width = width
        this.height = height
        this.contentType = contentType
        this.flags = flags
        this.placeholder = placeholder
    }

    @Nullable
    override fun getAttachmentId(): String? = attachmentId

    @Nonnull
    override fun getUrl(): String = url

    @Nonnull
    override fun getProxyUrl(): String = proxyUrl

    @Nonnull
    override fun getProxy(): AttachmentProxy = AttachmentProxy(if (width > 0 && height > 0) proxyUrl else url)

    override fun getWidth(): Int = width

    override fun getHeight(): Int = height

    @Nullable
    override fun getContentType(): String? = contentType

    @Nullable
    override fun getPlaceholder(): ThumbHashPlaceholder? = placeholder

    override fun getFlagsRaw(): Long = flags.toLong()

    @Nonnull
    override fun getFlags(): Set<ResolvedMediaFlag> = Collections.unmodifiableSet<ResolvedMediaFlag>(ResolvedMediaFlag.fromBitField(flags))

    override fun equals(other: Any?): Boolean {
        if (other !is ResolvedMediaImpl) {
            return false
        }
        return Objects.equals(url, other.url)
    }

    override fun hashCode(): Int = Objects.hash(url)

    override fun toString(): String =
        EntityString(this)
            // url is already shown by the classes containing resolved medias
            .addMetadata("proxy_url", url)
            .toString()
}
