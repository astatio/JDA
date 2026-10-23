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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.managers.channel.concrete.VoiceChannelManager
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractStandardGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IInteractionPermissionMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.VoiceChannelMixin
import net.dv8tion.jda.internal.interactions.ChannelInteractionPermissions
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DetachedVoiceChannelImpl(
    id: Long,
    guild: Guild,
) : AbstractStandardGuildChannelImpl<DetachedVoiceChannelImpl>(id, guild),
    VoiceChannel,
    VoiceChannelMixin<DetachedVoiceChannelImpl>,
    IInteractionPermissionMixin<DetachedVoiceChannelImpl> {
    private var interactionPermissionsValue: ChannelInteractionPermissions? = null

    private var region: String? = null
    private var status: String = ""
    private var latestMessageId: Long = 0
    private var bitrate: Int = 0
    private var userLimit: Int = 0
    private var slowmode: Int = 0
    private var nsfw: Boolean = false

    override fun checkCanAccess(): Unit = throw detachedException()

    override fun isDetached(): Boolean = true

    @Nonnull
    override fun getType(): ChannelType = ChannelType.VOICE

    override fun getBitrate(): Int = bitrate

    @Nullable
    override fun getRegionRaw(): String? = region

    override fun getUserLimit(): Int = userLimit

    override fun isNSFW(): Boolean = nsfw

    override fun getSlowmode(): Int = slowmode

    override fun getLatestMessageIdLong(): Long = latestMessageId

    @Nonnull
    override fun getMembers(): List<Member> = throw detachedException()

    @Nonnull
    override fun getManager(): VoiceChannelManager = throw detachedException()

    @Nonnull
    override fun getStatus(): String = status

    @Nonnull
    override fun modifyStatus(
        @Nonnull status: String,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override val interactionPermissions: ChannelInteractionPermissions
        get() = interactionPermissionsValue!!

    override fun setBitrate(bitrate: Int): DetachedVoiceChannelImpl {
        this.bitrate = bitrate
        return this
    }

    override fun setRegion(region: String): DetachedVoiceChannelImpl {
        this.region = region
        return this
    }

    override fun setUserLimit(userlimit: Int): DetachedVoiceChannelImpl {
        this.userLimit = userlimit
        return this
    }

    override fun setNSFW(ageRestricted: Boolean): DetachedVoiceChannelImpl {
        this.nsfw = ageRestricted
        return this
    }

    override fun setSlowmode(slowmode: Int): DetachedVoiceChannelImpl {
        this.slowmode = slowmode
        return this
    }

    override fun setLatestMessageIdLong(latestMessageId: Long): DetachedVoiceChannelImpl {
        this.latestMessageId = latestMessageId
        return this
    }

    override fun setStatus(status: String): DetachedVoiceChannelImpl {
        this.status = status
        return this
    }

    @Nonnull
    override fun setInteractionPermissions(
        @Nonnull interactionPermissions: ChannelInteractionPermissions,
    ): DetachedVoiceChannelImpl {
        this.interactionPermissionsValue = interactionPermissions
        return this
    }
}
