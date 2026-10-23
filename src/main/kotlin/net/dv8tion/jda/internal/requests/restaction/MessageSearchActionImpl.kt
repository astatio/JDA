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

package net.dv8tion.jda.internal.requests.restaction

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.messages.MessageSearchResponse
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.MessageSearchAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.messages.MessageSearchResponseImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import net.dv8tion.jda.internal.utils.PermissionUtil
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import java.util.function.Function
import java.util.stream.Collectors
import javax.annotation.Nonnull

private const val HTTP_ACCEPTED = 202

class MessageSearchActionImpl(
    private val guild: Guild,
) : RestActionImpl<MessageSearchResponse>(guild.jda, Route.Guilds.SEARCH_MESSAGES.compile(guild.id)),
    MessageSearchAction {
    private var limit: Int? = null
    private var offset: Int? = null
    private var minId: String? = null
    private var maxId: String? = null
    private var slop: Int? = null
    private var content: String? = null
    private var channels: Set<String> = emptySet()
    private var includedAuthorTypes: Set<MessageSearchAction.AuthorType> = emptySet()
    private var excludedAuthorTypes: Set<MessageSearchAction.AuthorType> = emptySet()
    private var authors: Set<String> = emptySet()
    private var mentionsUsers: Set<String> = emptySet()
    private var mentionsRoles: Set<String> = emptySet()
    private var mentionsEveryone: Boolean? = null
    private var repliesToUsers: Set<String> = emptySet()
    private var repliesToMessages: Set<String> = emptySet()
    private var pinned: Boolean? = null
    private var includedHasTypes: Set<MessageSearchAction.HasType> = emptySet()
    private var excludedHasTypes: Set<MessageSearchAction.HasType> = emptySet()
    private var embedTypes: Set<MessageSearchAction.EmbedType> = emptySet()
    private var embedProviders: Set<String> = emptySet()
    private var linkHostnames: Set<String> = emptySet()
    private var attachmentFilenames: Set<String> = emptySet()
    private var attachmentExtensions: Set<String> = emptySet()
    private var sortBy: MessageSearchAction.SortType? = null
    private var sortOrder: MessageSearchAction.SortOrder? = null
    private var includeNsfw: Boolean? = null

    @Nonnull
    override fun limit(limit: Int?): MessageSearchAction {
        if (limit != null) {
            Checks.positive(limit, "Limit")
            Checks.check(limit <= MessageSearchAction.MAX_LIMIT, "Limit must be lower than or equal to %d", MessageSearchAction.MAX_LIMIT)
        }
        this.limit = limit
        return this
    }

    @Nonnull
    override fun offset(offset: Int?): MessageSearchAction {
        if (offset != null) {
            Checks.positive(offset, "Offset")
            Checks.check(
                offset <= MessageSearchAction.MAX_OFFSET,
                "Offset must be lower than or equal to %d",
                MessageSearchAction.MAX_OFFSET,
            )
        }
        this.offset = offset
        return this
    }

    @Nonnull
    override fun minId(minId: Long): MessageSearchAction {
        Checks.notNegative(minId, "Min ID")
        this.minId = java.lang.Long.toUnsignedString(minId)
        return this
    }

    @Nonnull
    override fun minId(minId: String?): MessageSearchAction {
        if (minId != null) {
            Checks.isSnowflake(minId, "Min ID")
        }
        this.minId = minId
        return this
    }

    @Nonnull
    override fun maxId(maxId: Long): MessageSearchAction {
        Checks.notNegative(maxId, "Max ID")
        this.maxId = java.lang.Long.toUnsignedString(maxId)
        return this
    }

    @Nonnull
    override fun maxId(maxId: String?): MessageSearchAction {
        if (maxId != null) {
            Checks.isSnowflake(maxId, "Max ID")
        }
        this.maxId = maxId
        return this
    }

    @Nonnull
    override fun slop(slop: Int?): MessageSearchAction {
        if (slop != null) {
            Checks.notNegative(slop, "Slop")
            Checks.check(slop <= MessageSearchAction.MAX_SLOP, "Slop must be lower than or equal to %d", MessageSearchAction.MAX_SLOP)
        }
        this.slop = slop
        return this
    }

    @Nonnull
    override fun content(content: String?): MessageSearchAction {
        if (content != null) {
            Checks.inRange(content, 0, MessageSearchAction.MAX_CONTENT_LENGTH, "Content")
        }
        this.content = content
        return this
    }

    @Nonnull
    override fun channels(
        @Nonnull channels: Collection<GuildMessageChannel>,
    ): MessageSearchAction {
        Checks.noneNull(channels, "Channels")
        Checks.check(
            channels.size <= MessageSearchAction.MAX_CHANNELS,
            "Cannot filter on more than %d channels",
            MessageSearchAction.MAX_CHANNELS,
        )
        for (channel in channels) {
            Checks.check(
                channel.guild == guild,
                "Channel %s is from a different guild (expected %s, was %s)",
                channel,
                guild,
                channel.guild,
            )
            Checks.checkAccess(guild.selfMember, channel)
            if (!PermissionUtil.checkPermission(channel, guild.selfMember, Permission.MESSAGE_HISTORY)) {
                throw InsufficientPermissionException(channel, Permission.MESSAGE_HISTORY)
            }
        }
        this.channels = channels.stream().map { it.id }.collect(Collectors.toSet())
        return this
    }

    @Nonnull
    override fun channels(
        @Nonnull vararg channels: Long,
    ): MessageSearchAction {
        Checks.notNull(channels, "Channels")
        Checks.check(
            channels.size <= MessageSearchAction.MAX_CHANNELS,
            "Cannot filter on more than %d channels",
            MessageSearchAction.MAX_CHANNELS,
        )
        this.channels =
            java.util.Arrays
                .stream(channels)
                .mapToObj { java.lang.Long.toUnsignedString(it) }
                .collect(Collectors.toSet())
        return this
    }

    @Nonnull
    override fun channels(
        @Nonnull vararg channels: String,
    ): MessageSearchAction {
        Checks.noneNull(channels, "Channels")
        Checks.check(
            channels.size <= MessageSearchAction.MAX_CHANNELS,
            "Cannot filter on more than %d channels",
            MessageSearchAction.MAX_CHANNELS,
        )
        for (channel in channels) {
            Checks.isSnowflake(channel, "Channel")
        }
        this.channels = HashSet(channels.asList())
        return this
    }

    @Nonnull
    override fun includeAuthorTypes(
        @Nonnull authorTypes: Collection<MessageSearchAction.AuthorType>,
    ): MessageSearchAction {
        Checks.noneNull(authorTypes, "Author types")
        this.includedAuthorTypes = Helpers.copyEnumSet(MessageSearchAction.AuthorType::class.java, authorTypes)
        this.excludedAuthorTypes = emptySet()
        return this
    }

    @Nonnull
    override fun excludeAuthorTypes(
        @Nonnull authorTypes: Collection<MessageSearchAction.AuthorType>,
    ): MessageSearchAction {
        Checks.noneNull(authorTypes, "Author types")
        this.includedAuthorTypes = emptySet()
        this.excludedAuthorTypes = Helpers.copyEnumSet(MessageSearchAction.AuthorType::class.java, authorTypes)
        return this
    }

    @Nonnull
    override fun authors(
        @Nonnull authors: Collection<UserSnowflake>,
    ): MessageSearchAction {
        Checks.noneNull(authors, "Authors")
        Checks.check(
            authors.size <= MessageSearchAction.MAX_AUTHORS,
            "Cannot filter on more than %d authors",
            MessageSearchAction.MAX_AUTHORS,
        )
        this.authors = authors.stream().map { it.id }.collect(Collectors.toSet())
        return this
    }

    @Nonnull
    override fun mentionsUsers(
        @Nonnull mentions: Collection<UserSnowflake>,
    ): MessageSearchAction {
        Checks.noneNull(mentions, "Mentions")
        Checks.check(
            mentions.size <= MessageSearchAction.MAX_USER_MENTIONS,
            "Cannot filter on more than %d user mentions",
            MessageSearchAction.MAX_USER_MENTIONS,
        )
        this.mentionsUsers = mentions.stream().map { it.id }.collect(Collectors.toSet())
        return this
    }

    @Nonnull
    override fun mentionsRoles(
        @Nonnull mentions: Collection<Role>,
    ): MessageSearchAction {
        Checks.noneNull(mentions, "Mentions")
        Checks.check(
            mentions.size <= MessageSearchAction.MAX_ROLE_MENTIONS,
            "Cannot filter on more than %d role mentions",
            MessageSearchAction.MAX_ROLE_MENTIONS,
        )
        this.mentionsRoles = mentions.stream().map { it.id }.collect(Collectors.toSet())
        return this
    }

    @Nonnull
    override fun mentionsEveryone(mentionsEveryone: Boolean?): MessageSearchAction {
        this.mentionsEveryone = mentionsEveryone
        return this
    }

    @Nonnull
    override fun repliesToUsers(
        @Nonnull repliedTo: Collection<UserSnowflake>,
    ): MessageSearchAction {
        Checks.noneNull(repliedTo, "Users")
        Checks.check(
            repliedTo.size <= MessageSearchAction.MAX_REPLIED_TO_USERS,
            "Cannot filter on more than %d users replied",
            MessageSearchAction.MAX_REPLIED_TO_USERS,
        )
        this.repliesToUsers = repliedTo.stream().map { it.id }.collect(Collectors.toSet())
        return this
    }

    @Nonnull
    override fun repliesToMessages(
        @Nonnull repliedTo: Collection<String>,
    ): MessageSearchAction {
        Checks.noneNull(repliedTo, "Messages")
        Checks.check(
            repliedTo.size <= MessageSearchAction.MAX_REPLIED_TO_MESSAGES,
            "Cannot filter on more than %d messages replied",
            MessageSearchAction.MAX_REPLIED_TO_MESSAGES,
        )
        for (messageId in repliedTo) {
            Checks.isSnowflake(messageId, "Message ID")
        }
        this.repliesToMessages = HashSet(repliedTo)
        return this
    }

    @Nonnull
    override fun pinned(pinned: Boolean?): MessageSearchAction {
        this.pinned = pinned
        return this
    }

    @Nonnull
    override fun includeHasTypes(
        @Nonnull hasTypes: Collection<MessageSearchAction.HasType>,
    ): MessageSearchAction {
        Checks.noneNull(hasTypes, "HasTypes")
        this.includedHasTypes = Helpers.copyEnumSet(MessageSearchAction.HasType::class.java, hasTypes)
        this.excludedHasTypes = emptySet()
        return this
    }

    @Nonnull
    override fun excludeHasTypes(
        @Nonnull hasTypes: Collection<MessageSearchAction.HasType>,
    ): MessageSearchAction {
        Checks.noneNull(hasTypes, "HasTypes")
        this.includedHasTypes = emptySet()
        this.excludedHasTypes = Helpers.copyEnumSet(MessageSearchAction.HasType::class.java, hasTypes)
        return this
    }

    @Nonnull
    override fun embedTypes(
        @Nonnull embedTypes: Collection<MessageSearchAction.EmbedType>,
    ): MessageSearchAction {
        Checks.notNull(embedTypes, "Embed types")
        this.embedTypes = Helpers.copyEnumSet(MessageSearchAction.EmbedType::class.java, embedTypes)
        return this
    }

    @Nonnull
    override fun embedProvider(
        @Nonnull embedProviders: Collection<String>,
    ): MessageSearchAction {
        Checks.noneNull(embedProviders, "Embed providers")
        Checks.check(
            embedProviders.size <= MessageSearchAction.MAX_EMBED_PROVIDERS,
            "Cannot filter on more than %d embed providers",
            MessageSearchAction.MAX_EMBED_PROVIDERS,
        )
        for (embedProvider in embedProviders) {
            Checks.notLonger(embedProvider, MessageSearchAction.MAX_EMBED_PROVIDER_LENGTH, "Embed provider")
        }
        this.embedProviders = HashSet(embedProviders)
        return this
    }

    @Nonnull
    override fun linkHostnames(
        @Nonnull linkHostnames: Collection<String>,
    ): MessageSearchAction {
        Checks.noneNull(linkHostnames, "Link hostnames")
        Checks.check(
            linkHostnames.size <= MessageSearchAction.MAX_LINK_HOSTNAMES,
            "Cannot filter on more than %d link hostnames",
            MessageSearchAction.MAX_LINK_HOSTNAMES,
        )
        for (linkHostname in linkHostnames) {
            Checks.notLonger(linkHostname, MessageSearchAction.MAX_LINK_HOSTNAME_LENGTH, "Link hostname")
        }
        this.linkHostnames = HashSet(linkHostnames)
        return this
    }

    @Nonnull
    override fun attachmentFilenames(
        @Nonnull attachmentFilenames: Collection<String>,
    ): MessageSearchAction {
        Checks.noneNull(attachmentFilenames, "Attachment filenames")
        Checks.check(
            attachmentFilenames.size <= MessageSearchAction.MAX_ATTACHMENT_FILENAMES,
            "Cannot filter on more than %d attachment filenames",
            MessageSearchAction.MAX_ATTACHMENT_FILENAMES,
        )
        for (attachmentFilename in attachmentFilenames) {
            Checks.notLonger(attachmentFilename, MessageSearchAction.MAX_ATTACHMENT_FILENAME_LENGTH, "Attachment filename")
        }
        this.attachmentFilenames = HashSet(attachmentFilenames)
        return this
    }

    @Nonnull
    override fun attachmentExtensions(
        @Nonnull attachmentExtensions: Collection<String>,
    ): MessageSearchAction {
        Checks.noneNull(attachmentExtensions, "Attachment extensions")
        Checks.check(
            attachmentExtensions.size <= MessageSearchAction.MAX_ATTACHMENT_EXTENSIONS,
            "Cannot filter on more than %d attachment extensions",
            MessageSearchAction.MAX_ATTACHMENT_EXTENSIONS,
        )
        for (attachmentExtension in attachmentExtensions) {
            Checks.notLonger(attachmentExtension, MessageSearchAction.MAX_ATTACHMENT_EXTENSION_LENGTH, "Attachment extension")
        }
        this.attachmentExtensions = HashSet(attachmentExtensions)
        return this
    }

    @Nonnull
    override fun sortBy(
        @Nonnull sortType: MessageSearchAction.SortType,
    ): MessageSearchAction {
        Checks.notNull(sortType, "Sort type")
        this.sortBy = sortType
        return this
    }

    @Nonnull
    override fun sortOrder(
        @Nonnull sortOrder: MessageSearchAction.SortOrder,
    ): MessageSearchAction {
        Checks.notNull(sortOrder, "Sort order")
        this.sortOrder = sortOrder
        return this
    }

    @Nonnull
    override fun includeNsfw(includeNsfw: Boolean): MessageSearchAction {
        this.includeNsfw = includeNsfw
        return this
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun addCheck(
        @Nonnull checks: BooleanSupplier,
    ): MessageSearchAction = super<RestActionImpl>.addCheck(checks) as MessageSearchAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): MessageSearchAction = super<RestActionImpl>.setCheck(checks) as MessageSearchAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): MessageSearchAction = super<RestActionImpl>.timeout(timeout, unit) as MessageSearchAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): MessageSearchAction = super<RestActionImpl>.deadline(timestamp) as MessageSearchAction

    override fun finalizeRoute(): Route.CompiledRoute {
        var route = super.finalizeRoute()
        if (limit != null) {
            route = route.withQueryParams("limit", limit.toString())
        }
        if (offset != null) {
            route = route.withQueryParams("offset", offset.toString())
        }
        if (minId != null) {
            route = route.withQueryParams("min_id", minId!!)
        }
        if (maxId != null) {
            route = route.withQueryParams("max_id", maxId!!)
        }
        if (slop != null) {
            route = route.withQueryParams("slop", slop.toString())
        }
        if (content != null) {
            route = route.withQueryParams("content", content!!)
        }
        if (channels.isNotEmpty()) {
            route = appendList(route, "channel_id", channels)
        }
        if (includedAuthorTypes.isNotEmpty()) {
            route = appendList(route, "author_type", includedAuthorTypes) { it.value }
        } else if (excludedAuthorTypes.isNotEmpty()) {
            route = appendList(route, "author_type", excludedAuthorTypes) { "-" + it.value }
        }
        if (authors.isNotEmpty()) {
            route = appendList(route, "author_id", authors)
        }
        if (mentionsUsers.isNotEmpty()) {
            route = appendList(route, "mentions", mentionsUsers)
        }
        if (mentionsRoles.isNotEmpty()) {
            route = appendList(route, "mentions_role_id", mentionsRoles)
        }
        if (mentionsEveryone != null) {
            route = route.withQueryParams("mention_everyone", mentionsEveryone.toString())
        }
        if (repliesToUsers.isNotEmpty()) {
            route = appendList(route, "replied_to_user_id", repliesToUsers)
        }
        if (repliesToMessages.isNotEmpty()) {
            route = appendList(route, "replied_to_message_id", repliesToMessages)
        }
        if (pinned != null) {
            route = route.withQueryParams("pinned", pinned.toString())
        }
        if (includedHasTypes.isNotEmpty()) {
            route = appendList(route, "has", includedHasTypes) { it.value }
        } else if (excludedHasTypes.isNotEmpty()) {
            route = appendList(route, "has", includedHasTypes) { "-" + it.value }
        }
        if (embedTypes.isNotEmpty()) {
            route = appendList(route, "embed_type", embedTypes) { it.value }
        }
        if (embedProviders.isNotEmpty()) {
            route = appendList(route, "embed_provider", embedProviders)
        }
        if (linkHostnames.isNotEmpty()) {
            route = appendList(route, "link_hostname", linkHostnames)
        }
        if (attachmentFilenames.isNotEmpty()) {
            route = appendList(route, "attachment_filename", attachmentFilenames)
        }
        if (attachmentExtensions.isNotEmpty()) {
            route = appendList(route, "attachment_extension", attachmentExtensions)
        }
        if (sortBy != null) {
            route = route.withQueryParams("sort_by", sortBy!!.value)
        }
        if (sortOrder != null) {
            route = route.withQueryParams("sort_order", sortOrder!!.value)
        }
        if (includeNsfw != null) {
            route = route.withQueryParams("include_nsfw", includeNsfw.toString())
        }

        return route
    }

    override fun handleSuccess(
        response: Response,
        request: Request<MessageSearchResponse>,
    ) {
        val searchResponse: MessageSearchResponse
        val json = response.getObject()
        if (response.code == HTTP_ACCEPTED) {
            searchResponse =
                MessageSearchResponseImpl(
                    MessageSearchResponseImpl.NotReadyImpl(
                        json.getInt("documents_indexed"),
                        json.getInt("retry_after"),
                    ),
                )
        } else {
            searchResponse =
                MessageSearchResponseImpl(
                    MessageSearchResponseImpl.ResultsImpl(
                        readMessages(json),
                        json.getBoolean("doing_deep_historical_index"),
                        json.getInt("total_results"),
                    ),
                )
        }

        request.onSuccess(searchResponse)
    }

    private fun readMessages(json: DataObject): List<Message> {
        val threads = readThreadChannels(json)

        return Helpers
            .mapGracefully(
                json
                    .getArray("messages")
                    // Flatten as the API returns a 2D array
                    .stream(DataArray::getArray)
                    .flatMap { array -> array.stream(DataArray::getObject) },
                { d ->
                    val channelId = d.getUnsignedLong("channel_id")
                    var channel: GuildMessageChannel? = threads[channelId]
                    if (channel == null) {
                        channel = guild.getChannelById(GuildMessageChannel::class.java, channelId)
                    }
                    if (channel == null) {
                        throw IllegalStateException(
                            Helpers.format(
                                "Could not find a thread or a regular channel with ID %d in guild %s",
                                channelId,
                                guild.id,
                            ),
                        )
                    }
                    api.entityBuilder.createMessageWithChannel(d, channel, false)
                },
                "Unable to read a message from search results",
            ).collect(Helpers.toUnmodifiableList())
    }

    @Nonnull
    private fun readThreadChannels(json: DataObject): Map<Long, ThreadChannel> {
        if (json.isNull("threads")) {
            return emptyMap()
        }

        // Thread ID -> Thread member object
        val selfThreadMemberObjects = readSelfThreadMemberObjects(json)

        return Helpers
            .mapGracefully(
                json.getArray("threads").stream(DataArray::getObject),
                { o ->
                    // Put the self thread member, if it did join the thread
                    o.put("member", selfThreadMemberObjects[o.getUnsignedLong("id")])

                    api.entityBuilder.createThreadChannel(guild as GuildImpl, o, guild.idLong, false)
                },
                "Unable to read a thread channel from search results",
            ).collect(Collectors.toMap({ it.idLong }, { it }))
    }

    private companion object {
        @Nonnull
        private fun appendList(
            @Nonnull route: Route.CompiledRoute,
            @Nonnull paramName: String,
            @Nonnull list: Collection<String>,
        ): Route.CompiledRoute {
            var route = route
            for (element in list) {
                route = route.withQueryParams(paramName, element)
            }
            return route
        }

        @Nonnull
        private fun <T> appendList(
            @Nonnull route: Route.CompiledRoute,
            @Nonnull paramName: String,
            @Nonnull list: Collection<T>,
            @Nonnull valueFunction: Function<in T, String>,
        ): Route.CompiledRoute {
            var route = route
            for (element in list) {
                route = route.withQueryParams(paramName, valueFunction.apply(element))
            }
            return route
        }

        @Nonnull
        private fun readSelfThreadMemberObjects(json: DataObject): Map<Long, DataObject> {
            if (json.isNull("members")) {
                return emptyMap()
            }

            return json
                .getArray("members")
                .stream(DataArray::getObject)
                .collect(Collectors.toMap({ it.getUnsignedLong("id") }, { it }))
        }
    }
}
