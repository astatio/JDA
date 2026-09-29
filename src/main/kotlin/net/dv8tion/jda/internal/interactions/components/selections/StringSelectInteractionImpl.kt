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

package net.dv8tion.jda.internal.interactions.components.selections

import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import java.util.Collections
import java.util.stream.Collectors
import javax.annotation.Nonnull

class StringSelectInteractionImpl(
    jda: JDAImpl,
    data: DataObject,
) : SelectMenuInteractionImpl<String, StringSelectMenu>(jda, StringSelectMenu::class.java, data),
    StringSelectInteraction {
    private val values: List<String> = Collections.unmodifiableList(parseValues(data.getObject("data")))

    private fun parseValues(data: DataObject): List<String> =
        data
            .optArray("values")
            .map { arr -> arr.stream { a, i -> a.getString(i) }.collect(Collectors.toList()) }
            .orElse(Collections.emptyList())

    @Nonnull
    override fun getValues(): List<String> = values
}
