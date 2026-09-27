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

import gnu.trove.map.TLongObjectMap
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateNameEvent
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateRolesEvent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.emoji.RichCustomEmojiImpl
import net.dv8tion.jda.internal.utils.cache.SnowflakeCacheViewImpl
import org.apache.commons.collections4.CollectionUtils
import java.util.ArrayList
import java.util.HashSet
import java.util.Objects

class GuildEmojisUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        if (!getJDA().isCacheFlagSet(CacheFlag.EMOJI)) {
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

        val array = content.getArray("emojis")
        val oldEmojis: MutableList<RichCustomEmoji>
        val newEmojis: MutableList<RichCustomEmoji>
        val emojiView: SnowflakeCacheViewImpl<RichCustomEmoji> = guild.getEmojisView()
        emojiView.writeLock().use {
            val emojiMap: TLongObjectMap<RichCustomEmoji> = emojiView.getMap()
            oldEmojis = ArrayList(emojiMap.valueCollection()) // snapshot of emoji cache
            newEmojis = ArrayList()
            for (i in 0 until array.length()) {
                val current = array.getObject(i)
                val emojiId = current.getLong("id")
                var emoji = emojiMap.get(emojiId) as RichCustomEmojiImpl?
                var oldEmoji: RichCustomEmojiImpl? = null

                if (emoji == null) {
                    emoji = RichCustomEmojiImpl(emojiId, guild)
                    newEmojis.add(emoji)
                } else {
                    // emoji is in our cache
                    // which is why we don't want to remove it in cleanup later
                    oldEmojis.remove(emoji)
                    oldEmoji = emoji.copy()
                }

                emoji
                    .setName(current.getString("name"))
                    .setAnimated(current.getBoolean("animated"))
                    .setManaged(current.getBoolean("managed"))
                // update roles
                val roles: DataArray = current.getArray("roles")

                val newRoles = emoji.getRoleSet()
                val oldRoles: MutableSet<Role> = HashSet(newRoles) // snapshot of cached roles
                for (j in 0 until roles.length()) {
                    val role = guild.getRoleById(roles.getString(j))
                    if (role != null) {
                        newRoles.add(role)
                        oldRoles.remove(role)
                    }
                }

                // cleanup old cached roles that were not found in the JSONArray
                for (r in oldRoles) {
                    // newRoles directly writes to the set contained in the emoji
                    newRoles.remove(r)
                }

                // finally, update the emoji
                emojiMap.put(emoji.getIdLong(), emoji)
                // check for updated fields and fire events
                handleReplace(oldEmoji, emoji)
            }
            for (e in oldEmojis) {
                emojiMap.remove(e.getIdLong())
            }
        }
        // cleanup old emojis that don't exist anymore
        for (e in oldEmojis) {
            getJDA().handleEvent(EmojiRemovedEvent(getJDA(), responseNumber, e))
        }

        for (e in newEmojis) {
            getJDA().handleEvent(EmojiAddedEvent(getJDA(), responseNumber, e))
        }

        return null
    }

    private fun handleReplace(
        oldEmoji: RichCustomEmoji?,
        newEmoji: RichCustomEmoji?,
    ) {
        if (oldEmoji == null || newEmoji == null) {
            return
        }

        if (!Objects.equals(oldEmoji.getName(), newEmoji.getName())) {
            getJDA().handleEvent(EmojiUpdateNameEvent(getJDA(), responseNumber, newEmoji, oldEmoji.getName()))
        }

        if (!CollectionUtils.isEqualCollection(oldEmoji.getRoles(), newEmoji.getRoles())) {
            getJDA().handleEvent(EmojiUpdateRolesEvent(getJDA(), responseNumber, newEmoji, oldEmoji.getRoles()))
        }
    }
}
