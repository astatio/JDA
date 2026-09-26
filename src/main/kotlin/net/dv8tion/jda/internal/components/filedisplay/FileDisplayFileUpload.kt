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

package net.dv8tion.jda.internal.components.filedisplay

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.components.ResolvedMedia
import net.dv8tion.jda.api.components.container.ContainerChildComponentUnion
import net.dv8tion.jda.api.components.filedisplay.FileDisplay
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

class FileDisplayFileUpload :
    AbstractComponentImpl,
    FileDisplay,
    MessageTopLevelComponentUnion,
    ContainerChildComponentUnion,
    FileContainerMixin {
    private val uniqueId: Int
    private val file: FileUpload
    private val spoiler: Boolean

    constructor(file: FileUpload) : this(-1, file, false)

    constructor(uniqueId: Int, file: FileUpload, spoiler: Boolean) {
        this.uniqueId = uniqueId
        this.file = file
        this.spoiler = spoiler
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.FILE_DISPLAY

    @Nonnull
    override fun withUniqueId(uniqueId: Int): FileDisplayFileUpload {
        Checks.positive(uniqueId, "Unique ID")
        return FileDisplayFileUpload(uniqueId, file, spoiler)
    }

    @Nonnull
    override fun withSpoiler(spoiler: Boolean): FileDisplay = FileDisplayFileUpload(uniqueId, file, spoiler)

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getUrl(): String = "attachment://" + file.name

    override fun getFiles(): Stream<FileUpload> = Stream.of(file)

    @Nullable
    override fun getResolvedMedia(): ResolvedMedia? = null

    override fun isSpoiler(): Boolean = spoiler

    @Nonnull
    override fun toData(): DataObject {
        val json =
            DataObject
                .empty()
                .put("type", type.key)
                .put("file", DataObject.empty().put("url", url))
                .put("spoiler", spoiler)
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is FileDisplayFileUpload) {
            return false
        }
        return uniqueId == other.uniqueId &&
            spoiler == other.spoiler &&
            Objects.equals(file, other.file)
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, file, spoiler)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("file", file)
            .addMetadata("spoiler", spoiler)
            .toString()
}
