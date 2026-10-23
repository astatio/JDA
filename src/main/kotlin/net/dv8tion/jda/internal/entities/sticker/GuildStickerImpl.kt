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

package net.dv8tion.jda.internal.entities.sticker

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.sticker.GuildSticker
import net.dv8tion.jda.api.entities.sticker.Sticker.StickerFormat
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.GuildStickerManager
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.managers.GuildStickerManagerImpl
import net.dv8tion.jda.internal.requests.DeferredRestAction
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class GuildStickerImpl(
    id: Long,
    format: StickerFormat,
    name: String,
    tags: Set<String>,
    description: String,
    private var available: Boolean,
    private val guildId: Long,
    private val jda: JDA,
    private var owner: User?,
) : RichStickerImpl(id, format, name, tags, description),
    GuildSticker {
    private var guild: Guild? = jda.getGuildById(guildId)

    @Nonnull
    override fun asGuildSticker(): GuildSticker = this

    override fun isAvailable(): Boolean = available

    override fun getGuildIdLong(): Long = guildId

    @Nullable
    override fun getGuild(): Guild? {
        val realGuild = jda.getGuildById(guildId)
        if (realGuild != null) {
            guild = realGuild
        }
        return guild
    }

    @Nullable
    override fun getOwner(): User? {
        if (owner != null) {
            val realOwner = jda.getUserById(owner!!.idLong)
            if (realOwner != null) {
                owner = realOwner
            }
        }
        return owner
    }

    @Nonnull
    override fun retrieveOwner(): CacheRestAction<User> {
        val guild = getGuild()
        if (guild != null) {
            checkCreateOrManagePermissions(
                guild,
                "Retrieving sticker owner requires either MANAGE_GUILD_EXPRESSIONS or CREATE_GUILD_EXPRESSIONS permissions",
            )
        }
        return DeferredRestAction(jda, User::class.java, { owner }) {
            val route: Route.CompiledRoute = Route.Stickers.GET_GUILD_STICKER.compile(getGuildId(), getId())
            RestActionImpl(jda, route) { response, _ ->
                val json = response.`object`
                val user =
                    json
                        .optObject("user")
                        .map { (jda as JDAImpl).entityBuilder.createUser(json.getObject("user")) }
                        .orElseThrow {
                            ErrorResponseException.create(ErrorResponse.MISSING_PERMISSIONS, response)
                        }
                owner = user
                user
            }
        }
    }

    @Nonnull
    override fun delete(): AuditableRestAction<Void> {
        val guild = getGuild()
        if (guild != null) {
            checkManagePermissions(guild, owner)
        }
        val route: Route.CompiledRoute = Route.Stickers.DELETE_GUILD_STICKER.compile(getGuildId(), getId())
        return AuditableRestActionImpl(jda, route)
    }

    @Nonnull
    override fun getManager(): GuildStickerManager = GuildStickerManagerImpl(getGuild(), getGuildIdLong(), this)

    fun setAvailable(available: Boolean): GuildStickerImpl {
        this.available = available
        return this
    }

    fun copy(): GuildStickerImpl = GuildStickerImpl(id, format, name, tags, description, available, guildId, jda, owner)

    override fun toString(): String =
        EntityString(this)
            .setName(name)
            .addMetadata("guild", getGuildId())
            .toString()

    override fun hashCode(): Int = Objects.hash(id, format, name, type, tags, description, available, guildId)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is GuildStickerImpl) {
            return false
        }
        return id == other.id &&
            format == other.format &&
            type == other.type &&
            available == other.available &&
            guildId == other.guildId &&
            Objects.equals(name, other.name) &&
            Objects.equals(description, other.description) &&
            Helpers.deepEqualsUnordered(tags, other.tags)
    }

    companion object {
        @JvmStatic
        private fun checkManagePermissions(
            @Nonnull guild: Guild,
            @Nullable owner: UserSnowflake?,
        ) {
            val selfMember: SelfMember = guild.selfMember
            if (owner != null) {
                if (owner.idLong == selfMember.idLong) {
                    checkCreateOrManagePermissions(guild)
                } else {
                    if (!selfMember.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS)) {
                        throw InsufficientPermissionException(guild, Permission.MANAGE_GUILD_EXPRESSIONS)
                    }
                }
            } else {
                // We don't know if we own the sticker, let's assume we do
                checkCreateOrManagePermissions(guild)
            }
        }

        @JvmStatic
        fun checkCreateOrManagePermissions(
            @Nonnull guild: Guild,
        ) {
            checkCreateOrManagePermissions(
                guild,
                "Managing a sticker requires either MANAGE_GUILD_EXPRESSIONS or CREATE_GUILD_EXPRESSIONS permissions",
            )
        }

        @JvmStatic
        private fun checkCreateOrManagePermissions(
            @Nonnull guild: Guild,
            @Nonnull message: String,
        ) {
            val selfMember: SelfMember = guild.selfMember
            if (!selfMember.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS) &&
                !selfMember.hasPermission(Permission.CREATE_GUILD_EXPRESSIONS)
            ) {
                throw InsufficientPermissionException(guild, Permission.MANAGE_GUILD_EXPRESSIONS, message)
            }
        }
    }
}
