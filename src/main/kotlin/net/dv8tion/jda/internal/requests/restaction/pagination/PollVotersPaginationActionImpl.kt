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
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.exceptions.ParsingException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.PollVotersPaginationAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import java.util.ArrayList
import java.util.EnumSet
import javax.annotation.Nonnull

private const val PAGE_LIMIT = 100

class PollVotersPaginationActionImpl(
    jda: JDA,
    channelId: String,
    messageId: String,
    answerId: Long,
) : PaginationActionImpl<User, PollVotersPaginationAction>(
        jda,
        Route.Messages.GET_POLL_ANSWER_VOTERS.compile(channelId, messageId, answerId.toString()),
        1,
        PAGE_LIMIT,
        PAGE_LIMIT,
    ),
    PollVotersPaginationAction {
    init {
        order = PaginationAction.PaginationOrder.FORWARD
    }

    @Nonnull
    override fun getSupportedOrders(): EnumSet<PaginationAction.PaginationOrder> = EnumSet.of(PaginationAction.PaginationOrder.FORWARD)

    override fun getKey(it: User): Long = it.idLong

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<User>>,
    ) {
        val array: DataArray = response.getObject().getArray("users")
        val users: MutableList<User> = ArrayList(array.length())
        val builder: EntityBuilder = api.entityBuilder
        for (i in 0 until array.length()) {
            try {
                val obj: DataObject = array.getObject(i)
                users.add(builder.createUser(obj))
            } catch (e: ParsingException) {
                LOG.warn("Encountered an exception in PollVotersPaginationAction", e)
            } catch (e: NullPointerException) {
                LOG.warn("Encountered an exception in PollVotersPaginationAction", e)
            }
        }

        if (users.isNotEmpty()) {
            if (useCache) {
                cached.addAll(users)
            }
            last = users[users.size - 1]
            lastKey = users[users.size - 1].idLong
        }

        request.onSuccess(users)
    }
}
