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

package net.dv8tion.jda.internal.handle

import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl

class GuildSoundboardSoundsUpdateHandler(
    api: JDAImpl,
    private val soundboardSoundUpdateHandler: GuildSoundboardSoundUpdateHandler,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        if (!getJDA().isCacheFlagSet(CacheFlag.SOUNDBOARD_SOUNDS)) {
            return null
        }
        val guildId = content.getLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        val guild = getJDA().getGuildById(guildId) as GuildImpl?
        if (guild == null) {
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            return null
        }

        val array = content.getArray("soundboard_sounds")
        for (i in 0 until array.length()) {
            val payload = array.getObject(i)
            // Just in case
            payload.put("guild_id", guildId)

            soundboardSoundUpdateHandler.handle(
                responseNumber,
                DataObject.empty().put("t", "GUILD_SOUNDBOARD_SOUND_UPDATE").put("d", payload),
            )
        }

        return null
    }
}
