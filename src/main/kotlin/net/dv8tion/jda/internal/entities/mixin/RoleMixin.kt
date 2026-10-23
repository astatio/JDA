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

package net.dv8tion.jda.internal.entities.mixin

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.RoleIcon
import net.dv8tion.jda.api.requests.restaction.RoleAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.detached.mixin.IDetachableEntityMixin
import net.dv8tion.jda.internal.utils.Checks
import java.time.OffsetDateTime
import javax.annotation.Nonnull
import javax.annotation.Nullable

interface RoleMixin<T : RoleMixin<T>> :
    Role,
    IDetachableEntityMixin {
    @Nonnull
    override fun createCopy(
        @Nonnull guild: Guild,
    ): RoleAction {
        Checks.notNull(guild, "Guild")
        return guild
            .createRole()
            .setColors(colors)
            .setHoisted(isHoisted)
            .setMentionable(isMentionable)
            .setName(name)
            .setPermissions(permissionsRaw)
            .setIcon(
                // we can only copy the emoji as we don't have access to the Icon instance
                icon?.emoji,
            )
    }

    @Suppress("ReturnCount") // ported verbatim from the Java original; matching its early-return control flow
    override fun compareTo(
        @Nonnull other: Role,
    ): Int {
        if (this === other) {
            return 0
        }

        if (this.guild.idLong != other.guild.idLong) {
            throw IllegalArgumentException("Cannot compare roles that aren't from the same guild!")
        }

        if (this.positionRaw != other.positionRaw) {
            return this.positionRaw - other.positionRaw
        }

        val thisTime: OffsetDateTime = this.timeCreated
        val rTime: OffsetDateTime = other.timeCreated

        // We compare the provided role's time to this's time
        // instead of the reverse as one would expect due to how discord deals with hierarchy.
        // The more recent a role was created,
        // the lower its hierarchy ranking when it shares the same position as another role.
        return rTime.compareTo(thisTime)
    }

    fun setName(name: String): T

    fun setPrimaryColor(color: Int): T

    fun setSecondaryColor(color: Int): T

    fun setTertiaryColor(color: Int): T

    fun setManaged(managed: Boolean): T

    fun setHoisted(hoisted: Boolean): T

    fun setMentionable(mentionable: Boolean): T

    fun setRawPermissions(rawPermissions: Long): T

    fun setRawPosition(rawPosition: Int): T

    fun setTags(tags: DataObject): T

    fun setIcon(
        @Nullable icon: RoleIcon?,
    ): T
}
