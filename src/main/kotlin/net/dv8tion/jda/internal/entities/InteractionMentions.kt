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

package net.dv8tion.jda.internal.entities

import gnu.trove.map.TLongObjectMap
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.mentions.AbstractMentions
import java.util.regex.Matcher
import javax.annotation.Nullable

class InteractionMentions(
    content: String,
    @JvmField protected val resolved: TLongObjectMap<Any>,
    jda: JDAImpl,
    @Nullable guild: Guild?,
) : AbstractMentions(content, jda, guild, false) {
    override fun matchUser(matcher: Matcher): User? {
        val userId = MiscUtil.parseSnowflake(matcher.group(1))
        val it = resolved[userId]
        return when (it) {
            is User -> it
            is Member -> it.user
            else -> null
        }
    }

    override fun matchMember(matcher: Matcher): Member? {
        val userId = MiscUtil.parseSnowflake(matcher.group(1))
        val it = resolved[userId]
        return it as? Member
    }

    override fun matchChannel(matcher: Matcher): GuildChannel? {
        val channelId = MiscUtil.parseSnowflake(matcher.group(1))
        val it = resolved[channelId]
        return it as? GuildChannel
    }

    override fun matchRole(matcher: Matcher): Role? {
        val roleId = MiscUtil.parseSnowflake(matcher.group(1))
        val it = resolved[roleId]
        return it as? Role
    }

    override fun isUserMentioned(mentionable: IMentionable): Boolean =
        resolved.containsKey(mentionable.idLong) &&
            (content.contains("<@!" + mentionable.id + ">") || content.contains("<@" + mentionable.id + ">"))
}
