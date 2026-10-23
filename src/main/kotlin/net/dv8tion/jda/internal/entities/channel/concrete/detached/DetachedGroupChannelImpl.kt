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
import net.dv8tion.jda.api.entities.channel.ChannelFlag
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.internal.entities.channel.AbstractChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.GroupChannelMixin
import net.dv8tion.jda.internal.entities.detached.mixin.IDetachableEntityMixin
import java.util.EnumSet
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DetachedGroupChannelImpl(
    api: JDA,
    id: Long,
) : AbstractChannelImpl<DetachedGroupChannelImpl>(id, api),
    GroupChannelMixin<DetachedGroupChannelImpl>,
    IDetachableEntityMixin {
    private var latestMessageId: Long = 0
    private var ownerId: Long = 0
    private var icon: String? = null

    @Nonnull
    override fun getFlags(): EnumSet<ChannelFlag> = EnumSet.noneOf(ChannelFlag::class.java)

    override fun getFlagsRaw(): Long = 0L

    @Nonnull
    override fun getType(): ChannelType = ChannelType.GROUP

    override fun isDetached(): Boolean = true

    override fun getOwnerIdLong(): Long = ownerId

    @Nullable
    override fun getIconId(): String? = icon

    override fun getLatestMessageIdLong(): Long = latestMessageId

    override fun canTalk(): Boolean = false

    override fun checkCanAccess(): Unit = throw detachedException()

    override fun checkCanSendMessage(): Unit = throw detachedException()

    override fun checkCanSendMessageEmbeds(): Unit = throw detachedException()

    override fun checkCanSendFiles(): Unit = throw detachedException()

    override fun checkCanViewHistory(): Unit = throw detachedException()

    override fun checkCanAddReactions(): Unit = throw detachedException()

    override fun checkCanRemoveReactions(): Unit = throw detachedException()

    override fun checkCanControlMessagePins(): Unit = throw detachedException()

    override fun canDeleteOtherUsersMessages(): Boolean = false

    override fun setLatestMessageIdLong(latestMessageId: Long): DetachedGroupChannelImpl {
        this.latestMessageId = latestMessageId
        return this
    }

    override fun setOwnerId(ownerId: Long): DetachedGroupChannelImpl {
        this.ownerId = ownerId
        return this
    }

    override fun setIcon(
        @Nullable icon: String?,
    ): DetachedGroupChannelImpl {
        this.icon = icon
        return this
    }
}
