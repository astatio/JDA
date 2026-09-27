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

package net.dv8tion.jda.internal.entities.channel.mixin.middleman

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.MessageHistory
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.PinnedMessagePaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction
import net.dv8tion.jda.api.utils.AttachedFile
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.TimeUtil
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData
import net.dv8tion.jda.api.utils.messages.MessagePollData
import net.dv8tion.jda.internal.entities.channel.mixin.ChannelMixin
import net.dv8tion.jda.internal.requests.RestActionImpl
import java.util.TreeSet
import java.util.concurrent.CompletableFuture
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

private const val BULK_DELETE_CHUNK_SIZE = 100

interface MessageChannelMixin<T : MessageChannelMixin<T>> :
    MessageChannel,
    MessageChannelUnion,
    ChannelMixin<T> {
    // ---- Default implementations of interface ----
    @Nonnull
    override fun purgeMessages(
        @Nonnull messages: List<Message>,
    ): List<CompletableFuture<Void>> {
        checkCanAccess()
        if (messages.isEmpty()) {
            return emptyList()
        }

        if (!canDeleteOtherUsersMessages()) {
            for (m in messages) {
                if (m.author == jda.selfUser) {
                    continue
                }

                if (type == ChannelType.PRIVATE) {
                    throw IllegalStateException("Cannot delete messages of other users in a private channel")
                } else {
                    throw InsufficientPermissionException(
                        this as GuildChannel,
                        Permission.MESSAGE_MANAGE,
                        "Cannot delete messages of other users",
                    )
                }
            }
        }

        return super<MessageChannelUnion>.purgeMessages(messages)
    }

    @Nonnull
    @Suppress("ReturnCount") // ported verbatim from the Java original; matching its early-return control flow
    override fun purgeMessagesById(
        @Nonnull vararg messageIds: Long,
    ): List<CompletableFuture<Void>> {
        checkCanAccess()
        if (messageIds.isEmpty()) {
            return emptyList()
        }

        // If we can't use the bulk delete system, then use the standard purge defined in
        // MessageChannel
        if (!canDeleteOtherUsersMessages()) {
            return super<MessageChannelUnion>.purgeMessagesById(*messageIds)
        }

        // remove duplicates and sort messages
        val list = ArrayList<CompletableFuture<Void>>()
        val bulk = TreeSet<Long>(Comparator.reverseOrder())
        val norm = TreeSet<Long>(Comparator.reverseOrder())
        val twoWeeksAgo =
            TimeUtil.getDiscordTimestamp(System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000) + 10000)
        for (messageId in messageIds) {
            if (messageId > twoWeeksAgo) { // Bulk delete cannot delete messages older than 2 weeks.
                bulk.add(messageId)
            } else {
                norm.add(messageId)
            }
        }

        // delete chunks of 100 messages each
        if (!bulk.isEmpty()) {
            val toDelete = ArrayList<String>(BULK_DELETE_CHUNK_SIZE)
            while (!bulk.isEmpty()) {
                toDelete.clear()
                for (i in 0 until BULK_DELETE_CHUNK_SIZE) {
                    if (bulk.isEmpty()) break
                    toDelete.add(java.lang.Long.toUnsignedString(bulk.pollLast()))
                }

                // If we only had 1 in the bulk collection
                // then use the standard deleteMessageById request
                // as you cannot bulk delete a single message
                if (toDelete.size == 1) {
                    list.add(deleteMessageById(toDelete[0]).submit())
                } else if (toDelete.isNotEmpty()) {
                    list.add(bulkDeleteMessages(toDelete).submit())
                }
            }
        }

        // delete messages too old for bulk delete
        if (!norm.isEmpty()) {
            for (message in norm) {
                list.add(deleteMessageById(message).submit())
            }
        }
        return list
    }

    @Nonnull
    @CheckReturnValue
    override fun sendMessage(
        @Nonnull text: CharSequence,
    ): MessageCreateAction {
        checkCanSendMessage()
        return super<MessageChannelUnion>.sendMessage(text)
    }

    @Nonnull
    @CheckReturnValue
    override fun sendMessageEmbeds(
        @Nonnull embed: MessageEmbed,
        @Nonnull vararg other: MessageEmbed,
    ): MessageCreateAction {
        checkCanSendMessage()
        checkCanSendMessageEmbeds()
        return super<MessageChannelUnion>.sendMessageEmbeds(embed, *other)
    }

    @Nonnull
    @CheckReturnValue
    override fun sendMessageEmbeds(
        @Nonnull embeds: Collection<MessageEmbed>,
    ): MessageCreateAction {
        checkCanSendMessage()
        checkCanSendMessageEmbeds()
        return super<MessageChannelUnion>.sendMessageEmbeds(embeds)
    }

    @Nonnull
    override fun sendMessageComponents(
        @Nonnull components: Collection<MessageTopLevelComponent>,
    ): MessageCreateAction {
        checkCanSendMessage()
        return super<MessageChannelUnion>.sendMessageComponents(components)
    }

    @Nonnull
    override fun sendMessagePoll(
        @Nonnull poll: MessagePollData,
    ): MessageCreateAction {
        checkCanSendMessage()
        return super<MessageChannelUnion>.sendMessagePoll(poll)
    }

    @Nonnull
    @CheckReturnValue
    override fun sendMessage(
        @Nonnull msg: MessageCreateData,
    ): MessageCreateAction {
        checkCanSendMessage()
        return super<MessageChannelUnion>.sendMessage(msg)
    }

    @Nonnull
    @CheckReturnValue
    override fun sendFiles(
        @Nonnull files: Collection<FileUpload>,
    ): MessageCreateAction {
        checkCanSendMessage()
        checkCanSendFiles()
        return super<MessageChannelUnion>.sendFiles(files)
    }

    @Nonnull
    @CheckReturnValue
    override fun retrieveMessageById(
        @Nonnull messageId: String,
    ): RestAction<Message> {
        checkCanViewHistory()
        return super<MessageChannelUnion>.retrieveMessageById(messageId)
    }

    @Nonnull
    @CheckReturnValue
    override fun deleteMessageById(
        @Nonnull messageId: String,
    ): AuditableRestAction<Void> {
        checkCanAccess()
        // We don't know if this is a Message sent by us or another user, so we can't run checks for
        // Permission.MESSAGE_MANAGE
        return super<MessageChannelUnion>.deleteMessageById(messageId)
    }

    @Nonnull
    override fun getHistory(): MessageHistory {
        checkCanViewHistory()
        return super<MessageChannelUnion>.history
    }

    @Nonnull
    @CheckReturnValue
    override fun getIterableHistory(): MessagePaginationAction {
        checkCanViewHistory()
        return super<MessageChannelUnion>.iterableHistory
    }

    @Nonnull
    @CheckReturnValue
    override fun getHistoryAround(
        @Nonnull messageId: String,
        limit: Int,
    ): MessageHistory.MessageRetrieveAction {
        checkCanViewHistory()
        return super<MessageChannelUnion>.getHistoryAround(messageId, limit)
    }

    @Nonnull
    @CheckReturnValue
    override fun getHistoryAfter(
        @Nonnull messageId: String,
        limit: Int,
    ): MessageHistory.MessageRetrieveAction {
        checkCanViewHistory()
        return super<MessageChannelUnion>.getHistoryAfter(messageId, limit)
    }

    @Nonnull
    @CheckReturnValue
    override fun getHistoryBefore(
        @Nonnull messageId: String,
        limit: Int,
    ): MessageHistory.MessageRetrieveAction {
        checkCanViewHistory()
        return super<MessageChannelUnion>.getHistoryBefore(messageId, limit)
    }

    @Nonnull
    @CheckReturnValue
    override fun getHistoryFromBeginning(limit: Int): MessageHistory.MessageRetrieveAction {
        checkCanViewHistory()
        return MessageHistory.getHistoryFromBeginning(this).limit(limit)
    }

    @Nonnull
    @CheckReturnValue
    override fun sendTyping(): RestAction<Void> {
        checkCanAccess()
        return super<MessageChannelUnion>.sendTyping()
    }

    @Nonnull
    @CheckReturnValue
    override fun addReactionById(
        @Nonnull messageId: String,
        @Nonnull emoji: Emoji,
    ): RestAction<Void> {
        checkCanAddReactions()
        return super<MessageChannelUnion>.addReactionById(messageId, emoji)
    }

    @Nonnull
    @CheckReturnValue
    override fun removeReactionById(
        @Nonnull messageId: String,
        @Nonnull emoji: Emoji,
    ): RestAction<Void> {
        checkCanRemoveReactions()
        return super<MessageChannelUnion>.removeReactionById(messageId, emoji)
    }

    @Nonnull
    @CheckReturnValue
    override fun retrieveReactionUsersById(
        @Nonnull messageId: String,
        @Nonnull emoji: Emoji,
    ): ReactionPaginationAction {
        checkCanRemoveReactions()
        return super<MessageChannelUnion>.retrieveReactionUsersById(messageId, emoji)
    }

    @Nonnull
    @CheckReturnValue
    override fun pinMessageById(
        @Nonnull messageId: String,
    ): AuditableRestAction<Void> {
        checkCanControlMessagePins()
        return super<MessageChannelUnion>.pinMessageById(messageId)
    }

    @Nonnull
    @CheckReturnValue
    override fun unpinMessageById(
        @Nonnull messageId: String,
    ): AuditableRestAction<Void> {
        checkCanControlMessagePins()
        return super<MessageChannelUnion>.unpinMessageById(messageId)
    }

    @Nonnull
    @CheckReturnValue
    override fun retrievePinnedMessages(): PinnedMessagePaginationAction {
        checkCanAccess()
        return super<MessageChannelUnion>.retrievePinnedMessages()
    }

    @Nonnull
    @CheckReturnValue
    override fun editMessageById(
        @Nonnull messageId: String,
        @Nonnull newContent: CharSequence,
    ): MessageEditAction {
        checkCanSendMessage()
        return super<MessageChannelUnion>.editMessageById(messageId, newContent)
    }

    @Nonnull
    @CheckReturnValue
    override fun editMessageById(
        @Nonnull messageId: String,
        @Nonnull data: MessageEditData,
    ): MessageEditAction {
        checkCanSendMessage()
        return super<MessageChannelUnion>.editMessageById(messageId, data)
    }

    @Nonnull
    @CheckReturnValue
    override fun editMessageEmbedsById(
        @Nonnull messageId: String,
        @Nonnull newEmbeds: Collection<MessageEmbed>,
    ): MessageEditAction {
        checkCanSendMessage()
        checkCanSendMessageEmbeds()
        return super<MessageChannelUnion>.editMessageEmbedsById(messageId, newEmbeds)
    }

    @Nonnull
    @CheckReturnValue
    override fun editMessageComponentsById(
        @Nonnull messageId: String,
        @Nonnull components: Collection<MessageTopLevelComponent>,
    ): MessageEditAction {
        checkCanSendMessage()
        return super<MessageChannelUnion>.editMessageComponentsById(messageId, components)
    }

    @Nonnull
    override fun editMessageAttachmentsById(
        @Nonnull messageId: String,
        @Nonnull attachments: Collection<AttachedFile>,
    ): MessageEditAction {
        checkCanSendMessage()
        return super<MessageChannelUnion>.editMessageAttachmentsById(messageId, attachments)
    }

    // ---- State Accessors ----
    fun setLatestMessageIdLong(latestMessageId: Long): T

    // ---- Mixin Hooks ----
    fun checkCanSendMessage()

    fun checkCanSendMessageEmbeds()

    fun checkCanSendFiles()

    fun checkCanViewHistory()

    fun checkCanAddReactions()

    fun checkCanRemoveReactions()

    fun checkCanControlMessagePins()

    fun canDeleteOtherUsersMessages(): Boolean

    // ---- Helpers -----
    fun bulkDeleteMessages(messageIds: Collection<String>): RestActionImpl<Void> {
        val body = DataObject.empty().put("messages", messageIds)
        val route = Route.Messages.DELETE_MESSAGES.compile(id)
        return RestActionImpl(jda, route, body)
    }
}
