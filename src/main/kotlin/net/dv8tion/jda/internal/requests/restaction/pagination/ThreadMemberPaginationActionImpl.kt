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

import net.dv8tion.jda.api.entities.ThreadMember
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.exceptions.ParsingException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadMemberPaginationAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl
import java.util.ArrayList
import java.util.EnumSet
import javax.annotation.Nonnull

private const val PAGE_LIMIT = 100

class ThreadMemberPaginationActionImpl(
    channel: ThreadChannel,
) : PaginationActionImpl<ThreadMember, ThreadMemberPaginationAction>(
        channel.jda,
        Route.Channels.LIST_THREAD_MEMBERS
            .compile(channel.id)
            .withQueryParams("with_member", "true"),
        1,
        PAGE_LIMIT,
        PAGE_LIMIT,
    ),
    ThreadMemberPaginationAction {
    private val channel: ThreadChannelImpl = channel as ThreadChannelImpl

    init {
        order = PaginationAction.PaginationOrder.FORWARD
    }

    @Nonnull
    override fun getThreadChannel(): ThreadChannel = channel

    @Nonnull
    override fun getSupportedOrders(): EnumSet<PaginationAction.PaginationOrder> = EnumSet.of(order)

    override fun getKey(it: ThreadMember): Long = it.idLong

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<ThreadMember>>,
    ) {
        val array: DataArray = response.array
        val members: MutableList<ThreadMember> = ArrayList(array.length())
        val builder: EntityBuilder = api.entityBuilder
        for (i in 0 until array.length()) {
            try {
                val obj: DataObject = array.getObject(i)
                if (obj.isNull("member")) {
                    continue
                }
                val threadMember = builder.createThreadMember(channel.guild, channel, obj)
                members.add(threadMember)
            } catch (e: ParsingException) {
                LOG.warn("Encountered an exception in ThreadMemberPaginationAction", e)
            } catch (e: NullPointerException) {
                LOG.warn("Encountered an exception in ThreadMemberPaginationAction", e)
            }
        }

        //        if (order == PaginationOrder.BACKWARD)
        //            Collections.reverse(members);
        if (useCache) {
            cached.addAll(members)
        }

        if (members.isNotEmpty()) {
            last = members[members.size - 1]
            lastKey = members[members.size - 1].idLong
        }
        request.onSuccess(members)
    }
}
