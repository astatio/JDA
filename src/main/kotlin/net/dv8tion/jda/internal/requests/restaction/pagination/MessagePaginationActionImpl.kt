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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.exceptions.ParsingException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.utils.Checks
import java.util.ArrayList
import java.util.Collections
import javax.annotation.Nonnull

private const val PAGE_LIMIT = 100

class MessagePaginationActionImpl(
    private val channel: MessageChannel,
) : PaginationActionImpl<Message, MessagePaginationAction>(
        channel.jda,
        Route.Messages.GET_MESSAGE_HISTORY.compile(channel.id),
        1,
        PAGE_LIMIT,
        PAGE_LIMIT,
    ),
    MessagePaginationAction {
    init {
        if (channel is GuildChannel) {
            val selfMember: Member = channel.guild.selfMember
            Checks.checkAccess(selfMember, channel)
            if (!selfMember.hasPermission(channel, Permission.MESSAGE_HISTORY)) {
                throw InsufficientPermissionException(channel, Permission.MESSAGE_HISTORY)
            }
        }
    }

    @Nonnull
    override fun getChannel(): MessageChannelUnion = channel as MessageChannelUnion

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<Message>>,
    ) {
        val array: DataArray = response.array
        val messages: MutableList<Message> = ArrayList(array.length())
        val builder: EntityBuilder = api.entityBuilder
        for (i in 0 until array.length()) {
            try {
                val msg = builder.createMessageWithChannel(array.getObject(i), channel, false)
                messages.add(msg)
            } catch (e: ParsingException) {
                LOG.warn("Encountered an exception in MessagePagination", e)
            } catch (e: NullPointerException) {
                LOG.warn("Encountered an exception in MessagePagination", e)
            } catch (e: IllegalArgumentException) {
                if (EntityBuilder.UNKNOWN_MESSAGE_TYPE == e.message) {
                    LOG.warn("Skipping unknown message type during pagination", e)
                } else {
                    LOG.warn("Unexpected issue trying to parse message during pagination", e)
                }
            }
        }

        if (order === PaginationAction.PaginationOrder.FORWARD) {
            Collections.reverse(messages)
        }
        if (useCache) {
            cached.addAll(messages)
        }

        if (messages.isNotEmpty()) {
            last = messages[messages.size - 1]
            lastKey = messages[messages.size - 1].idLong
        }

        request.onSuccess(messages)
    }

    override fun getKey(it: Message): Long = it.idLong
}
