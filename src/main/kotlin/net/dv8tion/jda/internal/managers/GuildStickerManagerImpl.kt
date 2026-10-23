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
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import net.dv8tion.jda.api.managers.GuildStickerManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.sticker.GuildStickerImpl
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val NAME_MIN_LENGTH = 2
private const val NAME_MAX_LENGTH = 30
private const val DESCRIPTION_MIN_LENGTH = 2
private const val DESCRIPTION_MAX_LENGTH = 100
private const val TAGS_MAX_LENGTH = 200

class GuildStickerManagerImpl(
    private val guild: Guild?,
    private val guildId: Long,
    sticker: StickerSnowflake,
) : ManagerBase<GuildStickerManager>(
        guild!!.getJDA(),
        Route.Stickers.MODIFY_GUILD_STICKER.compile(java.lang.Long.toUnsignedString(guildId), sticker.getId()),
    ),
    GuildStickerManager {
    private var name: String? = null
    private var description: String? = null
    private var tags: String? = null

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    @Nullable
    override fun getGuild(): Guild? = guild

    override fun getGuildIdLong(): Long = guildId

    @Nonnull
    override fun reset(fields: Long): GuildStickerManagerImpl {
        super.reset(fields)
        if (fields and GuildStickerManager.NAME == GuildStickerManager.NAME) {
            name = null
        }
        if (fields and GuildStickerManager.DESCRIPTION == GuildStickerManager.DESCRIPTION) {
            description = null
        }
        if (fields and GuildStickerManager.TAGS == GuildStickerManager.TAGS) {
            tags = null
        }
        return this
    }

    @Nonnull
    override fun reset(
        @Nonnull vararg fields: Long,
    ): GuildStickerManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    override fun reset(): GuildStickerManagerImpl {
        super.reset()
        name = null
        description = null
        tags = null
        return this
    }

    @Nonnull
    override fun setName(
        @Nonnull name: String,
    ): GuildStickerManager {
        Checks.inRange(name, NAME_MIN_LENGTH, NAME_MAX_LENGTH, "Name")
        this.name = name
        set = set or GuildStickerManager.NAME
        return this
    }

    @Nonnull
    override fun setDescription(
        @Nonnull description: String,
    ): GuildStickerManager {
        Checks.inRange(description, DESCRIPTION_MIN_LENGTH, DESCRIPTION_MAX_LENGTH, "Description")
        this.description = description
        set = set or GuildStickerManager.DESCRIPTION
        return this
    }

    @Nonnull
    override fun setTags(
        @Nonnull tags: Collection<String>,
    ): GuildStickerManager {
        Checks.notEmpty(tags, "Tags")
        for (tag in tags) {
            Checks.notEmpty(tag, "Tags") // checks for empty and null
        }
        val csv = tags.joinToString(",")
        Checks.notLonger(csv, TAGS_MAX_LENGTH, "List of tags")
        this.tags = csv
        set = set or GuildStickerManager.TAGS
        return this
    }

    override fun finalizeData(): RequestBody {
        val obj = DataObject.empty()
        if (shouldUpdate(GuildStickerManager.NAME)) {
            obj.put("name", name)
        }
        if (shouldUpdate(GuildStickerManager.DESCRIPTION)) {
            obj.put("description", description)
        }
        if (shouldUpdate(GuildStickerManager.TAGS)) {
            obj.put("tags", tags)
        }
        reset()
        return getRequestBody(obj)
    }

    override fun checkPermissions(): Boolean {
        if (guild != null) {
            // We don't have the owner, assume we own the sticker
            GuildStickerImpl.checkCreateOrManagePermissions(guild)
        }
        return super.checkPermissions()
    }
}
