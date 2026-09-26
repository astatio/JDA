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

package net.dv8tion.jda.internal.components.replacer

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.replacer.ComponentReplacer
import java.util.function.Function
import java.util.function.Predicate
import javax.annotation.Nonnull
import javax.annotation.Nullable

class TypedComponentReplacerImpl<T : Component>(
    private val type: Class<in T>,
    private val filter: Predicate<in T>,
    private val updater: Function<in T, out Component>,
) : ComponentReplacer {
    @Nullable
    override fun apply(
        @Nonnull oldComponent: Component,
    ): Component? {
        if (!type.isInstance(oldComponent)) {
            return oldComponent
        }

        @Suppress("UNCHECKED_CAST")
        val typed = oldComponent as T

        return if (filter.test(typed)) updater.apply(typed) else oldComponent
    }
}
