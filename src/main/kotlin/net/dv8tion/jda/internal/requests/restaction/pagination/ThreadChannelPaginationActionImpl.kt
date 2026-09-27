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
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion
import net.dv8tion.jda.api.exceptions.ParsingException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.utils.Helpers
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.ArrayList
import java.util.EnumSet
import javax.annotation.Nonnull

private const val PAGE_LIMIT = 100

class ThreadChannelPaginationActionImpl(
    api: JDA,
    route: Route.CompiledRoute,
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val channel: IThreadContainer,
    // Whether IDs or ISO8601 timestamps shall be provided for all pagination requests.
    // Some thread pagination endpoints require this odd and singular behavior
    // throughout the discord api.
    @JvmField
    protected val useID: Boolean,
) : PaginationActionImpl<ThreadChannel, ThreadChannelPaginationAction>(api, route, 2, PAGE_LIMIT, PAGE_LIMIT),
    ThreadChannelPaginationAction {
    @Nonnull
    override fun getChannel(): IThreadContainerUnion = channel as IThreadContainerUnion

    @Nonnull
    override fun getSupportedOrders(): EnumSet<PaginationAction.PaginationOrder> = EnumSet.of(PaginationAction.PaginationOrder.BACKWARD)

    // Thread pagination supplies ISO8601 timestamps for some cases, see constructor
    @Nonnull
    @Suppress("ReturnCount")
    override fun getPaginationLastEvaluatedKey(
        lastId: Long,
        last: ThreadChannel?,
    ): String {
        if (useID) {
            return java.lang.Long.toUnsignedString(lastId)
        }

        if (order === PaginationAction.PaginationOrder.FORWARD && lastId == 0L) {
            // first second of 2015 aka discords epoch
            return "2015-01-01T00:00:00.000"
        }

        // this should be redundant, due to calling this with PaginationAction#getLast()
        // as last param, but let's have this here.
        if (last == null) {
            return OffsetDateTime.now(ZoneOffset.UTC).toString()
        }

        // OffsetDateTime#toString() is defined to be ISO8601, needs no helper method.
        return last.timeArchiveInfoLastModified.toString()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<ThreadChannel>>,
    ) {
        val obj: DataObject = response.getObject()
        val selfThreadMembers: DataArray = obj.getArray("members")
        val threads: DataArray = obj.getArray("threads")

        val list: MutableList<ThreadChannel> = ArrayList(threads.length())
        val builder: EntityBuilder = api.entityBuilder

        val selfThreadMemberMap: TLongObjectMap<DataObject> =
            Helpers.convertToMap({ o -> o.getUnsignedLong("id") }, selfThreadMembers)

        for (i in 0 until threads.length()) {
            try {
                val threadObj: DataObject = threads.getObject(i)
                val selfThreadMemberObj: DataObject? = selfThreadMemberMap.get(threadObj.getLong("id", 0))

                if (selfThreadMemberObj != null) {
                    // Combine the thread and self thread-member into a single object
                    // to model what we get from thread payloads (like from Gateway, etc)
                    threadObj.put("member", selfThreadMemberObj)
                }

                try {
                    val thread: ThreadChannel = builder.createThreadChannel(threadObj, getGuild().idLong)
                    list.add(thread)

                    if (useCache) {
                        cached.add(thread)
                    }
                    last = thread
                    lastKey = thread.idLong
                } catch (e: Exception) {
                    if (EntityBuilder.MISSING_CHANNEL == e.message) {
                        EntityBuilder.LOG.debug("Discarding thread without cached parent channel. JSON: {}", threadObj)
                    } else {
                        EntityBuilder.LOG.warn("Failed to create thread channel. JSON: {}", threadObj, e)
                    }
                }
            } catch (e: ParsingException) {
                LOG.warn("Encountered exception in ThreadChannelPagination", e)
            } catch (e: NullPointerException) {
                LOG.warn("Encountered exception in ThreadChannelPagination", e)
            }
        }

        request.onSuccess(list)
    }

    override fun getKey(it: ThreadChannel): Long = it.idLong
}
