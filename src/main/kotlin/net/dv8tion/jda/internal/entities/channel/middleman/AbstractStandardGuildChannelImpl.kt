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

package net.dv8tion.jda.internal.entities.channel.middleman

import gnu.trove.map.TLongObjectMap
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.StandardGuildChannelMixin

abstract class AbstractStandardGuildChannelImpl<T : AbstractStandardGuildChannelImpl<T>>(
    id: Long,
    guild: Guild,
) : AbstractGuildChannelImpl<T>(id, guild),
    StandardGuildChannelMixin<T> {
    @JvmField
    protected val overrides: TLongObjectMap<PermissionOverride> = MiscUtil.newLongMap()

    @JvmField
    protected var parentCategoryId: Long = 0

    @JvmField
    protected var position: Int = 0

    override fun getParentCategoryIdLong(): Long = parentCategoryId

    override fun getPositionRaw(): Int = position

    override val permissionOverrideMap: TLongObjectMap<PermissionOverride>
        get() = overrides

    @Suppress("UNCHECKED_CAST")
    override fun setParentCategory(parentCategoryId: Long): T {
        this.parentCategoryId = parentCategoryId
        return this as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun setPosition(position: Int): T {
        onPositionChange()
        this.position = position
        return this as T
    }

    protected fun onPositionChange() {
        if (!isDetached) {
            (guild as GuildImpl).channelView.clearCachedLists()
        }
    }
}
