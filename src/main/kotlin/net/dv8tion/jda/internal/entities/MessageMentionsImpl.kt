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
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.mentions.AbstractMentions
import java.util.Collections
import java.util.regex.Matcher
import java.util.stream.Collectors
import javax.annotation.Nonnull

class MessageMentionsImpl(
    jda: JDAImpl,
    guild: GuildImpl,
    content: String,
    mentionsEveryone: Boolean,
    userMentions: DataArray,
    roleMentions: DataArray,
) : AbstractMentions(content, jda, guild, mentionsEveryone) {
    private val userMentionMap: TLongObjectMap<DataObject> = TLongObjectHashMap(userMentions.length())
    private val roleMentionMap: TLongSet =
        TLongHashSet(roleMentions.stream { arr, i -> arr.getUnsignedLong(i) }.collect(Collectors.toList()))

    init {
        userMentions.stream { arr, i -> arr.getObject(i) }.forEach { obj ->
            if (obj.isNull("member")) {
                userMentionMap.put(obj.getUnsignedLong("id"), obj.put("is_member", false))
                return@forEach
            }

            val member = obj.getObject("member")
            obj.remove("member")
            member.put("user", obj).put("is_member", true)
            userMentionMap.put(obj.getUnsignedLong("id"), member)
        }

        // Eager parsing member mentions for caching purposes
        getMembers()
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    @Nonnull
    @Synchronized
    override fun getMembers(): List<Member> {
        if (guild == null) {
            return emptyList()
        }
        if (mentionedMembers != null) {
            return mentionedMembers!!
        }

        // Parse members from mentions array in order of appearance
        val entityBuilder = jda.entityBuilder
        val unseen = TLongHashSet(userMentionMap.keySet())
        val members =
            processMentions(
                Message.MentionType.USER,
                false,
                { matcher ->
                    if (unseen.remove(java.lang.Long.parseUnsignedLong(matcher.group(1)))) matchMember(matcher) else null
                },
                { ArrayList<Member>() },
            )

        // Add reply mentions at beginning
        val iter = unseen.iterator()
        while (iter.hasNext()) {
            val mention = userMentionMap.get(iter.next())
            if (mention.getBoolean("is_member")) {
                members.add(0, entityBuilder.createMember(guild as GuildImpl, mention))
            }
        }

        // Update member cache
        members.map(MemberImpl::class.java::cast).forEach(entityBuilder::updateMemberCache)

        return Collections.unmodifiableList(members).also { mentionedMembers = it }
    }

    @Nonnull
    @Synchronized
    override fun getUsers(): List<User> {
        if (mentionedUsers != null) {
            return mentionedUsers!!
        }

        // Parse members from mentions array in order of appearance
        val entityBuilder = jda.entityBuilder
        val unseen = TLongHashSet(userMentionMap.keySet())
        val users =
            processMentions(
                Message.MentionType.USER,
                false,
                { matcher ->
                    if (unseen.remove(java.lang.Long.parseUnsignedLong(matcher.group(1)))) matchUser(matcher) else null
                },
                { ArrayList<User>() },
            )

        // Add reply mentions at beginning
        val iter = unseen.iterator()
        while (iter.hasNext()) {
            val mention = userMentionMap.get(iter.next())
            if (mention.getBoolean("is_member")) {
                users.add(0, entityBuilder.createUser(mention.getObject("user")))
            } else {
                users.add(0, entityBuilder.createUser(mention))
            }
        }

        return Collections.unmodifiableList(users).also { mentionedUsers = it }
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    override fun matchUser(matcher: Matcher): User? {
        val userId = MiscUtil.parseSnowflake(matcher.group(1))
        val mention = userMentionMap.get(userId) ?: return null
        if (!mention.getBoolean("is_member")) {
            return jda.entityBuilder.createUser(mention)
        }
        val member = matchMember(matcher)
        return member?.user
    }

    override fun matchMember(matcher: Matcher): Member? {
        val id = java.lang.Long.parseUnsignedLong(matcher.group(1))
        val member = userMentionMap.get(id)
        return if (member != null && member.getBoolean("is_member")) {
            jda.entityBuilder.createMember(guild as GuildImpl, member)
        } else {
            null
        }
    }

    override fun matchChannel(matcher: Matcher): GuildChannel? {
        val channelId = MiscUtil.parseSnowflake(matcher.group(1))
        return getJDA().getGuildChannelById(channelId)
    }

    override fun matchRole(matcher: Matcher): Role? {
        val roleId = MiscUtil.parseSnowflake(matcher.group(1))
        if (!roleMentionMap.contains(roleId)) {
            return null
        }
        return if (guild != null) {
            guild.getRoleById(roleId)
        } else {
            getJDA().getRoleById(roleId)
        }
    }

    override fun isUserMentioned(mentionable: IMentionable): Boolean = userMentionMap.containsKey(mentionable.idLong)
}
