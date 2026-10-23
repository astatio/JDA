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
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.TimeUtil
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.MessageCreateActionImpl
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

private const val MAX_BULK_DELETE_IDS = 100
private const val MIN_BULK_DELETE_IDS = 2

interface GuildMessageChannelMixin<T : GuildMessageChannelMixin<T>> :
    GuildMessageChannel,
    GuildMessageChannelUnion,
    GuildChannelMixin<T>,
    MessageChannelMixin<T> {
    // ---- Default implementations of interface ----
    @Nonnull
    @CheckReturnValue
    override fun deleteMessagesByIds(
        @Nonnull messageIds: Collection<String>,
    ): RestAction<Void> {
        checkCanAccess()
        checkPermission(
            Permission.MESSAGE_MANAGE,
            "Must have MESSAGE_MANAGE in order to bulk delete messages in this channel regardless of author.",
        )

        if (messageIds.size < MIN_BULK_DELETE_IDS || messageIds.size > MAX_BULK_DELETE_IDS) {
            throw IllegalArgumentException("Must provide at least 2 or at most 100 messages to be deleted.")
        }

        val twoWeeksAgo = TimeUtil.getDiscordTimestamp((System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000)))
        for (id in messageIds) {
            Checks.check(
                MiscUtil.parseSnowflake(id) > twoWeeksAgo,
                "Message Id provided was older than 2 weeks. Id: $id",
            )
        }

        return bulkDeleteMessages(messageIds)
    }

    @Nonnull
    override fun removeReactionById(
        @Nonnull messageId: String,
        @Nonnull emoji: Emoji,
        @Nonnull user: User,
    ): RestAction<Void> {
        Checks.isSnowflake(messageId, "Message ID")
        Checks.notNull(emoji, "Emoji")
        Checks.notNull(user, "User")

        checkCanAccess()
        if (jda.selfUser != user) {
            checkPermission(Permission.MESSAGE_MANAGE)
        }

        val targetUser: String
        if (user == jda.selfUser) {
            targetUser = "@me"
        } else {
            targetUser = user.id
        }

        val route =
            Route.Messages.REMOVE_REACTION.compile(id, messageId, emoji.asReactionCode, targetUser)
        return RestActionImpl(jda, route)
    }

    @Nonnull
    override fun clearReactionsById(
        @Nonnull messageId: String,
    ): RestAction<Void> {
        Checks.isSnowflake(messageId, "Message ID")

        checkCanAccess()
        checkPermission(Permission.MESSAGE_MANAGE)

        val route = Route.Messages.REMOVE_ALL_REACTIONS.compile(id, messageId)
        return RestActionImpl(jda, route)
    }

    @Nonnull
    override fun clearReactionsById(
        @Nonnull messageId: String,
        @Nonnull emoji: Emoji,
    ): RestAction<Void> {
        Checks.notNull(messageId, "Message ID")
        Checks.notNull(emoji, "Emoji")

        checkCanAccess()
        checkPermission(Permission.MESSAGE_MANAGE)

        val route =
            Route.Messages.CLEAR_EMOJI_REACTIONS.compile(id, messageId, emoji.asReactionCode)
        return RestActionImpl(jda, route)
    }

    @Nonnull
    override fun sendStickers(
        @Nonnull stickers: Collection<StickerSnowflake>,
    ): MessageCreateAction {
        checkCanSendMessage()
        Checks.notEmpty(stickers, "Stickers")
        Checks.noneNull(stickers, "Stickers")
        return MessageCreateActionImpl(this).setStickers(stickers)
    }

    // ---- Default implementation of parent mixins hooks ----

    override fun checkCanSendMessage() {
        checkCanAccess()
        if (type.isThread) {
            checkPermission(Permission.MESSAGE_SEND_IN_THREADS)
        } else {
            checkPermission(Permission.MESSAGE_SEND)
        }
    }

    override fun checkCanSendMessageEmbeds() {
        checkCanAccess()
        checkPermission(Permission.MESSAGE_EMBED_LINKS)
    }

    override fun checkCanSendFiles() {
        checkCanAccess()
        checkPermission(Permission.MESSAGE_ATTACH_FILES)
    }

    override fun checkCanViewHistory() {
        checkCanAccess()
        checkPermission(Permission.MESSAGE_HISTORY)
    }

    override fun checkCanAddReactions() {
        checkCanAccess()
        checkPermission(Permission.MESSAGE_ADD_REACTION)
        checkPermission(Permission.MESSAGE_HISTORY, "You need MESSAGE_HISTORY to add reactions to a message")
    }

    override fun checkCanRemoveReactions() {
        checkCanAccess()
        checkPermission(Permission.MESSAGE_HISTORY, "You need MESSAGE_HISTORY to remove reactions from a message")
    }

    override fun checkCanControlMessagePins() {
        checkCanAccess()
        checkPermission(Permission.PIN_MESSAGES)
    }

    override fun canDeleteOtherUsersMessages(): Boolean = hasPermission(Permission.MESSAGE_MANAGE)
}
