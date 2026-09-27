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

package net.dv8tion.jda.internal.managers

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.SoundboardSoundSnowflake
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.managers.SoundboardSoundManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val NAME_MIN_LENGTH = 2
private const val NAME_MAX_LENGTH = 32

class SoundboardSoundManagerImpl(
    private val guild: Guild,
    soundboardSound: SoundboardSoundSnowflake,
) : ManagerBase<SoundboardSoundManager>(
        guild.getJDA(),
        Route.SoundboardSounds.MODIFY_GUILD_SOUNDBOARD_SOUND.compile(guild.getId(), soundboardSound.getId()),
    ),
    SoundboardSoundManager {
    private var name: String? = null
    private var volume: Double = 0.0
    private var emoji: Emoji? = null

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): SoundboardSoundManagerImpl {
        super.reset(fields)
        if (fields and SoundboardSoundManager.NAME == SoundboardSoundManager.NAME) {
            this.name = null
        }
        if (fields and SoundboardSoundManager.VOLUME == SoundboardSoundManager.VOLUME) {
            this.volume = 1.0
        }
        if (fields and SoundboardSoundManager.EMOJI == SoundboardSoundManager.EMOJI) {
            this.emoji = null
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): SoundboardSoundManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): SoundboardSoundManagerImpl {
        super.reset()
        this.name = null
        this.volume = 1.0
        this.emoji = null
        return this
    }

    @Nonnull
    override fun setName(
        @Nonnull name: String,
    ): SoundboardSoundManagerImpl {
        Checks.notNull(name, "name")
        Checks.check(
            name.length in NAME_MIN_LENGTH..NAME_MAX_LENGTH,
            "Name must be between $NAME_MIN_LENGTH and $NAME_MAX_LENGTH characters",
        )
        this.name = name
        set = set or SoundboardSoundManager.NAME
        return this
    }

    @Nonnull
    override fun setVolume(volume: Double): SoundboardSoundManagerImpl {
        Checks.check(volume in 0.0..1.0, "Volume must be between 0 and 1")
        this.volume = volume
        set = set or SoundboardSoundManager.VOLUME
        return this
    }

    @Nonnull
    override fun setEmoji(
        @Nullable emoji: Emoji?,
    ): SoundboardSoundManagerImpl {
        this.emoji = emoji
        set = set or SoundboardSoundManager.EMOJI
        return this
    }

    override fun finalizeData(): RequestBody {
        val json = DataObject.empty()
        if (shouldUpdate(SoundboardSoundManager.NAME)) {
            json.put("name", name)
        }
        if (shouldUpdate(SoundboardSoundManager.VOLUME)) {
            json.put("volume", volume)
        }
        if (shouldUpdate(SoundboardSoundManager.EMOJI)) {
            if (emoji is CustomEmoji) {
                json.put("emoji_id", (emoji as CustomEmoji).getId())
            } else if (emoji != null) {
                json.put("emoji_name", emoji!!.getName())
            } else {
                json.put("emoji_id", null)
                json.put("emoji_name", null)
            }
        }
        reset()
        return getRequestBody(json)
    }
}
