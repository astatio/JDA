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

package net.dv8tion.jda.internal.components.tree

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.attribute.IDisableable
import net.dv8tion.jda.api.components.replacer.ComponentReplacer
import net.dv8tion.jda.api.components.tree.ComponentTree
import net.dv8tion.jda.internal.utils.Helpers
import javax.annotation.Nonnull

abstract class AbstractComponentTree<E : Component>(
    components: Collection<E>,
) : ComponentTree<E> {
    @JvmField
    protected val components: List<E> = Helpers.copyAsUnmodifiableList(components)

    @Nonnull
    override fun getComponents(): List<E> = components

    @Nonnull
    override fun withDisabled(disabled: Boolean): ComponentTree<E> =
        replace(ComponentReplacer.of(IDisableable::class.java, { true }, { c -> c.withDisabled(disabled) }))
}
