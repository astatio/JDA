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
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.exceptions.ParsingException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.internal.entities.EntityBuilder
import java.util.ArrayList
import java.util.EnumSet
import javax.annotation.Nonnull

private const val PAGE_LIMIT = 100

class ReactionPaginationActionImpl private constructor(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val reaction: MessageReaction?,
    jda: JDA,
    route: Route.CompiledRoute,
) : PaginationActionImpl<User, ReactionPaginationAction>(jda, route, 1, PAGE_LIMIT, PAGE_LIMIT),
    ReactionPaginationAction {
    init {
        super.order(PaginationAction.PaginationOrder.FORWARD)
    }

    /**
     * Creates a new PaginationAction instance
     *
     * @param reaction
     *        The target [MessageReaction]
     */
    constructor(reaction: MessageReaction) : this(
        reaction,
        reaction.jda,
        getCompiledRoute(
            reaction.channelId,
            reaction.messageId,
            getCode(reaction),
            MessageReaction.ReactionType.NORMAL,
        ),
    )

    /**
     * Creates a new PaginationAction instance
     *
     * @param reaction
     *        The target [MessageReaction]
     * @param type
     *        Type of [MessageReaction.ReactionType] to retrieve users for
     */
    constructor(reaction: MessageReaction, type: MessageReaction.ReactionType) : this(
        reaction,
        reaction.jda,
        getCompiledRoute(reaction.channelId, reaction.messageId, getCode(reaction), type),
    )

    constructor(message: Message, code: String, type: MessageReaction.ReactionType) : this(
        null,
        message.jda,
        getCompiledRoute(message.channelId, message.id, code, type),
    )

    constructor(
        channel: MessageChannel,
        messageId: String,
        code: String,
        type: MessageReaction.ReactionType,
    ) : this(null, channel.jda, getCompiledRoute(channel.id, messageId, code, type))

    @Nonnull
    override fun getReaction(): MessageReaction {
        if (reaction == null) {
            throw IllegalStateException("Cannot get reaction for this action")
        }
        return reaction
    }

    @Nonnull
    override fun getSupportedOrders(): EnumSet<PaginationAction.PaginationOrder> = EnumSet.of(PaginationAction.PaginationOrder.FORWARD)

    @Suppress("TooGenericExceptionCaught")
    override fun handleSuccess(
        response: Response,
        request: Request<List<User>>,
    ) {
        val builder: EntityBuilder = api.entityBuilder
        val array: DataArray = response.array
        val users: MutableList<User> = ArrayList()
        for (i in 0 until array.length()) {
            try {
                val user = builder.createUser(array.getObject(i))
                users.add(user)
                if (useCache) {
                    cached.add(user)
                }
                last = user
                lastKey = user.idLong
            } catch (e: ParsingException) {
                LOG.warn("Encountered exception in ReactionPagination", e)
            } catch (e: NullPointerException) {
                LOG.warn("Encountered exception in ReactionPagination", e)
            }
        }

        request.onSuccess(users)
    }

    override fun getKey(it: User): Long = it.idLong

    companion object {
        private fun getCompiledRoute(
            channelId: String,
            messageId: String,
            code: String,
            type: MessageReaction.ReactionType,
        ): Route.CompiledRoute =
            Route.Messages.GET_REACTION_USERS
                .compile(channelId, messageId, code)
                .withQueryParams("type", type.id.toString())

        @JvmStatic
        protected fun getCode(reaction: MessageReaction): String = reaction.emoji.getAsReactionCode()
    }
}
