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

import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.managers.CustomEmojiManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.emoji.RichCustomEmojiImpl
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.ArrayList
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val NAME_MIN_LENGTH = 2
private const val NAME_MAX_LENGTH = 32

class CustomEmojiManagerImpl(
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField protected val emoji: RichCustomEmojiImpl,
) : ManagerBase<CustomEmojiManager>(emoji.getJDA(), Route.Emojis.MODIFY_EMOJI.compile(emoji.getGuild().getId(), emoji.getId())),
    CustomEmojiManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val roles: MutableList<String> = ArrayList()

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var name: String? = null

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    @Nonnull
    override fun getEmoji(): RichCustomEmoji = emoji

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): CustomEmojiManagerImpl {
        super.reset(fields)
        if (fields and CustomEmojiManager.ROLES == CustomEmojiManager.ROLES) {
            withLock(this.roles) { it.clear() }
        }
        if (fields and CustomEmojiManager.NAME == CustomEmojiManager.NAME) {
            this.name = null
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): CustomEmojiManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): CustomEmojiManagerImpl {
        super.reset()
        withLock(this.roles) { it.clear() }
        this.name = null
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): CustomEmojiManagerImpl {
        Checks.notBlank(name, "Name")
        val trimmed = name.trim()
        Checks.inRange(trimmed, NAME_MIN_LENGTH, NAME_MAX_LENGTH, "Name")
        this.name = trimmed
        set = set or CustomEmojiManager.NAME
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setRoles(
        @Nullable roles: Set<Role>?,
    ): CustomEmojiManagerImpl {
        if (roles == null) {
            withLock(this.roles) { it.clear() }
        } else {
            Checks.notNull(roles, "Roles")
            roles.forEach { role ->
                Checks.notNull(role, "Roles")
                Checks.check(role.getGuild() == getGuild(), "Roles must all be from the same guild")
            }
            withLock(this.roles) { list ->
                list.clear()
                roles.map { it.getId() }.forEach { list.add(it) }
            }
        }
        set = set or CustomEmojiManager.ROLES
        return this
    }

    override fun finalizeData(): RequestBody {
        val json = DataObject.empty()
        if (shouldUpdate(CustomEmojiManager.NAME)) {
            json.put("name", name)
        }
        withLock(this.roles) { list ->
            if (shouldUpdate(CustomEmojiManager.ROLES)) {
                json.put("roles", DataArray.fromCollection(list))
            }
        }
        reset()
        return getRequestBody(json)
    }

    override fun checkPermissions(): Boolean {
        emoji.checkManagePermissions()
        return super.checkPermissions()
    }
}
