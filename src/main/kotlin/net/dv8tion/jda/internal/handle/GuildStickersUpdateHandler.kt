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
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.sticker.GuildSticker
import net.dv8tion.jda.api.events.sticker.GuildStickerAddedEvent
import net.dv8tion.jda.api.events.sticker.GuildStickerRemovedEvent
import net.dv8tion.jda.api.events.sticker.update.GuildStickerUpdateAvailableEvent
import net.dv8tion.jda.api.events.sticker.update.GuildStickerUpdateDescriptionEvent
import net.dv8tion.jda.api.events.sticker.update.GuildStickerUpdateNameEvent
import net.dv8tion.jda.api.events.sticker.update.GuildStickerUpdateTagsEvent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.sticker.GuildStickerImpl
import net.dv8tion.jda.internal.utils.Helpers
import net.dv8tion.jda.internal.utils.cache.SnowflakeCacheViewImpl
import org.apache.commons.collections4.CollectionUtils
import java.util.ArrayList
import java.util.Objects

class GuildStickersUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        if (!getJDA().isCacheFlagSet(CacheFlag.STICKER)) {
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

        val array = content.getArray("stickers")
        val oldStickers: MutableList<GuildSticker>
        val newStickers: MutableList<GuildSticker>
        val stickersView: SnowflakeCacheViewImpl<GuildSticker> = guild.getStickersView()
        val builder: EntityBuilder = api.getEntityBuilder()
        stickersView.writeLock().use {
            val stickersMap: TLongObjectMap<GuildSticker> = stickersView.getMap()
            oldStickers = ArrayList(stickersMap.valueCollection()) // snapshot of sticker cache
            newStickers = ArrayList()
            for (i in 0 until array.length()) {
                val current = array.getObject(i)
                val stickerId = current.getLong("id")
                var sticker = stickersMap.get(stickerId) as GuildStickerImpl?
                var oldSticker: GuildStickerImpl? = null

                if (sticker == null) {
                    sticker = builder.createRichSticker(current) as GuildStickerImpl
                    newStickers.add(sticker)
                } else {
                    // sticker is in our cache
                    // which is why we don't want to remove it in cleanup later
                    oldStickers.remove(sticker)
                    oldSticker = sticker.copy()
                }

                sticker.setName(current.getString("name"))
                sticker.setAvailable(current.getBoolean("available"))
                sticker.setDescription(current.getString("description", ""))
                sticker.setTags(Helpers.setOf(*current.getString("tags").split(",\\s*".toRegex()).toTypedArray()))

                // finally, update the sticker
                stickersMap.put(sticker.getIdLong(), sticker)
                // check for updated fields and fire events
                handleReplace(guild, oldSticker, sticker)
            }
            for (e in oldStickers) {
                stickersMap.remove(e.getIdLong())
            }
        }
        // cleanup old stickers that don't exist anymore
        for (e in oldStickers) {
            getJDA().handleEvent(GuildStickerRemovedEvent(getJDA(), responseNumber, guild, e))
        }

        for (e in newStickers) {
            getJDA().handleEvent(GuildStickerAddedEvent(getJDA(), responseNumber, guild, e))
        }

        return null
    }

    private fun handleReplace(
        guild: Guild,
        oldSticker: GuildStickerImpl?,
        newSticker: GuildStickerImpl?,
    ) {
        if (oldSticker == null || newSticker == null) {
            return
        }

        if (!Objects.equals(oldSticker.getName(), newSticker.getName())) {
            getJDA().handleEvent(
                GuildStickerUpdateNameEvent(getJDA(), responseNumber, guild, newSticker, oldSticker.getName()),
            )
        }

        if (!Objects.equals(oldSticker.getDescription(), newSticker.getDescription())) {
            getJDA().handleEvent(
                GuildStickerUpdateDescriptionEvent(
                    getJDA(),
                    responseNumber,
                    guild,
                    newSticker,
                    oldSticker.getDescription(),
                ),
            )
        }

        if (oldSticker.isAvailable() != newSticker.isAvailable()) {
            getJDA().handleEvent(
                GuildStickerUpdateAvailableEvent(getJDA(), responseNumber, guild, newSticker, oldSticker.isAvailable()),
            )
        }

        if (!CollectionUtils.isEqualCollection(oldSticker.getTags(), newSticker.getTags())) {
            getJDA().handleEvent(
                GuildStickerUpdateTagsEvent(getJDA(), responseNumber, guild, newSticker, oldSticker.getTags()),
            )
        }
    }
}
