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

package net.dv8tion.jda.internal.components.utils

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.IComponentUnion
import net.dv8tion.jda.api.components.ResolvedMedia
import net.dv8tion.jda.api.components.UnknownComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.replacer.ComponentReplacer
import net.dv8tion.jda.api.components.replacer.IReplaceable
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.utils.ComponentIterator
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import java.util.ArrayList
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.annotation.Nonnull
import javax.annotation.Nullable

object ComponentsUtil {
    /**
     * Checks the component has the target union type, and isn't an [UnknownComponent],
     * throws [IllegalArgumentException] otherwise.
     */
    @JvmStatic
    fun <T : IComponentUnion> safeUnionCast(
        componentCategory: String,
        component: Component,
        toUnionClass: Class<T>,
    ): T {
        if (toUnionClass.isInstance(component)) {
            val union = toUnionClass.cast(component)
            Checks.check(!union.isUnknownComponent, "Cannot provide UnknownComponent")
            return union
        }

        val cleanedClassName = component.javaClass.simpleName.replace("Impl", "")
        throw IllegalArgumentException(
            Helpers.format(
                "Cannot convert %s of type %s to %s!",
                componentCategory,
                cleanedClassName,
                toUnionClass.simpleName,
            ),
        )
    }

    /**
     * Checks the component has the target union type, and allows unknown components,
     * throws [IllegalArgumentException] otherwise.
     *
     * This should only be used for reading purposes,
     * use [membersToUnion] to verify components to be sent.
     */
    @JvmStatic
    fun <T : Component> safeUnionCastWithUnknownType(
        componentCategory: String,
        component: Component,
        toUnionClass: Class<T>,
    ): T {
        if (toUnionClass.isInstance(component)) {
            return toUnionClass.cast(component)
        }

        val cleanedClassName = component.javaClass.simpleName.replace("Impl", "")
        throw IllegalArgumentException(
            Helpers.format(
                "Cannot convert %s of type %s to %s!",
                componentCategory,
                cleanedClassName,
                toUnionClass.simpleName,
            ),
        )
    }

    /**
     * Checks all the components has the target union type, and isn't an [UnknownComponent],
     * throws [IllegalArgumentException] otherwise.
     */
    @JvmStatic
    fun <TUnion : IComponentUnion> membersToUnion(
        members: Collection<Component>,
        clazz: Class<TUnion>,
    ): List<TUnion> = members.map { safeUnionCast("component", it, clazz) }

    /**
     * Retains all components extending the provided union type, keeps unknown components,
     * throws [IllegalArgumentException] on invalid types.
     *
     * This should only be used for reading purposes,
     * use [membersToUnion] to verify components to be sent.
     */
    @JvmStatic
    fun <T : Component> membersToUnionWithUnknownType(
        members: Collection<Component>,
        clazz: Class<T>,
    ): List<T> = members.map { safeUnionCastWithUnknownType("component", it, clazz) }

    @JvmStatic
    fun <R, E : Component> doReplace(
        // This isn't '? extends E' as users are not required to return unions
        expectedChildrenType: Class<out Component>,
        children: Iterable<E>,
        replacer: ComponentReplacer,
        finisher: Function<List<E>, R>,
    ): R {
        val newComponents = ArrayList<E>()
        for (component in children) {
            var newComponent: Component? = replacer.apply(component)
            if (newComponent == null) {
                continue
            }
            // If it returned a different component, then use it and don't try to recurse
            if (newComponent !== component) {
                Checks.checkComponentType(expectedChildrenType, component, newComponent)
            } else if (component is IReplaceable) {
                newComponent = component.replace(replacer)
                Checks.checkComponentType(expectedChildrenType, component, newComponent)
            }
            @Suppress("UNCHECKED_CAST")
            newComponents.add(newComponent as E)
        }

        return finisher.apply(newComponents)
    }

    @JvmStatic
    fun getComponentTreeSize(
        @Nonnull tree: Collection<Component>,
    ): Long = ComponentIterator.createStream(tree).count()

    @JvmStatic
    @Nonnull
    fun getIllegalV1Components(
        @Nonnull components: Collection<Component>,
    ): List<Component> = components.stream().filter { it !is ActionRow }.collect(Collectors.toList())

    @JvmStatic
    fun hasIllegalV1Components(
        @Nonnull components: Collection<Component>,
    ): Boolean = getIllegalV1Components(components).isNotEmpty()

    @JvmStatic
    fun getComponentTreeTextContentLength(
        @Nonnull components: Collection<Component>,
    ): Long =
        ComponentIterator
            .createStream(components)
            .mapToInt { if (it is TextDisplay) it.content.length else 0 }
            .sum()
            .toLong()

    @JvmStatic
    fun getFilesFromMedia(
        @Nullable media: ResolvedMedia?,
    ): Stream<FileUpload> {
        if (media != null && media.attachmentId != null) {
            // Retain or reupload the entire file
            val fileName = Helpers.getLastPathSegment(media.url)
            return Stream.of(media.proxy.downloadAsFileUpload(fileName))
        } else {
            // External URL or user-managed attachment
            return Stream.empty()
        }
    }

    @JvmStatic
    fun getMediaUrl(
        @Nullable media: ResolvedMedia?,
        @Nullable url: String?,
    ): String? =
        if (media != null && media.attachmentId != null) {
            // Retain or reupload the entire file, both cases uses attachment://
            "attachment://" + Helpers.getLastPathSegment(media.url)
        } else {
            // User-managed attachment
            url
        }
}
