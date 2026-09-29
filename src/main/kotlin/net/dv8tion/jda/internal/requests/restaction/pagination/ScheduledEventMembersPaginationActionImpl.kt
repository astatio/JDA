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
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.ScheduledEvent
import net.dv8tion.jda.api.exceptions.ParsingException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.ScheduledEventMembersPaginationAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl
import java.util.ArrayList
import java.util.Collections
import javax.annotation.Nonnull

private const val PAGE_LIMIT = 100

class ScheduledEventMembersPaginationActionImpl private constructor(
    jda: JDA,
    route: Route.CompiledRoute,
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val guild: Guild,
) : PaginationActionImpl<Member, ScheduledEventMembersPaginationAction>(jda, route, 1, PAGE_LIMIT, PAGE_LIMIT),
    ScheduledEventMembersPaginationAction {
    constructor(event: ScheduledEvent) : this(
        event.guild.jda,
        Route.Guilds.GET_SCHEDULED_EVENT_USERS
            .compile(event.guild.id, event.id)
            .withQueryParams("with_member", "true"),
        event.guild,
    )

    @Nonnull
    override fun getGuild(): Guild = guild

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<Member>>,
    ) {
        val array: DataArray = response.array
        val members: MutableList<Member> = ArrayList(array.length())
        val builder: EntityBuilder = api.entityBuilder
        for (i in 0 until array.length()) {
            try {
                val obj: DataObject = array.getObject(i)
                if (obj.isNull("member")) {
                    continue
                }
                val userObject = obj.getObject("user")
                val memberObject = obj.getObject("member")
                val member = builder.createMember(guild as GuildImpl, memberObject.put("user", userObject))
                members.add(member)
            } catch (e: ParsingException) {
                LOG.warn("Encountered an exception in ScheduledEventPagination", e)
            } catch (e: NullPointerException) {
                LOG.warn("Encountered an exception in ScheduledEventPagination", e)
            }
        }

        if (order === PaginationAction.PaginationOrder.BACKWARD) {
            Collections.reverse(members)
        }
        if (useCache) {
            cached.addAll(members)
        }

        if (members.isNotEmpty()) {
            last = members[members.size - 1]
            lastKey = members[members.size - 1].idLong
        }
        request.onSuccess(members)
    }

    override fun getKey(it: Member): Long = it.idLong
}
