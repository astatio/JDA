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
import net.dv8tion.jda.api.entities.SoundboardSound
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.attribute.ISoundboardSoundChannel
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.SoundboardSoundManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.internal.managers.SoundboardSoundManagerImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.PermissionUtil
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class SoundboardSoundImpl(
    private val api: JDA,
    id: Long,
    private val name: String,
    private val volume: Double,
    private val emoji: EmojiUnion?,
    private val guild: Guild?,
    private val available: Boolean,
    private val user: User?,
) : SoundboardSoundSnowflakeImpl(id),
    SoundboardSound {
    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun getName(): String = name

    override fun getVolume(): Double = volume

    @Nullable
    override fun getEmoji(): EmojiUnion? = emoji

    @Nullable
    override fun getGuild(): Guild? = guild

    override fun isAvailable(): Boolean = available

    @Nullable
    override fun getUser(): User? = user

    @Nonnull
    override fun sendTo(
        @Nonnull channel: ISoundboardSoundChannel,
    ): RestAction<Void> {
        Checks.notNull(channel, "Channel")

        // Check available
        if (!isAvailable) {
            throw IllegalStateException("Cannot send an unavailable sound")
        }

        // For non-default sounds
        if (guild != null) {
            // Check additional speak permissions
            // This isn't done in ISoundboardSoundChannel as we wouldn't know the origin guild
            val targetGuild = channel.guild
            if (targetGuild != guild &&
                !targetGuild.selfMember.hasPermission(channel, Permission.VOICE_USE_EXTERNAL_SOUNDS)
            ) {
                throw InsufficientPermissionException(channel, Permission.VOICE_USE_EXTERNAL_SOUNDS)
            }

            return channel.sendSoundboardSound(this, guild.idLong)
        } else {
            return channel.sendSoundboardSound(this, null)
        }
    }

    @Nonnull
    override fun delete(): AuditableRestAction<Void> {
        Checks.check(guild != null, "Cannot delete default soundboard sounds")
        checkEditPermissions()

        return guild!!.deleteSoundboardSound(this)
    }

    @Nonnull
    override fun getManager(): SoundboardSoundManager {
        Checks.check(guild != null, "Cannot edit default soundboard sounds")
        checkEditPermissions()

        return SoundboardSoundManagerImpl(guild!!, this)
    }

    private fun checkEditPermissions() {
        val selfMember = guild!!.selfMember
        if (Objects.equals(getUser(), selfMember.user)) {
            PermissionUtil.requireAnyPermission(
                selfMember,
                Permission.CREATE_GUILD_EXPRESSIONS,
                Permission.MANAGE_GUILD_EXPRESSIONS,
            )
        } else if (!selfMember.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS)) {
            throw InsufficientPermissionException(guild, Permission.MANAGE_GUILD_EXPRESSIONS)
        }
    }

    override fun toString(): String = EntityString(this).setName(name).toString()
}
