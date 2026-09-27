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

package net.dv8tion.jda.internal.interactions

import net.dv8tion.jda.api.interactions.FileType
import net.dv8tion.jda.api.interactions.IFilterableFileTypes
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.internal.utils.Checks
import org.jetbrains.annotations.UnmodifiableView
import java.util.Collections
import java.util.stream.Collectors
import javax.annotation.Nonnull

class FileTypesImpl private constructor(
    private val fileTypes: MutableList<FileType>,
) {
    @Nonnull
    fun copy(): FileTypesImpl = FileTypesImpl(ArrayList(fileTypes))

    @Nonnull
    fun asView(): @UnmodifiableView List<FileType> = Collections.unmodifiableList(fileTypes)

    fun isEmpty(): Boolean = fileTypes.isEmpty()

    fun addAll(
        @Nonnull fileTypes: Collection<FileType>,
    ) {
        Checks.noneNull(fileTypes, "File types")
        Checks.check(
            this.fileTypes.size + fileTypes.size <= IFilterableFileTypes.MAX_FILE_TYPES,
            "Cannot have more than %d file types (provided: %d + %d)",
            IFilterableFileTypes.MAX_FILE_TYPES,
            this.fileTypes.size,
            fileTypes.size,
        )
        this.fileTypes.addAll(fileTypes)
    }

    fun setAll(
        @Nonnull fileTypes: Collection<FileType>,
    ) {
        Checks.noneNull(fileTypes, "File types")
        Checks.check(
            fileTypes.size <= IFilterableFileTypes.MAX_FILE_TYPES,
            "Cannot have more than %d file types (provided: %d)",
            IFilterableFileTypes.MAX_FILE_TYPES,
            fileTypes.size,
        )
        this.fileTypes.clear()
        this.fileTypes.addAll(fileTypes)
    }

    @Nonnull
    fun toData(): DataArray {
        val array = DataArray.empty()
        for (fileType in fileTypes) {
            array.add(fileType.value)
        }
        return array
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is FileTypesImpl) {
            return false
        }
        return fileTypes == other.fileTypes
    }

    override fun hashCode(): Int = fileTypes.hashCode()

    override fun toString(): String = fileTypes.toString()

    companion object {
        @JvmField
        val EMPTY_AND_IMMUTABLE: FileTypesImpl = FileTypesImpl(Collections.emptyList())

        @Nonnull
        @JvmStatic
        fun empty(): FileTypesImpl = FileTypesImpl(ArrayList())

        @Nonnull
        @JvmStatic
        fun fromArray(
            @Nonnull array: DataArray,
        ): FileTypesImpl = FileTypesImpl(array.stream { a, i -> a.getString(i) }.map { FileType(it) }.collect(Collectors.toList()))
    }
}
