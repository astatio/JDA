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

package net.dv8tion.jda.internal.components.attachmentupload

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload
import net.dv8tion.jda.api.components.label.LabelChildComponentUnion
import net.dv8tion.jda.api.interactions.FileType
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.interactions.FileTypesImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull

class AttachmentUploadImpl :
    AbstractComponentImpl,
    AttachmentUpload,
    LabelChildComponentUnion {
    // Java declared these protected, but the class is final and nothing outside it reads
    // them, so protected only widened the surface; detekt's ProtectedMemberInFinalClass
    // flags exactly that.
    private val uniqueId: Int
    private val customId: String
    private val minValues: Int
    private val maxValues: Int
    private val fileTypes: FileTypesImpl
    private val required: Boolean

    constructor(data: DataObject) : this(
        data.getInt("id", -1),
        data.getString("custom_id"),
        data.getInt("min_values", 1),
        data.getInt("max_values", 1),
        data.optArray("file_types").map { FileTypesImpl.fromArray(it) }.orElse(FileTypesImpl.empty()),
        data.getBoolean("required", true),
    )

    constructor(
        uniqueId: Int,
        customId: String,
        minValues: Int,
        maxValues: Int,
        fileTypes: FileTypesImpl,
        required: Boolean,
    ) {
        this.uniqueId = uniqueId
        this.customId = customId
        this.minValues = minValues
        this.maxValues = maxValues
        this.fileTypes = fileTypes.copy()
        this.required = required
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.FILE_UPLOAD

    @Nonnull
    override fun withUniqueId(uniqueId: Int): AttachmentUploadImpl {
        Checks.positive(uniqueId, "Unique ID")
        return AttachmentUploadImpl(uniqueId, customId, minValues, maxValues, fileTypes, required)
    }

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getCustomId(): String = customId

    override fun getMinValues(): Int = minValues

    override fun getMaxValues(): Int = maxValues

    @Nonnull
    override fun getFileTypes(): List<FileType> = fileTypes.asView()

    override fun isRequired(): Boolean = required

    @Nonnull
    override fun toData(): DataObject {
        val json =
            DataObject
                .empty()
                .put("type", type.key)
                .put("custom_id", customId)
                .put("required", required)
                .put("min_values", minValues)
                .put("max_values", maxValues)
                .put("file_types", fileTypes.toData())

        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }

        return json
    }

    override fun toString(): String =
        EntityString(this)
            .addMetadata("custom_id", customId)
            .addMetadata("required", required)
            .addMetadata("file_types", fileTypes)
            .toString()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is AttachmentUploadImpl) {
            return false
        }
        return uniqueId == other.uniqueId &&
            minValues == other.minValues &&
            maxValues == other.maxValues &&
            required == other.required &&
            Objects.equals(customId, other.customId)
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, customId, minValues, maxValues, required)
}
