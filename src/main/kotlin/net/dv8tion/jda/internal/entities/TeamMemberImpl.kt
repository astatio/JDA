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

import net.dv8tion.jda.api.entities.TeamMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull

class TeamMemberImpl(
    private val user: User,
    private val state: TeamMember.MembershipState,
    private val roleType: TeamMember.RoleType,
    private val teamId: Long,
) : TeamMember {
    @Nonnull
    override fun getUser(): User = user

    @Nonnull
    override fun getMembershipState(): TeamMember.MembershipState = state

    @Nonnull
    override fun getRoleType(): TeamMember.RoleType = roleType

    override fun getTeamIdLong(): Long = teamId

    override fun hashCode(): Int = Objects.hash(user, teamId)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is TeamMemberImpl) {
            return false
        }
        return other.teamId == this.teamId && other.user == this.user
    }

    override fun toString(): String =
        EntityString(this)
            .addMetadata("teamId", getTeamId())
            .addMetadata("user", user)
            .toString()
}
