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

package net.dv8tion.jda.internal.requests.restaction.pagination

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.audit.AuditLogEntry
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.exceptions.ParsingException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl
import java.util.ArrayList
import java.util.EnumSet
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val PAGE_LIMIT = 100

class AuditLogPaginationActionImpl(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val guild: Guild,
) : PaginationActionImpl<AuditLogEntry, AuditLogPaginationAction>(
        guild.jda,
        Route.Guilds.GET_AUDIT_LOGS.compile(guild.id),
        1,
        PAGE_LIMIT,
        PAGE_LIMIT,
    ),
    AuditLogPaginationAction {
    // filters
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var type: ActionType? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var userId: String? = null

    init {
        if (!guild.selfMember.hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            throw InsufficientPermissionException(guild, Permission.VIEW_AUDIT_LOGS)
        }
        order = PaginationAction.PaginationOrder.BACKWARD
    }

    @Nonnull
    override fun type(
        @Nullable type: ActionType?,
    ): AuditLogPaginationActionImpl {
        this.type = type
        return this
    }

    @Nonnull
    override fun user(
        @Nullable user: UserSnowflake?,
    ): AuditLogPaginationActionImpl {
        userId = user?.id
        return this
    }

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nonnull
    override fun getSupportedOrders(): EnumSet<PaginationAction.PaginationOrder> =
        EnumSet.of(PaginationAction.PaginationOrder.BACKWARD, PaginationAction.PaginationOrder.FORWARD)

    override fun finalizeRoute(): Route.CompiledRoute {
        var route = super.finalizeRoute()

        if (type != null) {
            route = route.withQueryParams("action_type", type!!.key.toString())
        }

        if (userId != null) {
            route = route.withQueryParams("user_id", userId!!)
        }

        return route
    }

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<AuditLogEntry>>,
    ) {
        val obj: DataObject = response.getObject()
        val users: DataArray = obj.getArray("users")
        val webhooks: DataArray = obj.getArray("webhooks")
        val entries: DataArray = obj.getArray("audit_log_entries")

        val list: MutableList<AuditLogEntry> = ArrayList(entries.length())
        val builder: EntityBuilder = api.entityBuilder

        val userMap: TLongObjectMap<DataObject> = TLongObjectHashMap()
        for (i in 0 until users.length()) {
            val user: DataObject = users.getObject(i)
            userMap.put(user.getLong("id"), user)
        }

        val webhookMap: TLongObjectMap<DataObject> = TLongObjectHashMap()
        for (i in 0 until webhooks.length()) {
            val webhook: DataObject = webhooks.getObject(i)
            webhookMap.put(webhook.getLong("id"), webhook)
        }

        for (i in 0 until entries.length()) {
            try {
                val entry: DataObject = entries.getObject(i)
                val user: DataObject? = userMap.get(entry.getLong("user_id", 0))
                val webhook: DataObject? = webhookMap.get(entry.getLong("target_id", 0))
                val result = builder.createAuditLogEntry(guild as GuildImpl, entry, user, webhook)
                list.add(result)
            } catch (e: ParsingException) {
                LOG.warn("Encountered exception in AuditLogPagination", e)
            } catch (e: NullPointerException) {
                LOG.warn("Encountered exception in AuditLogPagination", e)
            }
        }

        if (list.isNotEmpty()) {
            if (useCache) {
                cached.addAll(list)
            }
            last = list[list.size - 1]
            lastKey = list[list.size - 1].idLong
        }

        request.onSuccess(list)
    }

    override fun getKey(it: AuditLogEntry): Long = it.idLong
}
