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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildWelcomeScreen
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.GuildWelcomeScreenManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.Collections
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

class GuildWelcomeScreenManagerImpl(
    private val guild: Guild,
) : ManagerBase<GuildWelcomeScreenManager>(
        guild.getJDA(),
        Route.Guilds.MODIFY_WELCOME_SCREEN.compile(guild.getId()),
    ),
    GuildWelcomeScreenManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var enabled: Boolean = false

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var description: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val channels: MutableList<GuildWelcomeScreen.Channel> =
        ArrayList(GuildWelcomeScreen.MAX_WELCOME_CHANNELS)

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): GuildWelcomeScreenManagerImpl {
        super.reset(fields)
        if (fields and GuildWelcomeScreenManager.ENABLED == GuildWelcomeScreenManager.ENABLED) {
            enabled = false // Most important is the flag being removed anyway
        }
        if (fields and GuildWelcomeScreenManager.DESCRIPTION == GuildWelcomeScreenManager.DESCRIPTION) {
            description = null
        }
        if (fields and GuildWelcomeScreenManager.CHANNELS == GuildWelcomeScreenManager.CHANNELS) {
            channels.clear()
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): GuildWelcomeScreenManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): GuildWelcomeScreenManagerImpl {
        super.reset(GuildWelcomeScreenManager.ENABLED or GuildWelcomeScreenManager.DESCRIPTION or GuildWelcomeScreenManager.CHANNELS)
        return this
    }

    @Nonnull
    override fun setEnabled(enabled: Boolean): GuildWelcomeScreenManager {
        this.enabled = enabled
        set = set or GuildWelcomeScreenManager.ENABLED
        return this
    }

    @Nonnull
    override fun setDescription(
        @Nullable description: String?,
    ): GuildWelcomeScreenManager {
        if (description != null) {
            Checks.notLonger(description, GuildWelcomeScreen.MAX_DESCRIPTION_LENGTH, "Description")
        }
        this.description = description
        set = set or GuildWelcomeScreenManager.DESCRIPTION
        return this
    }

    @Nonnull
    override fun getWelcomeChannels(): List<GuildWelcomeScreen.Channel> = Collections.unmodifiableList(channels)

    @Nonnull
    override fun clearWelcomeChannels(): GuildWelcomeScreenManager {
        withLock(channels) { it.clear() }
        set = set or GuildWelcomeScreenManager.CHANNELS
        return this
    }

    @Nonnull
    override fun setWelcomeChannels(
        @Nonnull channels: Collection<GuildWelcomeScreen.Channel>,
    ): GuildWelcomeScreenManager {
        Checks.noneNull(channels, "Welcome channels")
        Checks.check(
            channels.size <= GuildWelcomeScreen.MAX_WELCOME_CHANNELS,
            "Cannot have more than %d welcome channels",
            GuildWelcomeScreen.MAX_WELCOME_CHANNELS,
        )
        withLock(this.channels) { c ->
            c.clear()
            c.addAll(channels)
        }
        set = set or GuildWelcomeScreenManager.CHANNELS
        return this
    }

    override fun finalizeData(): RequestBody {
        val obj = DataObject.empty()
        if (shouldUpdate(GuildWelcomeScreenManager.ENABLED)) {
            obj.put("enabled", enabled)
        }
        if (shouldUpdate(GuildWelcomeScreenManager.DESCRIPTION)) {
            obj.put("description", description)
        }
        withLock(channels) { list ->
            if (shouldUpdate(GuildWelcomeScreenManager.CHANNELS)) {
                obj.put("welcome_channels", DataArray.fromCollection(list))
            }
        }
        reset()
        return getRequestBody(obj)
    }

    override fun checkPermissions(): Boolean {
        if (!getGuild().getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            throw InsufficientPermissionException(getGuild(), Permission.MANAGE_SERVER)
        }
        return super.checkPermissions()
    }
}
