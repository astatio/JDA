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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.IVoiceStatusChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.managers.channel.concrete.VoiceChannelManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractStandardGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.VoiceChannelMixin
import net.dv8tion.jda.internal.managers.channel.concrete.VoiceChannelManagerImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull
import javax.annotation.Nullable

class VoiceChannelImpl(
    id: Long,
    guild: GuildImpl,
) : AbstractStandardGuildChannelImpl<VoiceChannelImpl>(id, guild),
    VoiceChannel,
    VoiceChannelMixin<VoiceChannelImpl> {
    private var region: String? = null
    private var status: String = ""
    private var latestMessageId: Long = 0
    private var bitrate: Int = 0
    private var userLimit: Int = 0
    private var slowmode: Int = 0
    private var nsfw: Boolean = false

    override fun isDetached(): Boolean = false

    @Nonnull
    override fun getGuild(): GuildImpl = super.getGuild() as GuildImpl

    override fun checkCanAccess() {
        super<VoiceChannelMixin>.checkCanAccess()
    }

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
    override fun getMembers(): List<Member> = getGuild().getConnectedMembers(this)

    @Nonnull
    override fun getManager(): VoiceChannelManager = VoiceChannelManagerImpl(this)

    @Nonnull
    override fun getStatus(): String = status

    @Nonnull
    override fun modifyStatus(
        @Nonnull status: String,
    ): AuditableRestAction<Void> {
        Checks.notLonger(status, IVoiceStatusChannel.MAX_STATUS_LENGTH, "Voice Status")
        checkCanAccess()
        if (this == getGuild().selfMember.voiceState!!.channel) {
            checkPermission(Permission.VOICE_SET_STATUS)
        } else {
            checkCanManage()
        }

        val route = Route.Channels.SET_STATUS.compile(getId())
        val body = DataObject.empty().put("status", status)
        return AuditableRestActionImpl(api, route, body)
    }

    override fun setBitrate(bitrate: Int): VoiceChannelImpl {
        this.bitrate = bitrate
        return this
    }

    override fun setRegion(region: String): VoiceChannelImpl {
        this.region = region
        return this
    }

    override fun setUserLimit(userlimit: Int): VoiceChannelImpl {
        this.userLimit = userlimit
        return this
    }

    override fun setNSFW(ageRestricted: Boolean): VoiceChannelImpl {
        this.nsfw = ageRestricted
        return this
    }

    override fun setSlowmode(slowmode: Int): VoiceChannelImpl {
        this.slowmode = slowmode
        return this
    }

    override fun setLatestMessageIdLong(latestMessageId: Long): VoiceChannelImpl {
        this.latestMessageId = latestMessageId
        return this
    }

    override fun setStatus(status: String): VoiceChannelImpl {
        this.status = status
        return this
    }
}
