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

package net.dv8tion.jda.internal.utils.cache

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.utils.cache.MemberCacheView
import net.dv8tion.jda.internal.utils.Checks
import java.util.Arrays
import java.util.Collections

class MemberCacheViewImpl :
    SnowflakeCacheViewImpl<Member>(Member::class.java, Member::getEffectiveName),
    MemberCacheView {
    override fun getElementById(id: Long): Member? = get(id)

    override fun getElementsByUsername(
        name: String,
        ignoreCase: Boolean,
    ): List<Member> {
        Checks.notEmpty(name, "Name")
        if (isEmpty) {
            return Collections.emptyList()
        }
        val members = ArrayList<Member>()
        forEach { member ->
            val nick = member.user.name
            if (equals(ignoreCase, nick, name)) {
                members.add(member)
            }
        }
        return Collections.unmodifiableList(members)
    }

    override fun getElementsByNickname(
        name: String?,
        ignoreCase: Boolean,
    ): List<Member> {
        if (isEmpty) {
            return Collections.emptyList()
        }
        val members = ArrayList<Member>()
        forEach { member ->
            val nick = member.nickname
            if (nick == null) {
                if (name == null) {
                    members.add(member)
                }
                return@forEach
            }

            if (name != null && equals(ignoreCase, nick, name)) {
                members.add(member)
            }
        }
        return Collections.unmodifiableList(members)
    }

    override fun getElementsWithRoles(vararg roles: Role): List<Member> {
        Checks.notNull(roles, "Roles")
        return getElementsWithRoles(Arrays.asList(*roles))
    }

    @Suppress("ReturnCount")
    override fun getElementsWithRoles(roles: Collection<Role>): List<Member> {
        Checks.noneNull(roles, "Roles")
        if (isEmpty) {
            return Collections.emptyList()
        }

        val rolesWithoutPublicRole = roles.filter { !it.isPublicRole }
        if (rolesWithoutPublicRole.isEmpty()) {
            return asList()
        }

        val members = ArrayList<Member>()
        forEach { member ->
            if (member.unsortedRoles.containsAll(rolesWithoutPublicRole)) {
                members.add(member)
            }
        }
        return Collections.unmodifiableList(members)
    }
}
