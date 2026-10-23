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

package net.dv8tion.jda.internal.entities.channel.mixin.concrete

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.Region
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.SoundboardSoundSnowflake
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IAgeRestrictedChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.ISlowmodeChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IWebhookContainerMixin
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.AudioChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.GuildMessageChannelMixin
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull
import javax.annotation.Nullable

interface VoiceChannelMixin<T : VoiceChannelMixin<T>> :
    VoiceChannel,
    GuildMessageChannelMixin<T>,
    AudioChannelMixin<T>,
    IWebhookContainerMixin<T>,
    IAgeRestrictedChannelMixin<T>,
    ISlowmodeChannelMixin<T> {
    override fun canTalk(
        @Nonnull member: Member,
    ): Boolean {
        Checks.notNull(member, "Member")
        return member.hasPermission(this, Permission.MESSAGE_SEND)
    }

    @Nonnull
    override fun createCopy(
        @Nonnull guild: Guild,
    ): ChannelAction<VoiceChannel> {
        Checks.notNull(guild, "Guild")

        val action =
            guild
                .createVoiceChannel(name)
                .setBitrate(bitrate)
                .setUserlimit(userLimit)

        if (regionRaw != null) {
            action.setRegion(Region.fromKey(regionRaw))
        }

        if (guild == this.guild) {
            val parent = parentCategory
            if (parent != null) {
                action.setParent(parent)
            }
            for (o in permissionOverrideMap.valueCollection()) {
                if (o.isMemberOverride) {
                    action.addMemberPermissionOverride(o.idLong, o.allowedRaw, o.deniedRaw)
                } else {
                    action.addRolePermissionOverride(o.idLong, o.allowedRaw, o.deniedRaw)
                }
            }
        }
        return action
    }

    @Nonnull
    @Suppress("ThrowsCount") // ported verbatim from the Java original; the multiple guards mirror its validation
    override fun sendSoundboardSound(
        @Nonnull sound: SoundboardSoundSnowflake,
        @Nullable sourceGuildId: String?,
    ): RestAction<Void> {
        Checks.notNull(sound, "Sound")
        if (sourceGuildId != null) {
            Checks.isSnowflake(sourceGuildId, "Source guild ID")
        }

        // Check speak permissions
        val targetGuild = guild
        if (!targetGuild.selfMember.hasPermission(this, Permission.VOICE_SPEAK)) {
            throw InsufficientPermissionException(this, Permission.VOICE_SPEAK)
        }
        if (!targetGuild.selfMember.hasPermission(this, Permission.VOICE_USE_SOUNDBOARD)) {
            throw InsufficientPermissionException(this, Permission.VOICE_USE_SOUNDBOARD)
        }

        // Check voice state, self member's voice state should always be cached, but guard just in case
        val voiceState: GuildVoiceState? = targetGuild.selfMember.voiceState
        if (voiceState != null) {
            if (this != voiceState.channel) {
                throw IllegalStateException(
                    "You must be connected to the voice channel you want to send the sound effect to",
                )
            }
            if (voiceState.isSuppressed) {
                throw IllegalStateException("You cannot send sound effects while you are being suppressed")
            }
            if (voiceState.isDeafened) {
                throw IllegalStateException("You cannot send sound effects while you are deafened")
            }
            if (voiceState.isMuted) {
                throw IllegalStateException("You cannot send sound effects while you are muted")
            }
        }

        // Send
        val data = DataObject.empty().put("sound_id", id)
        if (sourceGuildId != null) {
            data.put("source_guild_id", sourceGuildId)
        }

        return RestActionImpl(jda, Route.SoundboardSounds.SEND_SOUNDBOARD_SOUND.compile(this.id), data)
    }

    fun setStatus(status: String): T
}
