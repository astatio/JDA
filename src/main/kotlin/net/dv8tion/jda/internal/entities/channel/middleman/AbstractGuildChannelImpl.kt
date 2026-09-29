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

package net.dv8tion.jda.internal.entities.channel.middleman

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelFlag
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.channel.AbstractChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.GuildChannelMixin
import net.dv8tion.jda.internal.utils.ChannelUtil
import java.util.EnumSet
import javax.annotation.Nonnull

abstract class AbstractGuildChannelImpl<T : AbstractGuildChannelImpl<T>>(
    id: Long,
    guild: Guild,
) : AbstractChannelImpl<T>(id, guild.jda),
    GuildChannelMixin<T> {
    private var guild: Guild = guild

    @JvmField
    protected var flags: Int = 0

    @Nonnull
    override fun getGuild(): Guild {
        val cachedGuild: Guild? = jda.getGuildById(guild.idLong)
        if (cachedGuild is GuildImpl) {
            return cachedGuild.also { this.guild = it }
        }
        return guild
    }

    override fun compareTo(
        @Nonnull other: GuildChannel,
    ): Int = ChannelUtil.compare(this, other)

    // Explicit overrides avoid Kotlin emitting bridge methods here; Java subclasses overriding
    // these would otherwise trigger -Xlint:overrides.
    override fun checkCanAccess() {
        super.checkCanAccess()
    }

    override fun checkCanManage() {
        super.checkCanManage()
    }

    @Nonnull
    override fun getFlags(): EnumSet<ChannelFlag> = ChannelFlag.fromRaw(flags)

    override fun getFlagsRaw(): Long = flags.toLong()

    @Suppress("UNCHECKED_CAST")
    override fun setFlags(flags: Int): T {
        this.flags = flags
        return this as T
    }
}
