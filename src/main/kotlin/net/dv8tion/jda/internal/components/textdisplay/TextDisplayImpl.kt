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

package net.dv8tion.jda.internal.components.textdisplay

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.components.ModalTopLevelComponentUnion
import net.dv8tion.jda.api.components.container.ContainerChildComponentUnion
import net.dv8tion.jda.api.components.section.SectionContentComponentUnion
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull

class TextDisplayImpl :
    AbstractComponentImpl,
    TextDisplay,
    MessageTopLevelComponentUnion,
    ModalTopLevelComponentUnion,
    ContainerChildComponentUnion,
    SectionContentComponentUnion {
    private val uniqueId: Int
    private val content: String

    constructor(data: DataObject) : this(data.getInt("id", -1), data.getString("content"))

    constructor(content: String) : this(-1, content)

    private constructor(uniqueId: Int, content: String) {
        this.content = content
        this.uniqueId = uniqueId
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.TEXT_DISPLAY

    @Nonnull
    override fun withUniqueId(uniqueId: Int): TextDisplayImpl {
        Checks.positive(uniqueId, "Unique ID")
        return TextDisplayImpl(uniqueId, content)
    }

    @Nonnull
    override fun withContent(
        @Nonnull content: String,
    ): TextDisplay {
        Checks.notBlank(content, "Content")
        return TextDisplayImpl(uniqueId, content)
    }

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getContent(): String = content

    @Nonnull
    override fun toData(): DataObject {
        val json = DataObject.empty().put("type", type.key).put("content", content)
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is TextDisplayImpl) {
            return false
        }
        return uniqueId == other.uniqueId && Objects.equals(content, other.content)
    }

    override fun hashCode(): Int = Objects.hash(uniqueId, content)

    override fun toString(): String =
        EntityString(this)
            .addMetadata("id", uniqueId)
            .addMetadata("content", content)
            .toString()
}
