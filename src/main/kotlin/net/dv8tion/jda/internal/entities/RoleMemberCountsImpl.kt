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

package net.dv8tion.jda.internal.entities

import gnu.trove.map.TLongIntMap
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.RoleMemberCount
import net.dv8tion.jda.api.entities.RoleMemberCounts
import org.jetbrains.annotations.Unmodifiable
import java.util.Collections
import javax.annotation.Nonnull

class RoleMemberCountsImpl(
    private val guild: Guild,
    private val roleMemberCounts: TLongIntMap,
) : RoleMemberCounts {
    override fun get(roleId: Long): Int = roleMemberCounts.get(roleId) // Default value is 0

    override fun contains(roleId: Long): Boolean = roleMemberCounts.containsKey(roleId)

    @Nonnull
    override fun asList(): @Unmodifiable List<RoleMemberCount> {
        val map: MutableList<RoleMemberCount> = ArrayList(roleMemberCounts.size())
        roleMemberCounts.forEachEntry { roleId, count ->
            map.add(RoleMemberCountImpl(guild, roleId, count))
            true
        }
        return Collections.unmodifiableList(map)
    }
}
