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

package net.dv8tion.jda.internal.entities.emoji

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.CustomEmojiManager
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.managers.CustomEmojiManagerImpl
import net.dv8tion.jda.internal.requests.DeferredRestAction
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.Nonnull

class RichCustomEmojiImpl(
    private val id: Long,
    private var guild: GuildImpl,
) : RichCustomEmoji,
    EmojiUnion {
    private val api: JDAImpl = guild.jda
    private val roles: MutableSet<Role> = ConcurrentHashMap.newKeySet()

    private var managed: Boolean = false
    private var available: Boolean = true
    private var animated: Boolean = false
    private var name: String? = null
    private var owner: User? = null

    @Nonnull
    override fun getType(): Emoji.Type = Emoji.Type.CUSTOM

    @Nonnull
    override fun getAsReactionCode(): String = "$name:$id"

    @Nonnull
    override fun toData(): DataObject =
        DataObject
            .empty()
            .put("name", name)
            .put("animated", animated)
            .put("id", id)

    @Nonnull
    override fun getGuild(): GuildImpl {
        val realGuild = api.getGuildById(guild.idLong) as GuildImpl?
        if (realGuild != null) {
            guild = realGuild
        }
        return guild
    }

    @Nonnull
    override fun getRoles(): List<Role> = Collections.unmodifiableList(ArrayList(roles))

    @Nonnull
    override fun getName(): String = name as String

    override fun isManaged(): Boolean = managed

    override fun isAvailable(): Boolean = available

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getJDA(): JDAImpl = api

    override fun getOwner(): User? = owner

    @Nonnull
    override fun retrieveOwner(): CacheRestAction<User> {
        val guild = getGuild()
        if (!guild.selfMember.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS) &&
            !guild.selfMember.hasPermission(Permission.CREATE_GUILD_EXPRESSIONS)
        ) {
            throw InsufficientPermissionException(
                guild,
                Permission.MANAGE_GUILD_EXPRESSIONS,
                "Cannot retrieve owner without either MANAGE_GUILD_EXPRESSIONS or CREATE_GUILD_EXPRESSIONS permissions",
            )
        }
        return DeferredRestAction(api, User::class.java, { owner }) {
            val route: Route.CompiledRoute = Route.Emojis.GET_EMOJI.compile(guild.id, id.toString())
            RestActionImpl(api, route) { response, _ ->
                val data = response.`object`
                if (data.isNull("user")) { // user is not provided when permissions are missing
                    throw ErrorResponseException.create(ErrorResponse.MISSING_PERMISSIONS, response)
                }
                val user = data.getObject("user")
                owner = api.entityBuilder.createUser(user)
                owner
            }
        }
    }

    @Nonnull
    override fun getManager(): CustomEmojiManager = CustomEmojiManagerImpl(this)

    override fun isAnimated(): Boolean = animated

    @Nonnull
    override fun delete(): AuditableRestAction<Void> {
        if (managed) {
            throw UnsupportedOperationException("You cannot delete a managed emoji!")
        }
        checkManagePermissions()

        val route: Route.CompiledRoute = Route.Emojis.DELETE_EMOJI.compile(guild.id, id.toString())
        return AuditableRestActionImpl(getJDA(), route)
    }

    fun checkManagePermissions() {
        val selfMember: SelfMember = getGuild().selfMember
        if (owner != null) {
            if (owner!!.idLong == selfMember.idLong) {
                checkCreateOrManagePermissions()
            } else {
                if (!selfMember.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS)) {
                    throw InsufficientPermissionException(getGuild(), Permission.MANAGE_GUILD_EXPRESSIONS)
                }
            }
        } else {
            // We don't know if we own the emoji, let's assume we do
            checkCreateOrManagePermissions()
        }
    }

    private fun checkCreateOrManagePermissions() {
        val selfMember: SelfMember = getGuild().selfMember
        if (!selfMember.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS) &&
            !selfMember.hasPermission(Permission.CREATE_GUILD_EXPRESSIONS)
        ) {
            throw InsufficientPermissionException(
                guild,
                Permission.MANAGE_GUILD_EXPRESSIONS,
                "Managing a custom emoji requires either MANAGE_GUILD_EXPRESSIONS or CREATE_GUILD_EXPRESSIONS permissions",
            )
        }
    }

    // -- Setters --

    fun setName(name: String): RichCustomEmojiImpl {
        this.name = name
        return this
    }

    fun setAnimated(animated: Boolean): RichCustomEmojiImpl {
        this.animated = animated
        return this
    }

    fun setManaged(`val`: Boolean): RichCustomEmojiImpl {
        this.managed = `val`
        return this
    }

    fun setAvailable(available: Boolean): RichCustomEmojiImpl {
        this.available = available
        return this
    }

    fun setOwner(user: User?): RichCustomEmojiImpl {
        this.owner = user
        return this
    }

    // -- Set Getter --

    fun getRoleSet(): MutableSet<Role> = roles

    // -- Object overrides --

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is CustomEmoji) {
            return false
        }
        return this.id == other.idLong
    }

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun toString(): String = EntityString(this).setName(getName()).toString()

    fun copy(): RichCustomEmojiImpl {
        val copy =
            RichCustomEmojiImpl(id, getGuild())
                .setOwner(owner)
                .setManaged(managed)
                .setAnimated(animated)
                .setName(name!!)
        copy.roles.addAll(roles)
        return copy
    }

    @Nonnull
    override fun asUnicode(): UnicodeEmoji = throw IllegalStateException("Cannot convert CustomEmoji to UnicodeEmoji!")

    @Nonnull
    override fun asCustom(): CustomEmoji = this

    @Nonnull
    override fun asRich(): RichCustomEmoji = this

    @Nonnull
    override fun asApplication(): ApplicationEmoji = throw IllegalStateException("Cannot convert RichCustomEmoji to ApplicationEmoji!")
}
