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
import net.dv8tion.jda.api.entities.StageInstance
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.channel.concrete.StageChannelManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.StageInstanceAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractStandardGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.StageChannelMixin
import net.dv8tion.jda.internal.managers.channel.concrete.StageChannelManagerImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.StageInstanceActionImpl
import net.dv8tion.jda.internal.utils.Checks
import java.time.OffsetDateTime
import java.util.EnumSet
import javax.annotation.Nonnull
import javax.annotation.Nullable

class StageChannelImpl(
    id: Long,
    guild: GuildImpl,
) : AbstractStandardGuildChannelImpl<StageChannelImpl>(id, guild),
    StageChannel,
    StageChannelMixin<StageChannelImpl> {
    private var instance: StageInstance? = null
    private var region: String? = null
    private var bitrate: Int = 0
    private var userlimit: Int = 0
    private var slowmode: Int = 0
    private var ageRestricted: Boolean = false
    private var latestMessageId: Long = 0

    override fun isDetached(): Boolean = false

    @Nonnull
    override fun getGuild(): GuildImpl = super.getGuild() as GuildImpl

    override fun checkCanAccess() {
        super<StageChannelMixin>.checkCanAccess()
    }

    @Nonnull
    override fun getType(): ChannelType = ChannelType.STAGE

    override fun getBitrate(): Int = bitrate

    override fun getUserLimit(): Int = userlimit

    @Nullable
    override fun getRegionRaw(): String? = region

    @Nullable
    override fun getStageInstance(): StageInstance? = instance

    @Nonnull
    override fun getMembers(): List<Member> = getGuild().getConnectedMembers(this)

    @Nonnull
    override fun createStageInstance(
        @Nonnull topic: String,
    ): StageInstanceAction {
        val permissions = getGuild().selfMember.getPermissions(this)
        val required =
            EnumSet.of(
                Permission.MANAGE_CHANNEL,
                Permission.VOICE_MUTE_OTHERS,
                Permission.VOICE_MOVE_OTHERS,
            )
        for (perm in required) {
            if (!permissions.contains(perm)) {
                throw InsufficientPermissionException(
                    this,
                    perm,
                    "You must be a stage moderator to create a stage instance! Missing Permission: $perm",
                )
            }
        }

        return StageInstanceActionImpl(this).setTopic(topic)
    }

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
    override fun getManager(): StageChannelManager = StageChannelManagerImpl(this)

    @Nonnull
    override fun requestToSpeak(): RestAction<Void> {
        val guild = getGuild()
        val route = Route.Guilds.UPDATE_VOICE_STATE.compile(guild.id, "@me")
        val body = DataObject.empty().put("channel_id", id)
        // Stage moderators can bypass the request queue by just unsuppressing
        if (guild.selfMember.hasPermission(this, Permission.VOICE_MUTE_OTHERS)) {
            body.putNull("request_to_speak_timestamp").put("suppress", false)
        } else {
            body.put("request_to_speak_timestamp", OffsetDateTime.now().toString())
        }

        if (this != guild.selfMember.voiceState!!.channel) {
            throw IllegalStateException("Cannot request to speak without being connected to the stage channel!")
        }
        return RestActionImpl(jda, route, body)
    }

    @Nonnull
    override fun cancelRequestToSpeak(): RestAction<Void> {
        val guild = getGuild()
        val route = Route.Guilds.UPDATE_VOICE_STATE.compile(guild.id, "@me")
        val body =
            DataObject
                .empty()
                .putNull("request_to_speak_timestamp")
                .put("suppress", true)
                .put("channel_id", id)

        if (this != guild.selfMember.voiceState!!.channel) {
            throw IllegalStateException("Cannot cancel request to speak without being connected to the stage channel!")
        }
        return RestActionImpl(jda, route, body)
    }

    override fun setBitrate(bitrate: Int): StageChannelImpl {
        this.bitrate = bitrate
        return this
    }

    override fun setUserLimit(userlimit: Int): StageChannelImpl {
        this.userlimit = userlimit
        return this
    }

    override fun setRegion(region: String): StageChannelImpl {
        this.region = region
        return this
    }

    fun setStageInstance(instance: StageInstance?): StageChannelImpl {
        this.instance = instance
        return this
    }

    override fun setNSFW(ageRestricted: Boolean): StageChannelImpl {
        this.ageRestricted = ageRestricted
        return this
    }

    override fun setSlowmode(slowmode: Int): StageChannelImpl {
        this.slowmode = slowmode
        return this
    }

    override fun setLatestMessageIdLong(latestMessageId: Long): StageChannelImpl {
        this.latestMessageId = latestMessageId
        return this
    }
}
