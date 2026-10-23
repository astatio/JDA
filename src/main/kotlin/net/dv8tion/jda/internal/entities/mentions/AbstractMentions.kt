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

@file:Suppress("DEPRECATION")

package net.dv8tion.jda.internal.entities.mentions

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Mentions
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.commands.ICommandReference
import net.dv8tion.jda.api.interactions.commands.SlashCommandReference
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.utils.Checks
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MultiSet
import org.apache.commons.collections4.multiset.HashMultiSet
import java.util.Collections
import java.util.EnumSet
import java.util.regex.Matcher
import java.util.stream.Collectors
import javax.annotation.Nonnull
import javax.annotation.Nullable

// Regex group indices for Message.MentionType.SLASH_COMMAND's pattern
private const val SLASH_COMMAND_NAME_GROUP = 1
private const val SLASH_COMMAND_SUBGROUP_GROUP = 2
private const val SLASH_COMMAND_GROUP_GROUP = 3
private const val SLASH_COMMAND_ID_GROUP = 4

abstract class AbstractMentions protected constructor(
    @JvmField protected val content: String,
    @JvmField protected val jda: JDAImpl,
    @Nullable @JvmField protected val guild: Guild?,
    @JvmField protected val mentionsEveryone: Boolean,
) : Mentions {
    @JvmField protected var mentionedUsers: List<User>? = null

    @JvmField protected var mentionedMembers: List<Member>? = null

    @JvmField protected var mentionedRoles: List<Role>? = null

    @JvmField protected var mentionedChannels: List<GuildChannel>? = null

    @JvmField protected var mentionedEmojis: List<CustomEmoji>? = null

    @JvmField protected var mentionedSlashCommands: List<SlashCommandReference>? = null

    @Nonnull
    override fun getJDA(): JDA = jda

    override fun mentionsEveryone(): Boolean = mentionsEveryone

    @Nonnull
    @Synchronized
    override fun getUsers(): List<User> {
        if (mentionedUsers != null) {
            return mentionedUsers!!
        }
        return Collections
            .unmodifiableList(processMentions(Message.MentionType.USER, true, { matchUser(it) }, { ArrayList<User>() }))
            .also { mentionedUsers = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getUsersBag(): org.apache.commons.collections4.Bag<User> {
        val bag =
            processMentions(Message.MentionType.USER, false, { matchUser(it) }, {
                org.apache.commons.collections4.bag
                    .HashBag()
            })

        // Handle reply mentions
        for (user in getUsers()) {
            if (!bag.contains(user)) {
                bag.add(user, 1)
            }
        }

        return bag
    }

    @Nonnull
    override fun getUsersMultiSet(): MultiSet<User> {
        val set = processMentions(Message.MentionType.USER, false, { matchUser(it) }, { HashMultiSet() })

        // Handle reply mentions
        for (user in getUsers()) {
            if (!set.contains(user)) {
                set.add(user, 1)
            }
        }

        return set
    }

    @Nonnull
    @Synchronized
    override fun getChannels(): List<GuildChannel> {
        if (mentionedChannels != null) {
            return mentionedChannels!!
        }
        return Collections
            .unmodifiableList(processMentions(Message.MentionType.CHANNEL, true, { matchChannel(it) }, { ArrayList<GuildChannel>() }))
            .also { mentionedChannels = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getChannelsBag(): org.apache.commons.collections4.Bag<GuildChannel> =
        processMentions(Message.MentionType.CHANNEL, false, { matchChannel(it) }, {
            org.apache.commons.collections4.bag
                .HashBag()
        })

    @Nonnull
    override fun getChannelsMultiSet(): MultiSet<GuildChannel> =
        processMentions(Message.MentionType.CHANNEL, false, { matchChannel(it) }, { HashMultiSet() })

    @Nonnull
    override fun <T : GuildChannel> getChannels(clazz: Class<T>): List<T> {
        Checks.notNull(clazz, "clazz")
        return getChannels()
            .stream()
            .filter(clazz::isInstance)
            .map(clazz::cast)
            .collect(Collectors.toList())
    }

    @Nonnull
    @Deprecated("")
    override fun <T : GuildChannel> getChannelsBag(clazz: Class<T>): org.apache.commons.collections4.Bag<T> {
        Checks.notNull(clazz, "clazz")
        val matchTypedChannel = { matcher: Matcher ->
            val channel = matchChannel(matcher)
            if (clazz.isInstance(channel)) clazz.cast(channel) else null
        }

        return processMentions(Message.MentionType.CHANNEL, false, matchTypedChannel, {
            org.apache.commons.collections4.bag
                .HashBag()
        })
    }

    @Nonnull
    override fun <T : GuildChannel> getChannelsMultiSet(clazz: Class<T>): MultiSet<T> {
        Checks.notNull(clazz, "clazz")
        val matchTypedChannel = { matcher: Matcher ->
            val channel = matchChannel(matcher)
            if (clazz.isInstance(channel)) clazz.cast(channel) else null
        }

        return processMentions(Message.MentionType.CHANNEL, false, matchTypedChannel, { HashMultiSet() })
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    @Nonnull
    @Synchronized
    override fun getRoles(): List<Role> {
        if (guild == null) {
            return emptyList()
        }
        if (mentionedRoles != null) {
            return mentionedRoles!!
        }
        return Collections
            .unmodifiableList(processMentions(Message.MentionType.ROLE, true, { matchRole(it) }, { ArrayList<Role>() }))
            .also { mentionedRoles = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getRolesBag(): org.apache.commons.collections4.Bag<Role> {
        if (guild == null) {
            return org.apache.commons.collections4.bag
                .HashBag()
        }
        return processMentions(Message.MentionType.ROLE, false, { matchRole(it) }, {
            org.apache.commons.collections4.bag
                .HashBag()
        })
    }

    @Nonnull
    override fun getRolesMultiSet(): MultiSet<Role> {
        if (guild == null) {
            return HashMultiSet()
        }
        return processMentions(Message.MentionType.ROLE, false, { matchRole(it) }, { HashMultiSet() })
    }

    @Nonnull
    @Synchronized
    override fun getCustomEmojis(): List<CustomEmoji> {
        if (mentionedEmojis != null) {
            return mentionedEmojis!!
        }
        return Collections
            .unmodifiableList(processMentions(Message.MentionType.EMOJI, true, { matchEmoji(it) }, { ArrayList<CustomEmoji>() }))
            .also { mentionedEmojis = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getCustomEmojisBag(): org.apache.commons.collections4.Bag<CustomEmoji> =
        processMentions(Message.MentionType.EMOJI, false, { matchEmoji(it) }, {
            org.apache.commons.collections4.bag
                .HashBag()
        })

    @Nonnull
    override fun getCustomEmojisMultiSet(): MultiSet<CustomEmoji> =
        processMentions(Message.MentionType.EMOJI, false, { matchEmoji(it) }, { HashMultiSet() })

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
        return Collections
            .unmodifiableList(processMentions(Message.MentionType.USER, true, { matchMember(it) }, { ArrayList<Member>() }))
            .also { mentionedMembers = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getMembersBag(): org.apache.commons.collections4.Bag<Member> {
        if (guild == null) {
            return org.apache.commons.collections4.bag
                .HashBag()
        }
        val bag =
            processMentions(Message.MentionType.USER, false, { matchMember(it) }, {
                org.apache.commons.collections4.bag
                    .HashBag()
            })

        // Handle reply mentions
        for (member in getMembers()) {
            if (!bag.contains(member)) {
                bag.add(member, 1)
            }
        }

        return bag
    }

    @Nonnull
    override fun getMembersMultiSet(): MultiSet<Member> {
        if (guild == null) {
            return HashMultiSet()
        }
        val set = processMentions(Message.MentionType.USER, false, { matchMember(it) }, { HashMultiSet() })

        // Handle reply mentions
        for (member in getMembers()) {
            if (!set.contains(member)) {
                set.add(member, 1)
            }
        }

        return set
    }

    @Nonnull
    @Synchronized
    override fun getSlashCommands(): List<SlashCommandReference> {
        if (mentionedSlashCommands != null) {
            return mentionedSlashCommands!!
        }
        return Collections
            .unmodifiableList(
                processMentions(Message.MentionType.SLASH_COMMAND, true, {
                    matchSlashCommand(it)
                }, { ArrayList<SlashCommandReference>() }),
            ).also { mentionedSlashCommands = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getSlashCommandsBag(): org.apache.commons.collections4.Bag<SlashCommandReference> =
        processMentions(
            Message.MentionType.SLASH_COMMAND,
            false,
            { matchSlashCommand(it) },
            {
                org.apache.commons.collections4.bag
                    .HashBag()
            },
        )

    @Nonnull
    override fun getSlashCommandsMultiSet(): MultiSet<SlashCommandReference> =
        processMentions(Message.MentionType.SLASH_COMMAND, false, { matchSlashCommand(it) }, { HashMultiSet() })

    @Nonnull
    override fun getMentions(vararg types: Message.MentionType): List<IMentionable> {
        if (types.isEmpty()) {
            return getMentions(*Message.MentionType.values())
        }
        val mentions: MutableList<IMentionable> = ArrayList()
        // Conversion to set to prevent duplication of types
        for (type in EnumSet.of(types[0], *types)) {
            when (type) {
                Message.MentionType.CHANNEL -> mentions.addAll(getChannels())
                Message.MentionType.USER -> {
                    val set: TLongObjectMap<IMentionable> = TLongObjectHashMap()
                    for (u in getUsers()) {
                        set.put(u.idLong, u)
                    }
                    for (m in getMembers()) {
                        set.put(m.idLong, m)
                    }
                    mentions.addAll(set.valueCollection())
                }
                Message.MentionType.ROLE -> mentions.addAll(getRoles())
                Message.MentionType.EMOJI -> mentions.addAll(getCustomEmojis())
                Message.MentionType.SLASH_COMMAND -> mentions.addAll(getSlashCommands())
                else -> {}
            }
        }

        // Sort mentions by occurrence
        mentions.sortWith(compareBy { content.indexOf(it.id) })
        return Collections.unmodifiableList(mentions)
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    override fun isMentioned(
        mentionable: IMentionable,
        vararg types: Message.MentionType,
    ): Boolean {
        Checks.notNull(types, "Mention Types")
        if (types.isEmpty()) {
            return isMentioned(mentionable, *Message.MentionType.values())
        }
        for (type in types) {
            when (type) {
                Message.MentionType.HERE -> if (isMass("@here") && mentionable is UserSnowflake) return true
                Message.MentionType.EVERYONE -> if (isMass("@everyone") && mentionable is UserSnowflake) return true
                Message.MentionType.USER -> if (isUserMentioned(mentionable)) return true
                Message.MentionType.ROLE -> if (isRoleMentioned(mentionable)) return true
                Message.MentionType.CHANNEL ->
                    if (mentionable is GuildChannel && getChannels().contains(mentionable)) return true
                Message.MentionType.EMOJI ->
                    if (mentionable is CustomEmoji && getCustomEmojis().contains(mentionable)) return true
                Message.MentionType.SLASH_COMMAND -> if (isSlashCommandMentioned(mentionable)) return true
            }
        }
        return false
    }

    // Internal parsing methods

    protected fun <T : Any, C : MutableCollection<T>> processMentions(
        type: Message.MentionType,
        distinct: Boolean,
        mapping: (Matcher) -> T?,
        factory: () -> C,
    ): C {
        val accumulator = factory()
        val matcher = type.pattern.matcher(content)
        val unique: MutableSet<T>? = if (distinct) HashSet() else null
        while (matcher.find()) {
            try {
                val elem = mapping(matcher)
                if (elem != null && (unique == null || unique.add(elem))) {
                    accumulator.add(elem)
                }
            } catch (ignored: NumberFormatException) {
                // ignored
            }
        }
        return accumulator
    }

    protected abstract fun matchUser(matcher: Matcher): User?

    protected abstract fun matchMember(matcher: Matcher): Member?

    protected abstract fun matchChannel(matcher: Matcher): GuildChannel?

    protected abstract fun matchRole(matcher: Matcher): Role?

    protected open fun matchEmoji(m: Matcher): CustomEmoji {
        val emojiId = MiscUtil.parseSnowflake(m.group(2))
        val name = m.group(1)
        val animated = m.group(0).startsWith("<a:")
        return getJDA().getEmojiById(emojiId) ?: Emoji.fromCustom(name, emojiId, animated)
    }

    protected open fun matchSlashCommand(matcher: Matcher): SlashCommandReference =
        SlashCommandReference(
            matcher.group(SLASH_COMMAND_NAME_GROUP),
            matcher.group(SLASH_COMMAND_SUBGROUP_GROUP),
            matcher.group(SLASH_COMMAND_GROUP_GROUP),
            matcher.group(SLASH_COMMAND_ID_GROUP).toLong(),
        )

    protected abstract fun isUserMentioned(mentionable: IMentionable): Boolean

    protected open fun isRoleMentioned(mentionable: IMentionable): Boolean {
        if (mentionable is Role) {
            return getRoles().contains(mentionable)
        }
        var member: Member? = null
        if (mentionable is Member) {
            member = mentionable
        } else if (guild != null && mentionable is User) {
            member = guild.getMember(mentionable)
        }
        return member != null && CollectionUtils.containsAny(getRoles(), member.unsortedRoles)
    }

    protected open fun isSlashCommandMentioned(mentionable: IMentionable): Boolean {
        if (mentionable is ICommandReference) {
            for (r in getSlashCommands()) {
                if (r.fullCommandName == mentionable.fullCommandName && r.idLong == mentionable.idLong) {
                    return true
                }
            }
        }
        return false
    }

    protected open fun isMass(s: String): Boolean = mentionsEveryone && content.contains(s)
}
