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

package net.dv8tion.jda.internal.requests.restaction.order

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.order.RoleOrderAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.Collections
import javax.annotation.Nonnull

open class RoleOrderActionImpl(
    @JvmField protected val guild: Guild,
    useAscendingOrder: Boolean,
) : OrderActionImpl<Role, RoleOrderAction>(
        guild.jda,
        !useAscendingOrder,
        Route.Guilds.MODIFY_ROLES.compile(guild.id),
    ),
    RoleOrderAction {
    init {
        val roles = guild.roles.subList(0, guild.roles.size - 1) // Don't include the @everyone role.

        if (useAscendingOrder) {
            // Add roles to orderList in reverse due to role position ordering being descending
            // Top role starts at roles.size() - 1, bottom is 0.
            for (i in roles.size - 1 downTo 0) {
                this.orderList.add(roles[i])
            }
        } else {
            // If not using discord ordering, we are ascending, so we add from first to last.
            this.orderList.addAll(roles)
        }
    }

    @Nonnull
    override fun getGuild(): Guild = guild

    override fun finalizeData(): RequestBody {
        val self: Member = guild.selfMember
        val isOwner = self.isOwner

        if (!isOwner) {
            checkOrderPermission(self)
        }

        val array = DataArray.empty()
        val ordering: MutableList<Role> = ArrayList(orderList)

        // If not in normal discord order, reverse.
        // Normal order is descending, not ascending.
        if (ascendingOrder) {
            Collections.reverse(ordering)
        }

        for (i in ordering.indices) {
            val role = ordering[i]
            val initialPos = role.position
            if (initialPos != i && !isOwner && !self.canInteract(role)) {
                // If the current role was moved, we are not owner and we can't interact with the
                // role then throw a PermissionException
                throw IllegalStateException(
                    "Cannot change order: One of the roles could not be moved due to hierarchical power!",
                )
            }

            array.add(
                DataObject
                    .empty()
                    .put("id", role.id)
                    .put("position", i + 1), // plus 1 because position 0 is the @everyone position.
            )
        }

        return getRequestBody(array)
    }

    private fun checkOrderPermission(self: Member) {
        if (self.roles.isEmpty()) {
            throw IllegalStateException(
                "Cannot move roles above your highest role unless you are the guild owner",
            )
        }
        if (!self.hasPermission(Permission.MANAGE_ROLES)) {
            throw InsufficientPermissionException(guild, Permission.MANAGE_ROLES)
        }
    }

    override fun validateInput(entity: Role) {
        Checks.check(entity.guild == guild, "Provided selected role is not from this Guild!")
        Checks.check(orderList.contains(entity), "Provided role is not in the list of orderable roles!")
    }
}
