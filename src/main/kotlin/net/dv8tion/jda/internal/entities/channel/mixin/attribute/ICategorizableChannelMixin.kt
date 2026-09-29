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

package net.dv8tion.jda.internal.entities.channel.mixin.attribute

import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.GuildChannelMixin

interface ICategorizableChannelMixin<T : ICategorizableChannelMixin<T>> :
    ICategorizableChannel,
    GuildChannelMixin<T>,
    IPermissionContainerMixin<T> {
    // ---- Default implementations of interface ----
    @Suppress("ReturnCount") // ported verbatim from the Java original; matching its early-return control flow
    override fun isSynced(): Boolean {
        val parent = parentCategory as IPermissionContainerMixin<*>?
        if (parent == null) {
            // Channels without a parent category are always considered synced.
            // Also the case for categories.
            return true
        }
        val parentOverrides = parent.permissionOverrideMap
        val overrides = permissionOverrideMap
        if (parentOverrides.size() != overrides.size()) {
            return false
        }

        // Check that each override matches with the parent override
        for (parentOverride in parentOverrides.valueCollection()) {
            val ourOverride: PermissionOverride? = overrides.get(parentOverride.idLong)
            // this means we don't have the parent override => not synced
            if (ourOverride == null) {
                return false
            }
            // Permissions are different => not synced
            if (ourOverride.allowedRaw != parentOverride.allowedRaw ||
                ourOverride.deniedRaw != parentOverride.deniedRaw
            ) {
                return false
            }
        }

        // All overrides exist and are the same as the parent => synced
        return true
    }

    // ---- State Accessors ----
    fun setParentCategory(parentCategoryId: Long): T
}
