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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.BanPaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import java.util.ArrayList
import java.util.Collections
import javax.annotation.Nonnull

private const val PAGE_LIMIT = 1000

class BanPaginationActionImpl(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val guild: Guild,
) : PaginationActionImpl<Guild.Ban, BanPaginationAction>(
        guild.jda,
        Route.Guilds.GET_BANS.compile(guild.id),
        1,
        PAGE_LIMIT,
        PAGE_LIMIT,
    ),
    BanPaginationAction {
    init {
        lastKey = Long.MAX_VALUE
    }

    @Nonnull
    override fun order(
        @Nonnull order: PaginationAction.PaginationOrder,
    ): BanPaginationAction {
        if (order === PaginationAction.PaginationOrder.BACKWARD && lastKey == 0L) {
            lastKey = Long.MAX_VALUE
        } else if (order === PaginationAction.PaginationOrder.FORWARD && lastKey == Long.MAX_VALUE) {
            lastKey = 0
        }
        return super.order(order)
    }

    @Nonnull
    override fun getGuild(): Guild = guild

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<Guild.Ban>>,
    ) {
        val builder: EntityBuilder = api.entityBuilder
        val bannedArr: DataArray = response.array
        val bans: MutableList<Guild.Ban> = ArrayList(bannedArr.length())

        for (i in 0 until bannedArr.length()) {
            val obj: DataObject = bannedArr.getObject(i)
            try {
                val user = obj.getObject("user")
                val ban = Guild.Ban(builder.createUser(user), obj.getString("reason", null))

                bans.add(ban)
            } catch (t: Exception) {
                LOG.error(
                    "Got an unexpected error while decoding ban index {} for guild {}:\nData: {}",
                    i,
                    guild.id,
                    obj,
                    t,
                )
            }
        }

        if (order === PaginationAction.PaginationOrder.BACKWARD) {
            Collections.reverse(bans)
        }
        if (useCache) {
            cached.addAll(bans)
        }

        if (bans.isNotEmpty()) {
            last = bans[bans.size - 1]
            lastKey = bans[bans.size - 1].user.idLong
        }

        request.onSuccess(Collections.unmodifiableList(bans))
    }

    override fun getKey(it: Guild.Ban): Long = it.user.idLong
}
