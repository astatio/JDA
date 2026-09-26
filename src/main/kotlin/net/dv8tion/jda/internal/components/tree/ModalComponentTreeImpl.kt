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

import net.dv8tion.jda.api.components.ModalTopLevelComponent
import net.dv8tion.jda.api.components.ModalTopLevelComponentUnion
import net.dv8tion.jda.api.components.replacer.ComponentReplacer
import net.dv8tion.jda.api.components.tree.ComponentTree
import net.dv8tion.jda.api.components.tree.ModalComponentTree
import net.dv8tion.jda.internal.components.utils.ComponentsUtil
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull

class ModalComponentTreeImpl private constructor(
    components: Collection<ModalTopLevelComponentUnion>,
) : AbstractComponentTree<ModalTopLevelComponentUnion>(components),
    ModalComponentTree {
    @Nonnull
    override fun getType(): ComponentTree.Type = ComponentTree.Type.MODAL

    @Nonnull
    override fun replace(
        @Nonnull replacer: ComponentReplacer,
    ): ModalComponentTree {
        Checks.notNull(replacer, "ComponentReplacer")
        return ComponentsUtil.doReplace(
            ModalTopLevelComponent::class.java,
            components,
            replacer,
        ) { newComponents -> ModalComponentTreeImpl(newComponents) }
    }

    @Nonnull
    override fun withDisabled(disabled: Boolean): ModalComponentTree = super.withDisabled(disabled) as ModalComponentTree

    companion object {
        @JvmStatic
        @Nonnull
        fun of(
            @Nonnull components: Collection<ModalTopLevelComponent>,
        ): ModalComponentTree {
            Checks.notEmpty(components, "Components")
            Checks.noneNull(components, "Components")

            // Allow unknown components so [[Modal#getComponentTree]] works
            val componentUnions =
                ComponentsUtil.membersToUnionWithUnknownType(components, ModalTopLevelComponentUnion::class.java)
            return ModalComponentTreeImpl(componentUnions)
        }
    }
}
