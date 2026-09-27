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

package net.dv8tion.jda.internal.entities.channel.concrete.detached

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelFlag
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.exceptions.DetachedEntityException
import net.dv8tion.jda.internal.entities.channel.AbstractChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.PrivateChannelMixin
import java.util.EnumSet
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DetachedPrivateChannelImpl(
    api: JDA,
    id: Long,
    @Nullable private val user: User?,
) : AbstractChannelImpl<DetachedPrivateChannelImpl>(id, api),
    PrivateChannel,
    PrivateChannelMixin<DetachedPrivateChannelImpl> {
    private var latestMessageId: Long = 0

    @Nonnull
    override fun detachedException(): DetachedEntityException = DetachedEntityException("Cannot perform action in friend DMs")

    override fun isDetached(): Boolean = true

    @Nonnull
    override fun getType(): ChannelType = ChannelType.PRIVATE

    @Nullable
    override fun getUser(): User? = user

    @Nonnull
    override fun getFlags(): EnumSet<ChannelFlag> = EnumSet.noneOf(ChannelFlag::class.java)

    override fun getFlagsRaw(): Long = 0L

    @Nonnull
    override fun getName(): String = super<PrivateChannelMixin>.getName()

    override fun getLatestMessageIdLong(): Long = latestMessageId

    override fun canTalk(): Boolean = false

    override fun checkCanAccess(): Unit = throw detachedException()

    override fun checkCanSendMessage() {
        throw detachedException() // Should be checked by checkCanAccess first!
    }

    override fun checkCanSendMessageEmbeds() {
        throw detachedException() // Should be checked by checkCanSendMessage first!
    }

    override fun checkCanSendFiles() {
        throw detachedException() // Should be checked by checkCanSendMessage first!
    }

    override fun checkCanViewHistory(): Unit = throw detachedException()

    override fun checkCanAddReactions(): Unit = throw detachedException()

    override fun checkCanRemoveReactions(): Unit = throw detachedException()

    override fun checkCanControlMessagePins(): Unit = throw detachedException()

    override fun canDeleteOtherUsersMessages(): Boolean = false

    override fun setLatestMessageIdLong(latestMessageId: Long): DetachedPrivateChannelImpl {
        this.latestMessageId = latestMessageId
        return this
    }

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is DetachedPrivateChannelImpl) {
            return false
        }
        return other.id == this.id
    }
}
