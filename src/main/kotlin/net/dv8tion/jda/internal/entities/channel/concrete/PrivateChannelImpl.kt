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

package net.dv8tion.jda.internal.entities.channel.concrete

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelFlag
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.internal.entities.channel.AbstractChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.PrivateChannelMixin
import java.util.EnumSet
import javax.annotation.Nonnull
import javax.annotation.Nullable

@Suppress("EmptyFunctionBlock") // no-op permission hooks, matching the Java originals
class PrivateChannelImpl(
    api: JDA,
    id: Long,
    @Nullable user: User?,
) : AbstractChannelImpl<PrivateChannelImpl>(id, api),
    PrivateChannel,
    PrivateChannelMixin<PrivateChannelImpl> {
    private var user: User? = user
    private var latestMessageId: Long = 0

    override fun isDetached(): Boolean = false

    @Nonnull
    override fun getType(): ChannelType = ChannelType.PRIVATE

    @Nullable
    override fun getUser(): User? {
        updateUser()
        return user
    }

    @Nonnull
    override fun getFlags(): EnumSet<ChannelFlag> = EnumSet.noneOf(ChannelFlag::class.java)

    override fun getFlagsRaw(): Long = 0L

    @Nonnull
    override fun getName(): String = super<PrivateChannelMixin>.getName()

    override fun getLatestMessageIdLong(): Long = latestMessageId

    override fun canTalk(): Boolean {
        // The only way user is null is when an event is dispatched that doesn't give us enough information
        // to build the recipient user, which only happens if this bot sends a message (or otherwise triggers
        // an event) from a shard other than shard 0. The event is received on shard 0 without enough
        // information to build the recipient user. Since events only happen in this channel between the bot
        // and the user, a null user is a valid channel state. Events cannot happen between a bot and another
        // bot, so the user would never be null in that case.
        return user == null || !user!!.isBot
    }

    override fun checkCanAccess() {}

    override fun checkCanSendMessage() {
        checkBot()
    }

    override fun checkCanSendMessageEmbeds() {}

    override fun checkCanSendFiles() {}

    override fun checkCanViewHistory() {}

    override fun checkCanAddReactions() {}

    override fun checkCanRemoveReactions() {}

    override fun checkCanControlMessagePins() {}

    override fun canDeleteOtherUsersMessages(): Boolean = false

    fun setUser(user: User?) {
        this.user = user
    }

    override fun setLatestMessageIdLong(latestMessageId: Long): PrivateChannelImpl {
        this.latestMessageId = latestMessageId
        return this
    }

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is PrivateChannelImpl) {
            return false
        }
        return other.id == this.id
    }

    private fun updateUser() {
        // if the user is null then we don't even know their ID, and so we have to check that first
        val user = user ?: return
        // Load user from cache if one exists, otherwise we might have an outdated user instance
        val realUser = jda.getUserById(user.idLong)
        if (realUser != null) {
            this.user = realUser
        }
    }

    private fun checkBot() {
        // The only way user is null is when an event is dispatched that doesn't give us enough information
        // to build the recipient user, which only happens if this bot sends a message (or otherwise triggers
        // an event) from a shard other than shard 0. The event is received on shard 0 without enough
        // information to build the recipient user. Since events only happen in this channel between the bot
        // and the user, a null user is a valid channel state. Events cannot happen between a bot and another
        // bot, so the user would never be null in that case.
        if (getUser() != null && getUser()!!.isBot) {
            throw UnsupportedOperationException("Cannot send a private message between bots.")
        }
    }
}
