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

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.components.replacer.ComponentReplacer
import net.dv8tion.jda.api.components.tree.ComponentTree
import net.dv8tion.jda.api.components.tree.MessageComponentTree
import net.dv8tion.jda.internal.components.utils.ComponentsUtil
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull

class MessageComponentTreeImpl private constructor(
    components: Collection<MessageTopLevelComponentUnion>,
) : AbstractComponentTree<MessageTopLevelComponentUnion>(components),
    MessageComponentTree {
    @Nonnull
    override fun getType(): ComponentTree.Type = ComponentTree.Type.MESSAGE

    @Nonnull
    override fun replace(
        @Nonnull replacer: ComponentReplacer,
    ): MessageComponentTree {
        Checks.notNull(replacer, "ComponentReplacer")
        return ComponentsUtil.doReplace(
            MessageTopLevelComponent::class.java,
            components,
            replacer,
        ) { newComponents -> MessageComponentTreeImpl(newComponents) }
    }

    @Nonnull
    override fun withDisabled(disabled: Boolean): MessageComponentTree = super.withDisabled(disabled) as MessageComponentTree

    companion object {
        @JvmStatic
        @Nonnull
        fun of(
            @Nonnull components: Collection<MessageTopLevelComponent>,
        ): MessageComponentTree {
            // Empty trees are allowed (messages can contain no components)
            Checks.noneNull(components, "Components")

            // Allow unknown components so [[Message#getComponentTree]] works
            val componentUnions =
                ComponentsUtil.membersToUnionWithUnknownType(components, MessageTopLevelComponentUnion::class.java)
            return MessageComponentTreeImpl(componentUnions)
        }
    }
}
