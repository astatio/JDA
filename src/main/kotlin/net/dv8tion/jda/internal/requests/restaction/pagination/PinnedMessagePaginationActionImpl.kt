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

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.PinnedMessagePaginationAction
import net.dv8tion.jda.api.utils.TimeUtil
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.ReceivedMessage
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.ArrayList
import java.util.EnumSet
import javax.annotation.Nonnull

private const val PAGE_LIMIT = 50

class PinnedMessagePaginationActionImpl(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val channel: MessageChannel,
) : PaginationActionImpl<PinnedMessagePaginationAction.PinnedMessage, PinnedMessagePaginationAction>(
        channel.jda,
        Route.Messages.GET_MESSAGE_PINS.compile(channel.id),
        1,
        PAGE_LIMIT,
        PAGE_LIMIT,
    ),
    PinnedMessagePaginationAction {
    @Nonnull
    override fun getSupportedOrders(): EnumSet<PaginationAction.PaginationOrder> = EnumSet.of(PaginationAction.PaginationOrder.BACKWARD)

    override fun getKey(it: PinnedMessagePaginationAction.PinnedMessage): Long {
        val timestamp = it.timePinned
        val epochMillis = timestamp.toInstant().toEpochMilli()
        return TimeUtil.getDiscordTimestamp(epochMillis)
    }

    @Nonnull
    override fun getPaginationLastEvaluatedKey(
        lastId: Long,
        last: PinnedMessagePaginationAction.PinnedMessage?,
    ): String {
        if (last == null) {
            return OffsetDateTime.now(ZoneOffset.UTC).toString()
        }
        return last.timePinned.toString()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<PinnedMessagePaginationAction.PinnedMessage>>,
    ) {
        val obj: DataObject = response.getObject()
        val items: DataArray = obj.getArray("items")
        val entityBuilder: EntityBuilder = api.entityBuilder
        val messages: MutableList<PinnedMessagePaginationAction.PinnedMessage> = ArrayList(items.length())

        for (i in 0 until items.length()) {
            try {
                val item: DataObject = items.getObject(i)
                val message: ReceivedMessage =
                    entityBuilder.createMessageWithChannel(item.getObject("message"), channel, false)
                val pinnedAt = item.getOffsetDateTime("pinned_at")
                val pinnedMessage = PinnedMessagePaginationAction.PinnedMessage(pinnedAt, message)

                messages.add(pinnedMessage)
                last = pinnedMessage
                lastKey = getKey(pinnedMessage)
                if (useCache) {
                    cached.add(pinnedMessage)
                }
            } catch (e: Exception) {
                EntityBuilder.LOG.error("Failed to parse pinned message", e)
            }
        }

        request.onSuccess(messages)
    }
}
