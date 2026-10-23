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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Entitlement
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.exceptions.ParsingException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.EntitlementPaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.utils.Checks
import java.util.ArrayList
import java.util.EnumSet
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val PAGE_LIMIT = 100

class EntitlementPaginationActionImpl(
    api: JDA,
) : PaginationActionImpl<Entitlement, EntitlementPaginationAction>(
        api,
        Route.Applications.GET_ENTITLEMENTS.compile(api.selfUser.applicationId),
        1,
        PAGE_LIMIT,
        PAGE_LIMIT,
    ),
    EntitlementPaginationAction {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var skuIds: MutableList<String> = ArrayList()

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var guildId: Long = 0

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var userId: Long = 0

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var excludeEnded: Boolean = false

    @Nonnull
    override fun getSupportedOrders(): EnumSet<PaginationAction.PaginationOrder> =
        EnumSet.of(PaginationAction.PaginationOrder.BACKWARD, PaginationAction.PaginationOrder.FORWARD)

    @Nonnull
    override fun user(
        @Nullable user: UserSnowflake?,
    ): EntitlementPaginationAction {
        if (user == null) {
            userId = 0
        } else {
            userId = user.idLong
        }
        return this
    }

    @Nonnull
    override fun skuIds(vararg skuIds: Long): EntitlementPaginationAction {
        this.skuIds.clear()
        for (skuId in skuIds) {
            this.skuIds.add(java.lang.Long.toUnsignedString(skuId))
        }
        return this
    }

    @Nonnull
    override fun skuIds(vararg skuIds: String): EntitlementPaginationAction {
        Checks.noneNull(skuIds, "skuIds")
        for (skuId in skuIds) {
            Checks.isSnowflake(skuId, "skuId")
        }

        this.skuIds.clear()

        this.skuIds.addAll(skuIds)
        return this
    }

    @Nonnull
    override fun skuIds(
        @Nonnull skuIds: Collection<String>,
    ): EntitlementPaginationAction {
        Checks.noneNull(skuIds, "skuIds")

        this.skuIds.clear()
        for (skuId in skuIds) {
            Checks.isSnowflake(skuId, "skuId")
            this.skuIds.add(skuId)
        }

        return this
    }

    @Nonnull
    override fun guild(guildId: Long): EntitlementPaginationAction {
        this.guildId = guildId
        return this
    }

    @Nonnull
    override fun excludeEnded(excludeEnded: Boolean): EntitlementPaginationAction {
        this.excludeEnded = excludeEnded
        return this
    }

    override fun finalizeRoute(): Route.CompiledRoute {
        var route = super.finalizeRoute()

        if (userId != 0L) {
            route = route.withQueryParams("user_id", java.lang.Long.toUnsignedString(userId))
        }

        if (skuIds.isNotEmpty()) {
            route = route.withQueryParams("sku_ids", skuIds.joinToString(","))
        }

        if (guildId != 0L) {
            route = route.withQueryParams("guild_id", java.lang.Long.toUnsignedString(guildId))
        }

        if (excludeEnded) {
            route = route.withQueryParams("exclude_ended", true.toString())
        }

        return route
    }

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<Entitlement>>,
    ) {
        val array: DataArray = response.array
        val entitlements: MutableList<Entitlement> = ArrayList(array.length())
        val builder: EntityBuilder = api.entityBuilder
        for (i in 0 until array.length()) {
            try {
                val obj: DataObject = array.getObject(i)
                val entitlement = builder.createEntitlement(obj)
                entitlements.add(entitlement)
            } catch (e: ParsingException) {
                LOG.warn("Encountered an exception in EntitlementPaginationAction", e)
            } catch (e: NullPointerException) {
                LOG.warn("Encountered an exception in EntitlementPaginationAction", e)
            }
        }

        if (entitlements.isNotEmpty()) {
            if (useCache) {
                cached.addAll(entitlements)
            }
            last = entitlements[entitlements.size - 1]
            lastKey = entitlements[entitlements.size - 1].idLong
        }

        request.onSuccess(entitlements)
    }

    override fun getKey(it: Entitlement): Long = it.idLong
}
