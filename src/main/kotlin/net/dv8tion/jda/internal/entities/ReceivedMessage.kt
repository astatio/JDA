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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Mentions
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Message.Attachment
import net.dv8tion.jda.api.entities.Message.MessageFlag
import net.dv8tion.jda.api.entities.MessageActivity
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.MessageReference
import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.WebhookClient
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.entities.messages.MessagePoll
import net.dv8tion.jda.api.entities.messages.MessageSnapshot
import net.dv8tion.jda.api.entities.sticker.StickerItem
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction
import net.dv8tion.jda.api.utils.AttachedFile
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.messages.MessageEditData
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.interactions.InteractionHookImpl
import net.dv8tion.jda.internal.requests.CompletedRestAction
import net.dv8tion.jda.internal.requests.ErrorMapper
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.requests.restaction.MessageEditActionImpl
import net.dv8tion.jda.internal.requests.restaction.pagination.ReactionPaginationActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EncodingUtil
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.io.IOException
import java.io.UncheckedIOException
import java.time.OffsetDateTime
import java.util.Collections
import java.util.EnumSet
import java.util.Formattable
import java.util.FormattableFlags
import java.util.Formatter
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.annotation.Nonnull
import javax.annotation.Nullable

@Suppress("DEPRECATION")
open class ReceivedMessage(
    id: Long,
    channelId: Long,
    guildId: Long,
    jda: JDA,
    @Nullable guild: Guild?,
    @Nullable channel: MessageChannel?,
    type: MessageType,
    @Nullable messageReference: MessageReference?,
    fromWebhook: Boolean,
    applicationId: Long,
    tts: Boolean,
    pinned: Boolean,
    content: String,
    @Nullable nonce: String?,
    author: User,
    @Nullable member: Member?,
    @Nullable activity: MessageActivity?,
    @Nullable poll: MessagePoll?,
    @Nullable editTime: OffsetDateTime?,
    mentions: Mentions,
    reactions: List<MessageReaction>,
    attachments: List<Attachment>,
    embeds: List<MessageEmbed>,
    stickers: List<StickerItem>,
    components: List<MessageTopLevelComponentUnion>,
    messageSnapshots: List<MessageSnapshot>,
    flags: Int,
    @Nullable interaction: Message.Interaction?,
    @Nullable interactionMetadata: Message.InteractionMetadata?,
    @Nullable startedThread: ThreadChannel?,
    position: Int,
) : Message,
    Formattable {
    // The fields mirror the Java declaration exactly. @JvmField keeps them as plain fields, since a
    // Kotlin property would emit an accessor that collides with the Message/ISnowflake methods.
    @JvmField
    protected val id: Long = id

    @JvmField
    protected val channelId: Long = channelId

    @JvmField
    protected val guildId: Long = guildId

    @JvmField
    protected val api: JDAImpl = jda as JDAImpl

    @JvmField
    @Nullable
    protected val guild: Guild? = guild

    @JvmField
    @Nullable
    protected val channel: MessageChannel? = channel

    @JvmField
    protected val type: MessageType = type

    @JvmField
    @Nullable
    protected val messageReference: MessageReference? = messageReference

    @JvmField
    protected val fromWebhook: Boolean = fromWebhook

    @JvmField
    protected val applicationId: Long = applicationId

    @JvmField
    protected val pinned: Boolean = pinned

    @JvmField
    protected val content: String = content

    @JvmField
    @Nullable
    protected val nonce: String? = nonce

    @JvmField
    protected val author: User = author

    @JvmField
    @Nullable
    protected val member: Member? = member

    @JvmField
    @Nullable
    protected val activity: MessageActivity? = activity

    @JvmField
    @Nullable
    protected val poll: MessagePoll? = poll

    @JvmField
    @Nullable
    protected val editedTime: OffsetDateTime? = editTime

    @JvmField
    protected val mentions: Mentions = mentions

    @JvmField
    protected val reactions: List<MessageReaction> = Collections.unmodifiableList(reactions)

    @JvmField
    protected val attachments: List<Attachment> = Collections.unmodifiableList(attachments)

    @JvmField
    protected val embeds: List<MessageEmbed> = Collections.unmodifiableList(embeds)

    @JvmField
    protected val stickers: List<StickerItem> = Collections.unmodifiableList(stickers)

    @JvmField
    protected val components: List<MessageTopLevelComponentUnion> = Collections.unmodifiableList(components)

    @JvmField
    protected val messageSnapshots: List<MessageSnapshot> = Collections.unmodifiableList(messageSnapshots)

    @JvmField
    protected val flags: Int = flags

    @JvmField
    @Suppress("DEPRECATION")
    @Nullable
    protected val interaction: Message.Interaction? = interaction

    @JvmField
    @Nullable
    protected val interactionMetadata: Message.InteractionMetadata? = interactionMetadata

    @JvmField
    @Nullable
    protected val startedThread: ThreadChannel? = startedThread

    @JvmField
    protected val position: Int = position

    @JvmField
    protected val isTTS: Boolean = tts

    @JvmField
    @Nullable
    protected var webhook: WebhookClient<Message>? = null

    // LAZY EVALUATED
    @JvmField
    @Nullable
    protected var altContent: String? = null

    @JvmField
    @Nullable
    protected var strippedContent: String? = null

    @JvmField
    @Nullable
    protected var invites: List<String>? = null

    private val mutex = Any()

    private fun checkSystem(comment: String) {
        if (type.isSystem) {
            throw IllegalStateException("Cannot $comment a system message!")
        }
    }

    private fun checkUser() {
        if (api.selfUser != author) {
            throw IllegalStateException(
                "Attempted to update message that was not sent by this account. You cannot modify other User's messages!",
            )
        }
    }

    private fun checkIntent() {
        // Checks whether access to content is limited and the message content intent is not enabled
        if (!didContentIntentWarning && !api.isIntent(GatewayIntent.MESSAGE_CONTENT)) {
            val selfUser = api.selfUser
            val isBotOwnedWebhookMessage = selfUser.applicationIdLong == applicationIdLong && isWebhookMessage
            val isRelevantMessage =
                selfUser != author &&
                    !mentions.users.contains(selfUser) &&
                    isFromGuild &&
                    !isBotOwnedWebhookMessage &&
                    !hasPrivilegedContent()

            if (isRelevantMessage) {
                didContentIntentWarning = true
                JDAImpl.LOG.warn(
                    "Attempting to access message content without GatewayIntent.MESSAGE_CONTENT.\n" +
                        "Discord now requires to explicitly enable access to this using the MESSAGE_CONTENT intent.\n" +
                        "Useful resources to learn more:\n" +
                        "\t- https://support-dev.discord.com/hc/en-us/articles/4404772028055-Message-Content-Privileged-Intent-FAQ\n" +
                        "\t- https://jda.wiki/using-jda/gateway-intents-and-member-cache-policy/\n" +
                        "\t- https://jda.wiki/using-jda/troubleshooting/" +
                        "#cannot-get-message-content-attempting-to-access-message-content-without-gatewayintent\n" +
                        "Or suppress this warning if this is intentional with Message.suppressContentIntentWarning()",
                )
            }
        }
    }

    /**
     * `true` if the message has content that depends on the MESSAGE_CONTENT intent
     */
    private fun hasPrivilegedContent(): Boolean =
        content.isNotEmpty() ||
            embeds.isNotEmpty() ||
            attachments.isNotEmpty() ||
            components.isNotEmpty() ||
            poll != null

    fun withHook(hook: WebhookClient<Message>): ReceivedMessage {
        this.webhook = hook
        return this
    }

    @Nonnull
    override fun getJDA(): JDA = api

    @Nullable
    override fun getMessageReference(): MessageReference? = messageReference

    override fun isPinned(): Boolean = pinned

    @Nonnull
    override fun pin(): AuditableRestAction<Void> {
        checkSystem("pin")
        if (isEphemeral) {
            throw IllegalStateException("Cannot pin ephemeral messages.")
        }

        if (hasChannel()) {
            return channel!!.pinMessageById(idLong)
        }

        val route = Route.Messages.PIN_MESSAGE.compile(getChannelId(), getId())
        return AuditableRestActionImpl(api, route)
    }

    @Nonnull
    override fun unpin(): AuditableRestAction<Void> {
        checkSystem("unpin")
        if (isEphemeral) {
            throw IllegalStateException("Cannot unpin ephemeral messages.")
        }

        if (hasChannel()) {
            return channel!!.unpinMessageById(idLong)
        }

        val route = Route.Messages.UNPIN_MESSAGE.compile(getChannelId(), getId())
        return AuditableRestActionImpl(api, route)
    }

    @Nonnull
    override fun addReaction(emoji: Emoji): RestAction<Void> {
        if (isEphemeral) {
            throw IllegalStateException("Cannot add reactions to ephemeral messages.")
        }

        Checks.notNull(emoji, "Emoji")

        if (hasChannel()) {
            val missingReaction =
                reactions
                    .map { it.emoji }
                    .none { it.asReactionCode == emoji.asReactionCode }

            if (missingReaction && emoji is RichCustomEmoji) {
                Checks.check(
                    emoji.canInteract(api.selfUser, channel!!),
                    "Cannot react with the provided emoji because it is not available in the current getChannel().",
                )
            }

            return channel!!.addReactionById(id, emoji)
        }

        val encoded = EncodingUtil.encodeReaction(emoji.asReactionCode)
        val route = Route.Messages.ADD_REACTION.compile(getChannelId(), getId(), encoded, "@me")
        return RestActionImpl(api, route)
    }

    @Nonnull
    override fun clearReactions(): RestAction<Void> {
        if (isEphemeral) {
            throw IllegalStateException("Cannot clear reactions from ephemeral messages.")
        }
        if (!isFromGuild) {
            throw IllegalStateException("Cannot clear reactions from a message in a Group or PrivateChannel.")
        }

        if (channel is GuildMessageChannel) {
            return channel.clearReactionsById(id)
        }

        val route = Route.Messages.REMOVE_ALL_REACTIONS.compile(getChannelId(), getId())
        return RestActionImpl(api, route)
    }

    @Nonnull
    override fun clearReactions(emoji: Emoji): RestAction<Void> {
        if (isEphemeral) {
            throw IllegalStateException("Cannot clear reactions from ephemeral messages.")
        }
        if (!isFromGuild) {
            throw IllegalStateException("Cannot clear reactions from a message in a Group or PrivateChannel.")
        }

        if (channel is GuildMessageChannel) {
            return channel.clearReactionsById(id, emoji)
        }

        val encoded = EncodingUtil.encodeReaction(emoji.asReactionCode)
        val route = Route.Messages.CLEAR_EMOJI_REACTIONS.compile(getChannelId(), getId(), encoded)
        return RestActionImpl(api, route)
    }

    @Nonnull
    override fun removeReaction(emoji: Emoji): RestAction<Void> {
        if (isEphemeral) {
            throw IllegalStateException("Cannot remove reactions from ephemeral messages.")
        }

        if (hasChannel()) {
            return channel!!.removeReactionById(id, emoji)
        }

        val encoded = EncodingUtil.encodeReaction(emoji.asReactionCode)
        val route = Route.Messages.REMOVE_REACTION.compile(getChannelId(), getId(), encoded, "@me")
        return RestActionImpl(api, route)
    }

    @Nonnull
    // Mirrors the original Java control flow, which returns early for the self-user case.
    @Suppress("ReturnCount")
    override fun removeReaction(
        emoji: Emoji,
        user: User,
    ): RestAction<Void> {
        Checks.notNull(user, "User")
        if (isEphemeral) {
            throw IllegalStateException("Cannot remove reactions from ephemeral messages.")
        }

        // check if the passed user is the SelfUser,
        // then the ChannelType doesn't matter and we can safely remove that
        if (user == api.selfUser) {
            return removeReaction(emoji)
        }

        if (!isFromGuild) {
            throw IllegalStateException("Cannot remove reactions of others from a message in a Group or PrivateChannel.")
        }

        if (channel is GuildMessageChannel) {
            return channel.removeReactionById(idLong, emoji, user)
        }

        val encoded = EncodingUtil.encodeReaction(emoji.asReactionCode)
        val route = Route.Messages.REMOVE_REACTION.compile(getChannelId(), getId(), encoded, user.id)
        return RestActionImpl(api, route)
    }

    @Nonnull
    override fun retrieveReactionUsers(
        emoji: Emoji,
        type: MessageReaction.ReactionType,
    ): ReactionPaginationAction {
        if (isEphemeral) {
            throw IllegalStateException("Cannot retrieve reactions on ephemeral messages.")
        }

        if (hasChannel()) {
            return channel!!.retrieveReactionUsersById(id, emoji, type)
        }

        Checks.notNull(type, "ReactionType")
        Checks.notNull(emoji, "Emoji")

        return ReactionPaginationActionImpl(this, emoji.asReactionCode, type)
    }

    @Nullable
    override fun getReaction(emoji: Emoji): MessageReaction? {
        Checks.notNull(emoji, "Emoji")
        val code = emoji.asReactionCode
        return reactions
            .stream()
            .filter { r -> code == r.emoji.asReactionCode }
            .findFirst()
            .orElse(null)
    }

    @Nonnull
    override fun getType(): MessageType = type

    @Nullable
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getInteraction(): Message.Interaction? = interaction

    @Nullable
    override fun getInteractionMetadata(): Message.InteractionMetadata? = interactionMetadata

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getJumpUrl(): String = Helpers.format(Message.JUMP_URL, if (isFromGuild) guildId else "@me", channelId, id)

    override fun isEdited(): Boolean = editedTime != null

    @Nullable
    override fun getTimeEdited(): OffsetDateTime? = editedTime

    @Nonnull
    override fun getAuthor(): User = author

    @Nullable
    override fun getMember(): Member? = member

    override fun getApproximatePosition(): Int {
        if (!channelType.isThread) {
            throw IllegalStateException("This message was not sent in a thread.")
        }

        return position
    }

    @Nonnull
    // Mirrors the original Java control flow, which returns early on a cached value.
    @Suppress("ReturnCount")
    override fun getContentStripped(): String {
        if (strippedContent != null) {
            return strippedContent!!
        }
        synchronized(mutex) {
            if (strippedContent != null) {
                return strippedContent!!
            }
            return MarkdownSanitizer.sanitize(contentDisplay).also { strippedContent = it }
        }
    }

    @Nonnull
    // Mirrors the original Java control flow, which returns early on a cached value.
    @Suppress("ReturnCount")
    override fun getContentDisplay(): String {
        if (altContent != null) {
            return altContent!!
        }

        synchronized(mutex) {
            if (altContent != null) {
                return altContent!!
            }
            var tmp = contentRaw
            for (user in mentions.users) {
                val name: String =
                    if (hasGuild() && guild!!.isMember(user)) {
                        guild.getMember(user)!!.effectiveName
                    } else {
                        user.name
                    }
                tmp =
                    Pattern
                        .compile("<@!?" + Pattern.quote(user.id) + '>')
                        .matcher(tmp)
                        .replaceAll("@" + Matcher.quoteReplacement(name))
            }
            for (emoji in mentions.customEmojis) {
                tmp = tmp.replace(emoji.asMention, ":" + emoji.name + ":")
            }
            for (mentionedChannel in mentions.channels) {
                tmp = tmp.replace(mentionedChannel.asMention, "#" + mentionedChannel.name)
            }
            for (mentionedRole in mentions.roles) {
                tmp = tmp.replace(mentionedRole.asMention, "@" + mentionedRole.name)
            }
            return tmp.also { altContent = it }
        }
    }

    @Nonnull
    override fun getContentRaw(): String {
        checkIntent()
        return content
    }

    @Nonnull
    // Mirrors the original Java control flow, which returns early on a cached value.
    @Suppress("ReturnCount")
    override fun getInvites(): List<String> {
        if (invites != null) {
            return invites!!
        }
        synchronized(mutex) {
            if (invites != null) {
                return invites!!
            }
            val result = ArrayList<String>()
            val m = Message.INVITE_PATTERN.matcher(contentRaw)
            while (m.find()) {
                result.add(m.group(1))
            }
            return Collections.unmodifiableList(result).also { invites = it }
        }
    }

    @Nullable
    override fun getNonce(): String? = nonce

    override fun isFromType(type: ChannelType): Boolean = channelType == type

    override fun isFromGuild(): Boolean = guildId != 0L

    @Nonnull
    override fun getChannelType(): ChannelType = channel?.type ?: ChannelType.UNKNOWN

    @Nonnull
    override fun getChannel(): MessageChannelUnion {
        if (channel != null) {
            return channel as MessageChannelUnion
        }
        throw IllegalStateException("Channel is unavailable in this context. Use getChannelIdLong() instead!")
    }

    @Nonnull
    override fun getGuildChannel(): GuildMessageChannelUnion {
        if (channel == null || channel is GuildMessageChannelUnion) {
            return getChannel() as GuildMessageChannelUnion
        }
        throw IllegalStateException("This message was not sent in a guild.")
    }

    @Nullable
    override fun getCategory(): Category? {
        var channel: Channel? = this.channel
        if (channel is ThreadChannel) {
            channel = channel.parentChannel
        }

        return if (channel is ICategorizableChannel) channel.parentCategory else null
    }

    override fun hasGuild(): Boolean = guild != null

    override fun getGuildIdLong(): Long = guildId

    @Nonnull
    override fun getGuild(): Guild {
        if (guild == null) {
            val channelType = channelType
            if (channelType == ChannelType.UNKNOWN || channelType.isGuild) {
                throw IllegalStateException(
                    "This message instance does not provide a guild instance! Use getGuildId() instead.",
                )
            } else {
                throw IllegalStateException("This message was not sent in a guild")
            }
        }
        return guild
    }

    @Nonnull
    override fun getAttachments(): List<Attachment> {
        checkIntent()
        return attachments
    }

    @Nonnull
    override fun getEmbeds(): List<MessageEmbed> {
        checkIntent()
        return embeds
    }

    @Nonnull
    override fun getComponents(): List<MessageTopLevelComponentUnion> {
        checkIntent()
        return components
    }

    override fun isUsingComponentsV2(): Boolean = (this.flags and MessageFlag.IS_COMPONENTS_V2.value) != 0

    @Nullable
    override fun getPoll(): MessagePoll? {
        checkIntent()
        return poll
    }

    @Nonnull
    override fun endPoll(): AuditableRestAction<Message> {
        checkUser()
        if (poll == null) {
            throw IllegalStateException("This message does not contain a poll")
        }
        return AuditableRestActionImpl<Message>(
            api,
            Route.Messages.END_POLL.compile(getChannelId(), getId()),
        ) { response, _ ->
            val entityBuilder = api.entityBuilder
            if (hasChannel()) {
                return@AuditableRestActionImpl entityBuilder.createMessageWithChannel(response.getObject(), channel!!, false)
            }
            entityBuilder.createMessageFromWebhook(response.getObject(), if (hasGuild()) guild else null)
        }
    }

    @Nonnull
    override fun getMentions(): Mentions = mentions

    @Nonnull
    override fun getReactions(): List<MessageReaction> = reactions

    @Nonnull
    override fun getStickers(): List<StickerItem> = stickers

    @Nonnull
    override fun getMessageSnapshots(): List<MessageSnapshot> = messageSnapshots

    override fun isWebhookMessage(): Boolean = fromWebhook

    override fun getApplicationIdLong(): Long = applicationId

    override fun hasChannel(): Boolean = channel != null

    override fun getChannelIdLong(): Long = channelId

    override fun isTTS(): Boolean = isTTS

    @Nullable
    override fun getActivity(): MessageActivity? = activity

    @Nonnull
    override fun editMessage(newContent: CharSequence): MessageEditAction {
        val action = editRequest()
        action.setContent(newContent.toString())

        if (isWebhookRequest()) {
            return action.withHook(webhook!!, channelType, channelId)
        }

        checkSystem("edit")
        checkUser()

        return action.setContent(newContent.toString())
    }

    @Nonnull
    override fun editMessageEmbeds(embeds: Collection<MessageEmbed>): MessageEditAction {
        val action = editRequest()
        action.setEmbeds(embeds)

        if (isWebhookRequest()) {
            return action.withHook(webhook!!, channelType, channelId)
        }

        checkSystem("edit")
        checkUser()

        return action
    }

    @Nonnull
    override fun editMessageComponents(components: Collection<MessageTopLevelComponent>): MessageEditAction {
        val action = editRequest()
        action.setComponents(components)

        if (isWebhookRequest()) {
            return action.withHook(webhook!!, channelType, channelId)
        }

        checkSystem("edit")
        checkUser()

        return action
    }

    @Nonnull
    override fun editMessageFormat(
        format: String,
        vararg args: Any,
    ): MessageEditAction {
        val action = editRequest()
        action.setContent(String.format(format, *args))

        if (isWebhookRequest()) {
            return action.withHook(webhook!!, channelType, channelId)
        }

        checkSystem("edit")
        checkUser()

        return action
    }

    @Nonnull
    override fun editMessageAttachments(attachments: Collection<AttachedFile>): MessageEditAction {
        val action = editRequest()
        action.setAttachments(attachments)

        if (isWebhookRequest()) {
            return action.withHook(webhook!!, channelType, channelId)
        }

        checkSystem("edit")
        checkUser()

        return action
    }

    @Nonnull
    override fun editMessage(newContent: MessageEditData): MessageEditAction {
        val action = editRequest()
        action.applyData(newContent)

        if (isWebhookRequest()) {
            return action.withHook(webhook!!, channelType, channelId)
        }

        checkSystem("edit")
        checkUser()

        return action
    }

    @Nonnull
    // Mirrors the original Java control flow, which validates each precondition with a direct throw.
    @Suppress("ThrowsCount")
    override fun delete(): AuditableRestAction<Void> {
        if (!type.canDelete()) {
            throw IllegalStateException("Cannot delete messages of type $type")
        }

        if (isWebhookRequest()) {
            var route = Route.Webhooks.EXECUTE_WEBHOOK_DELETE.compile(webhook!!.id, webhook!!.token, getId())
            route = withThreadContext(route)

            val action = AuditableRestActionImpl<Void>(api, route)
            action.setErrorMapper(unknownWebhookErrorMapper)
            return action
        }

        val self = api.selfUser
        val isSelfAuthored = self == author

        if (!isSelfAuthored && !isFromGuild) {
            throw IllegalStateException("Cannot delete another User's messages in a PrivateChannel.")
        }

        if (isEphemeral) {
            throw IllegalStateException("Cannot delete ephemeral messages.")
        }

        if (channel is GuildMessageChannel && !isSelfAuthored) {
            val gChan = channel
            val sMember = getGuild().selfMember
            Checks.checkAccess(sMember, gChan)
            if (!sMember.hasPermission(gChan, Permission.MESSAGE_MANAGE)) {
                throw InsufficientPermissionException(gChan, Permission.MESSAGE_MANAGE)
            }
        }

        val route = Route.Messages.DELETE_MESSAGE.compile(getChannelId(), getId())
        return AuditableRestActionImpl(api, route)
    }

    @Nonnull
    // Mirrors the original Java control flow, which validates each precondition with a direct throw.
    @Suppress("ThrowsCount")
    override fun suppressEmbeds(suppressed: Boolean): AuditableRestAction<Void> {
        val self = api.selfUser

        var route: Route.CompiledRoute
        if (isWebhookRequest()) {
            route = Route.Webhooks.EXECUTE_WEBHOOK_EDIT.compile(webhook!!.id, webhook!!.token, getId())
            route = withThreadContext(route)
        } else {
            if (isEphemeral) {
                throw IllegalStateException("Cannot suppress embeds on ephemeral messages.")
            }

            if (self != author) {
                if (!isFromGuild) {
                    throw PermissionException("Cannot suppress embeds of others in a PrivateChannel.")
                }

                if (hasChannel()) {
                    val gChan = guildChannel
                    if (!getGuild().selfMember.hasPermission(gChan, Permission.MESSAGE_MANAGE)) {
                        throw InsufficientPermissionException(gChan, Permission.MESSAGE_MANAGE)
                    }
                }
            }

            route = Route.Messages.EDIT_MESSAGE.compile(getChannelId(), getId())
        }

        var newFlags = flags
        val suppressionValue = MessageFlag.EMBEDS_SUPPRESSED.value
        newFlags =
            if (suppressed) {
                newFlags or suppressionValue
            } else {
                newFlags and suppressionValue.inv()
            }
        val body = DataObject.empty().put("flags", newFlags)

        val action = AuditableRestActionImpl<Void>(api, route, body)
        action.setErrorMapper(unknownWebhookErrorMapper)
        return action
    }

    @Nonnull
    // Mirrors the original Java control flow, which validates each precondition with a direct throw.
    @Suppress("ThrowsCount", "ReturnCount")
    override fun crosspost(): RestAction<Message> {
        if (isEphemeral) {
            throw IllegalStateException("Cannot crosspost ephemeral messages.")
        }

        if (getFlags().contains(MessageFlag.CROSSPOSTED)) {
            return CompletedRestAction<Message>(api, this)
        }

        if (!hasChannel()) {
            val route = Route.Messages.CROSSPOST_MESSAGE.compile(getChannelId(), getId())
            return RestActionImpl<Message>(api, route) { response, _ ->
                api.entityBuilder.createMessageFromWebhook(response.getObject(), guild)
            }
        }

        val channel = getChannel()
        if (channel !is NewsChannel) {
            throw IllegalStateException("This message was not sent in a news channel")
        }
        Checks.checkAccess(getGuild().selfMember, channel)
        if (author != api.selfUser &&
            !getGuild().selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE)
        ) {
            throw InsufficientPermissionException(channel, Permission.MESSAGE_MANAGE)
        }
        return channel.crosspostMessageById(id)
    }

    override fun isSuppressedEmbeds(): Boolean = (this.flags and MessageFlag.EMBEDS_SUPPRESSED.value) > 0

    @Nonnull
    override fun getFlags(): EnumSet<MessageFlag> = MessageFlag.fromBitField(flags)

    override fun getFlagsRaw(): Long = flags.toLong()

    override fun isEphemeral(): Boolean = (this.flags and MessageFlag.EPHEMERAL.value) != 0

    override fun isSuppressedNotifications(): Boolean = (this.flags and MessageFlag.NOTIFICATIONS_SUPPRESSED.value) != 0

    override fun isVoiceMessage(): Boolean = (this.flags and MessageFlag.IS_VOICE_MESSAGE.value) != 0

    @Nullable
    override fun getStartedThread(): ThreadChannel? = startedThread

    override fun createThreadChannel(name: String): ThreadChannelAction = guildChannel.asThreadContainer().createThreadChannel(name, idLong)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ReceivedMessage) {
            return false
        }
        return this.id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    @Suppress("DEPRECATION")
    override fun toString(): String =
        EntityString(this)
            .addMetadata("author", if (author.discriminator == "0000") author.name else author.asTag)
            .addMetadata("content", String.format("%.20s ...", this))
            .toString()

    override fun formatTo(
        formatter: Formatter,
        flags: Int,
        width: Int,
        precision: Int,
    ) {
        val upper = (flags and FormattableFlags.UPPERCASE) == FormattableFlags.UPPERCASE
        val leftJustified = (flags and FormattableFlags.LEFT_JUSTIFY) == FormattableFlags.LEFT_JUSTIFY
        val alt = (flags and FormattableFlags.ALTERNATE) == FormattableFlags.ALTERNATE

        var out = if (alt) contentRaw else contentDisplay

        if (upper) {
            out = out.uppercase(formatter.locale())
        }

        try {
            val appendable = formatter.out()
            if (precision > -1 && out.length > precision) {
                appendable.append(Helpers.truncate(out, precision - ELLIPSIS_LENGTH)).append("...")
                return
            }

            if (leftJustified) {
                appendable.append(Helpers.rightPad(out, width))
            } else {
                appendable.append(Helpers.leftPad(out, width))
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun isWebhookRequest(): Boolean =
        webhook != null &&
            (!(webhook is InteractionHook) || !(webhook as InteractionHook).isExpired)

    @Nonnull
    private fun editRequest(): MessageEditActionImpl {
        val messageEditAction =
            if (hasChannel()) {
                MessageEditActionImpl(getChannel(), getId())
            } else {
                MessageEditActionImpl(api, if (hasGuild()) guild else null, getChannelId(), getId())
            }

        messageEditAction.setErrorMapper(unknownWebhookErrorMapper)
        return messageEditAction
    }

    private fun withThreadContext(route: Route.CompiledRoute): Route.CompiledRoute =
        if (channelType.isThread && webhook !is InteractionHook) {
            route.withQueryParams("thread_id", getChannelId())
        } else {
            route
        }

    private val unknownWebhookErrorMapper: ErrorMapper?
        get() {
            if (!isWebhookRequest()) {
                return null
            }

            return ErrorMapper { _, _, exception ->
                if (webhook is InteractionHookImpl &&
                    !(webhook as InteractionHookImpl).isAck() &&
                    exception.errorResponse == ErrorResponse.UNKNOWN_WEBHOOK
                ) {
                    IllegalStateException(
                        "Sending a webhook request requires the interaction to be acknowledged before expiration",
                        exception,
                    )
                } else {
                    null
                }
            }
        }

    companion object {
        private const val ELLIPSIS_LENGTH = 3

        @JvmField
        var didContentIntentWarning: Boolean = false
    }
}
