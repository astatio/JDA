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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.requests.CompletedRestAction
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.time.OffsetDateTime
import javax.annotation.Nonnull
import javax.annotation.Nullable

class GuildVoiceStateImpl(
    member: Member,
) : GuildVoiceState {
    private val api: JDA = member.jda
    private var guild: Guild = member.guild
    private var member: Member = member

    private var connectedChannel: AudioChannel? = null
    private var sessionId: String? = null
    private var requestToSpeak: Long = 0
    private var selfMuted: Boolean = false
    private var selfDeafened: Boolean = false
    private var guildMuted: Boolean = false
    private var guildDeafened: Boolean = false
    private var suppressed: Boolean = false
    private var stream: Boolean = false
    private var video: Boolean = false

    override fun isSelfMuted(): Boolean = selfMuted

    override fun isSelfDeafened(): Boolean = selfDeafened

    @Nonnull
    override fun getJDA(): JDA = api

    @Nullable
    override fun getSessionId(): String? = sessionId

    fun getRequestToSpeak(): Long = requestToSpeak

    @Nullable
    override fun getRequestToSpeakTimestamp(): OffsetDateTime? = if (requestToSpeak == 0L) null else Helpers.toOffset(requestToSpeak)

    @Nonnull
    override fun approveSpeaker(): RestAction<Void> = update(false)

    @Nonnull
    override fun declineSpeaker(): RestAction<Void> = update(true)

    private fun update(suppress: Boolean): RestAction<Void> {
        if (connectedChannel !is StageChannel || suppress == isSuppressed) {
            return CompletedRestAction(api, null)
        }

        val channel = connectedChannel!!
        val selfMember = getGuild().selfMember
        val isSelf = selfMember == member
        if (!isSelf && !selfMember.hasPermission(channel, Permission.VOICE_MUTE_OTHERS)) {
            throw InsufficientPermissionException(channel, Permission.VOICE_MUTE_OTHERS)
        }

        val route = Route.Guilds.UPDATE_VOICE_STATE.compile(guild.id, if (isSelf) "@me" else id)
        val body = DataObject.empty().put("channel_id", channel.id).put("suppress", suppress)
        return RestActionImpl(getJDA(), route, body)
    }

    @Nonnull
    override fun inviteSpeaker(): RestAction<Void> {
        if (connectedChannel !is StageChannel) {
            return CompletedRestAction(api, null)
        }

        val channel = connectedChannel!!
        if (!getGuild().selfMember.hasPermission(channel, Permission.VOICE_MUTE_OTHERS)) {
            throw InsufficientPermissionException(channel, Permission.VOICE_MUTE_OTHERS)
        }

        val route = Route.Guilds.UPDATE_VOICE_STATE.compile(guild.id, id)
        val body =
            DataObject
                .empty()
                .put("channel_id", channel.id)
                .put("suppress", false)
                .put("request_to_speak_timestamp", OffsetDateTime.now().toString())
        return RestActionImpl(getJDA(), route, body)
    }

    override fun isMuted(): Boolean = isSelfMuted || isGuildMuted

    override fun isDeafened(): Boolean = isSelfDeafened || isGuildDeafened

    override fun isGuildMuted(): Boolean = guildMuted

    override fun isGuildDeafened(): Boolean = guildDeafened

    override fun isSuppressed(): Boolean = suppressed

    override fun isStream(): Boolean = stream

    override fun isSendingVideo(): Boolean = video

    @Nullable
    override fun getChannel(): AudioChannelUnion? = connectedChannel as AudioChannelUnion?

    @Nonnull
    override fun getGuild(): Guild {
        val realGuild = api.getGuildById(guild.idLong)
        if (realGuild != null) {
            guild = realGuild
        }
        return guild
    }

    @Nonnull
    override fun getMember(): Member {
        val realMember = getGuild().getMemberById(member.idLong)
        if (realMember != null) {
            member = realMember
        }
        return member
    }

    override fun inAudioChannel(): Boolean = getChannel() != null

    override fun getIdLong(): Long = member.idLong

    override fun hashCode(): Int = member.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is GuildVoiceState) {
            return false
        }
        return member == other.member
    }

    override fun toString(): String =
        EntityString(this)
            .addMetadata("member", getMember()) // Guild metadata is included in Member metadata
            .toString()

    // -- Setters --

    fun setMember(member: Member) {
        this.member = member
    }

    fun updateConnectedChannel(connectedChannel: AudioChannel): GuildVoiceStateImpl {
        this.connectedChannel = connectedChannel
        (guild as GuildImpl).handleVoiceStateUpdate(this)
        return this
    }

    fun setSessionId(sessionId: String): GuildVoiceStateImpl {
        this.sessionId = sessionId
        return this
    }

    fun setSelfMuted(selfMuted: Boolean): GuildVoiceStateImpl {
        this.selfMuted = selfMuted
        return this
    }

    fun setSelfDeafened(selfDeafened: Boolean): GuildVoiceStateImpl {
        this.selfDeafened = selfDeafened
        return this
    }

    fun setGuildMuted(guildMuted: Boolean): GuildVoiceStateImpl {
        this.guildMuted = guildMuted
        return this
    }

    fun setGuildDeafened(guildDeafened: Boolean): GuildVoiceStateImpl {
        this.guildDeafened = guildDeafened
        return this
    }

    fun setSuppressed(suppressed: Boolean): GuildVoiceStateImpl {
        this.suppressed = suppressed
        return this
    }

    fun setStream(stream: Boolean): GuildVoiceStateImpl {
        this.stream = stream
        return this
    }

    fun setVideo(video: Boolean): GuildVoiceStateImpl {
        this.video = video
        return this
    }

    fun setRequestToSpeak(timestamp: OffsetDateTime?): GuildVoiceStateImpl {
        this.requestToSpeak = timestamp?.toInstant()?.toEpochMilli() ?: 0L
        return this
    }
}
