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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.StageInstance
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.managers.channel.concrete.StageChannelManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.StageInstanceAction
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractStandardGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IInteractionPermissionMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.StageChannelMixin
import net.dv8tion.jda.internal.interactions.ChannelInteractionPermissions
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DetachedStageChannelImpl(
    id: Long,
    guild: Guild,
) : AbstractStandardGuildChannelImpl<DetachedStageChannelImpl>(id, guild),
    StageChannel,
    StageChannelMixin<DetachedStageChannelImpl>,
    IInteractionPermissionMixin<DetachedStageChannelImpl> {
    private var interactionPermissionsValue: ChannelInteractionPermissions? = null

    private var region: String? = null
    private var bitrate: Int = 0
    private var userlimit: Int = 0
    private var slowmode: Int = 0
    private var ageRestricted: Boolean = false
    private var latestMessageId: Long = 0

    override fun checkCanAccess(): Unit = throw detachedException()

    override fun isDetached(): Boolean = true

    @Nonnull
    override fun getType(): ChannelType = ChannelType.STAGE

    override fun getBitrate(): Int = bitrate

    override fun getUserLimit(): Int = userlimit

    @Nullable
    override fun getRegionRaw(): String? = region

    @Nullable
    override fun getStageInstance(): StageInstance? = throw detachedException()

    @Nonnull
    override fun getMembers(): List<Member> = throw detachedException()

    @Nonnull
    override fun createStageInstance(
        @Nonnull topic: String,
    ): StageInstanceAction = throw detachedException()

    override fun getSlowmode(): Int = slowmode

    override fun isNSFW(): Boolean = ageRestricted

    override fun canTalk(
        @Nonnull member: Member,
    ): Boolean {
        Checks.notNull(member, "Member")
        return member.hasPermission(this, Permission.MESSAGE_SEND)
    }

    override fun getLatestMessageIdLong(): Long = latestMessageId

    @Nonnull
    override fun getManager(): StageChannelManager = throw detachedException()

    @Nonnull
    override fun requestToSpeak(): RestAction<Void> = throw detachedException()

    @Nonnull
    override fun cancelRequestToSpeak(): RestAction<Void> = throw detachedException()

    @Nonnull
    override val interactionPermissions: ChannelInteractionPermissions
        get() = interactionPermissionsValue!!

    override fun setBitrate(bitrate: Int): DetachedStageChannelImpl {
        this.bitrate = bitrate
        return this
    }

    override fun setUserLimit(userlimit: Int): DetachedStageChannelImpl {
        this.userlimit = userlimit
        return this
    }

    override fun setRegion(region: String): DetachedStageChannelImpl {
        this.region = region
        return this
    }

    override fun setNSFW(ageRestricted: Boolean): DetachedStageChannelImpl {
        this.ageRestricted = ageRestricted
        return this
    }

    override fun setSlowmode(slowmode: Int): DetachedStageChannelImpl {
        this.slowmode = slowmode
        return this
    }

    override fun setLatestMessageIdLong(latestMessageId: Long): DetachedStageChannelImpl {
        this.latestMessageId = latestMessageId
        return this
    }

    @Nonnull
    override fun setInteractionPermissions(
        @Nonnull interactionPermissions: ChannelInteractionPermissions,
    ): DetachedStageChannelImpl {
        this.interactionPermissionsValue = interactionPermissions
        return this
    }
}
