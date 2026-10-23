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

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageReference
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.sticker.GuildSticker
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.data.SerializableData
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.message.MessageCreateBuilderMixin
import okhttp3.RequestBody
import java.security.SecureRandom
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull

class MessageCreateActionImpl(
    private val channel: MessageChannel,
) : RestActionImpl<Message>(channel.jda, Route.Messages.SEND_MESSAGE.compile(channel.id)),
    MessageCreateAction,
    MessageCreateBuilderMixin<MessageCreateAction> {
    private val builder = MessageCreateBuilder()
    private val stickers: MutableList<String> = ArrayList()
    private var nonce: String? = null
    private var messageReference: MessageReferenceData? = null
    private var myFailOnInvalidReply = defaultFailOnInvalidReply

    override fun getBuilder(): MessageCreateBuilder = builder

    override fun finalizeData(): RequestBody? {
        if (builder.isUsingComponentsV2) {
            Checks.check(stickers.isEmpty(), "Cannot send stickers when using Components V2!")
        }

        if (builder.isEmpty) {
            // Special cases where builder is empty but can still send message on this endpoint
            val body = DataObject.empty().put("flags", builder.messageFlagsRaw)
            populateBody(body)

            if (stickers.isNotEmpty() ||
                messageReference != null &&
                messageReference!!.type == MessageReference.MessageReferenceType.FORWARD
            ) {
                return getRequestBody(body)
            }

            throw IllegalStateException(
                "Cannot build empty messages! Must provide at least one of: content, embed, file, poll, or stickers",
            )
        }

        builder.build().use { data ->
            val json = data.toData()
            populateBody(json)

            return getMultipartBody(data.allDistinctFiles, json)
        }
    }

    private fun populateBody(json: DataObject) {
        json.put("enforce_nonce", true)
        if (nonce != null && !nonce!!.isEmpty()) {
            json.put("nonce", nonce)
        } else {
            json.put("nonce", java.lang.Long.toUnsignedString(nonceGenerator.nextLong()))
        }
        if (stickers.isNotEmpty()) {
            json.put("sticker_ids", stickers)
        }
        if (messageReference != null) {
            json.put(
                "message_reference",
                messageReference!!.toData().put("fail_if_not_exists", myFailOnInvalidReply),
            )
        }
    }

    override fun handleSuccess(
        response: Response,
        request: Request<Message>,
    ) {
        request.onSuccess(api.entityBuilder.createMessageWithChannel(response.getObject(), channel, false))
    }

    @Nonnull
    override fun setNonce(nonce: String?): MessageCreateAction {
        if (nonce != null) {
            Checks.notLonger(nonce, Message.MAX_NONCE_LENGTH, "Nonce")
        }
        this.nonce = nonce
        return this
    }

    @Nonnull
    override fun setMessageReference(
        @Nonnull type: MessageReference.MessageReferenceType,
        guildId: String?,
        @Nonnull channelId: String,
        @Nonnull messageId: String,
    ): MessageCreateAction {
        Checks.notNull(type, "Type")
        if (guildId != null) {
            Checks.isSnowflake(guildId, "Guild ID")
        }
        Checks.isSnowflake(channelId, "Channel ID")
        Checks.isSnowflake(messageId, "Message ID")
        Checks.check(
            type != MessageReference.MessageReferenceType.UNKNOWN,
            "Cannot create a message reference of UNKNOWN type",
        )
        this.messageReference = MessageReferenceData(type, guildId, channelId, messageId)
        return this
    }

    @Nonnull
    override fun setMessageReference(messageId: String?): MessageCreateAction {
        if (messageId == null) {
            this.messageReference = null
            return this
        }

        Checks.isSnowflake(messageId)
        var guildId: String? = null
        if (channel is GuildChannel) {
            guildId = (channel as GuildChannel).guild.id
        }
        this.messageReference =
            MessageReferenceData(
                MessageReference.MessageReferenceType.DEFAULT,
                guildId,
                channel.id,
                messageId,
            )
        return this
    }

    @Nonnull
    override fun failOnInvalidReply(fail: Boolean): MessageCreateAction {
        myFailOnInvalidReply = fail
        return this
    }

    @Nonnull
    override fun setStickers(stickers: Collection<StickerSnowflake>?): MessageCreateAction {
        this.stickers.clear()
        if (stickers == null || stickers.isEmpty()) {
            return this
        }

        if (!channel.type.isGuild) {
            throw IllegalStateException("Cannot send stickers in direct messages!")
        }

        val guildChannel = channel as GuildChannel

        Checks.noneNull(stickers, "Stickers")
        Checks.check(
            stickers.size <= Message.MAX_STICKER_COUNT,
            "Cannot send more than %d stickers in a message!",
            Message.MAX_STICKER_COUNT,
        )
        for (sticker in stickers) {
            if (sticker is GuildSticker) {
                Checks.check(
                    sticker.isAvailable,
                    "Cannot use unavailable sticker. The guild may have lost the boost level required to use this sticker!",
                )
                Checks.check(
                    sticker.guildIdLong == guildChannel.guild.idLong,
                    "Sticker must be from the same guild. Cross-guild sticker posting is not supported!",
                )
            }
        }

        this.stickers.addAll(
            stickers.stream().map { it.id }.collect(
                java.util.stream.Collectors
                    .toList(),
            ),
        )
        return this
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): MessageCreateAction = super<RestActionImpl>.setCheck(checks) as MessageCreateAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): MessageCreateAction = super<RestActionImpl>.deadline(timestamp) as MessageCreateAction

    private class MessageReferenceData(
        val type: MessageReference.MessageReferenceType,
        val guildId: String?,
        val channelId: String,
        val messageId: String,
    ) : SerializableData {
        @Nonnull
        override fun toData(): DataObject {
            val data =
                DataObject
                    .empty()
                    .put("type", type.id)
                    .put("message_id", messageId)
                    .put("channel_id", channelId)
            if (guildId != null) {
                data.put("guild_id", guildId)
            }
            return data
        }
    }

    companion object {
        @JvmField
        protected val nonceGenerator: SecureRandom = SecureRandom()

        @JvmField
        protected var defaultFailOnInvalidReply: Boolean = false

        @JvmStatic
        fun setDefaultFailOnInvalidReply(fail: Boolean) {
            defaultFailOnInvalidReply = fail
        }
    }
}
