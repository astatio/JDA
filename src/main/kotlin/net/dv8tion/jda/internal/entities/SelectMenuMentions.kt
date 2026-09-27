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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Mentions
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.interactions.commands.SlashCommandReference
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import org.apache.commons.collections4.MultiSet
import org.apache.commons.collections4.MultiSetUtils
import org.apache.commons.collections4.multiset.HashMultiSet
import java.util.Collections
import java.util.EnumSet
import javax.annotation.Nonnull
import javax.annotation.Nullable

class SelectMenuMentions(
    private val jda: JDAImpl,
    private val interactionEntityBuilder: InteractionEntityBuilder,
    @Nullable private val guild: Guild?,
    private val resolved: DataObject,
    values: DataArray,
) : Mentions {
    private val values: List<String> = values.stream { arr, i -> arr.getString(i) }.toList()

    private var cachedUsers: List<User>? = null
    private var cachedMembers: List<Member>? = null
    private var cachedRoles: List<Role>? = null
    private var cachedChannels: List<GuildChannel>? = null

    @Nonnull
    override fun getJDA(): JDA = jda

    override fun mentionsEveryone(): Boolean = false

    @Nonnull
    override fun getUsers(): List<User> {
        if (cachedUsers != null) {
            return cachedUsers!!
        }

        val userMap = resolved.optObject("users").orElseGet { DataObject.empty() }
        val builder = jda.entityBuilder

        return values
            .asSequence()
            .map { id -> userMap.optObject(id).orElse(null) }
            .filter { it != null }
            .map { builder.createUser(it!!) }
            .toList()
            .also { cachedUsers = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getUsersBag(): org.apache.commons.collections4.Bag<User> =
        org.apache.commons.collections4.bag
            .HashBag(getUsers())

    @Nonnull
    override fun getUsersMultiSet(): MultiSet<User> = HashMultiSet(getUsers())

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    @Nonnull
    override fun getChannels(): List<GuildChannel> {
        if (guild == null) {
            return emptyList()
        }
        if (cachedChannels != null) {
            return cachedChannels!!
        }

        val channelMap = resolved.optObject("channels").orElseGet { DataObject.empty() }

        return values
            .asSequence()
            .map { id -> channelMap.optObject(id).orElse(null) }
            .filter { it != null }
            .mapNotNull { json ->
                val channelType = ChannelType.fromId(json!!.getInt("type", -1))
                // Unknown guilds
                if (channelType.isThread) {
                    interactionEntityBuilder.createThreadChannel(guild, json)
                } else {
                    // Will return null if the type isn't known
                    interactionEntityBuilder.createGuildChannel(guild, json)
                }
            }.toList()
            .also { cachedChannels = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getChannelsBag(): org.apache.commons.collections4.Bag<GuildChannel> =
        org.apache.commons.collections4.bag
            .HashBag(getChannels())

    @Nonnull
    override fun getChannelsMultiSet(): MultiSet<GuildChannel> = HashMultiSet(getChannels())

    @Nonnull
    override fun <T : GuildChannel> getChannels(clazz: Class<T>): List<T> =
        getChannels()
            .stream()
            .filter(clazz::isInstance)
            .map(clazz::cast)
            .collect(Helpers.toUnmodifiableList())

    @Nonnull
    @Deprecated("")
    override fun <T : GuildChannel> getChannelsBag(clazz: Class<T>): org.apache.commons.collections4.Bag<T> =
        org.apache.commons.collections4.bag
            .HashBag(getChannels(clazz))

    @Nonnull
    override fun <T : GuildChannel> getChannelsMultiSet(clazz: Class<T>): MultiSet<T> = HashMultiSet(getChannels(clazz))

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    @Nonnull
    override fun getRoles(): List<Role> {
        if (guild == null) {
            return emptyList()
        }
        if (cachedRoles != null) {
            return cachedRoles!!
        }

        val roleMap = resolved.optObject("roles").orElseGet { DataObject.empty() }

        return values
            .asSequence()
            .filter { roleMap.hasKey(it) }
            .map { roleMap.getObject(it) }
            .mapNotNull { json ->
                if (!guild.isDetached) {
                    guild.getRoleById(json.getUnsignedLong("id"))
                } else {
                    interactionEntityBuilder.createRole(guild, json)
                }
            }.toList()
            .also { cachedRoles = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getRolesBag(): org.apache.commons.collections4.Bag<Role> =
        org.apache.commons.collections4.bag
            .HashBag(getRoles())

    @Nonnull
    override fun getRolesMultiSet(): MultiSet<Role> = HashMultiSet(getRoles())

    @Nonnull
    override fun getCustomEmojis(): List<CustomEmoji> = emptyList()

    @Nonnull
    @Deprecated("")
    override fun getCustomEmojisBag(): org.apache.commons.collections4.Bag<CustomEmoji> =
        org.apache.commons.collections4.BagUtils
            .emptyBag()

    @Nonnull
    override fun getCustomEmojisMultiSet(): MultiSet<CustomEmoji> = MultiSetUtils.emptyMultiSet()

    @Nonnull
    override fun getSlashCommands(): List<SlashCommandReference> = emptyList()

    @Nonnull
    @Deprecated("")
    override fun getSlashCommandsBag(): org.apache.commons.collections4.Bag<SlashCommandReference> =
        org.apache.commons.collections4.BagUtils
            .emptyBag()

    @Nonnull
    override fun getSlashCommandsMultiSet(): MultiSet<SlashCommandReference> = MultiSetUtils.emptyMultiSet()

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    @Nonnull
    override fun getMembers(): List<Member> {
        if (guild == null) {
            return emptyList()
        }
        if (cachedMembers != null) {
            return cachedMembers!!
        }

        val memberMap = resolved.optObject("members").orElseGet { DataObject.empty() }
        val userMap = resolved.optObject("users").orElseGet { DataObject.empty() }

        return values
            .asSequence()
            .map { id -> memberMap.optObject(id).map { m -> m.put("id", id) }.orElse(null) }
            .filter { it != null }
            .map { json -> json!!.put("user", userMap.getObject(json.getString("id"))) }
            .mapNotNull { json -> interactionEntityBuilder.createMember(guild, json) }
            .filter { member ->
                if (!member.isDetached) {
                    jda.entityBuilder.updateMemberCache(member as MemberImpl)
                }
                true
            }.toList()
            .also { cachedMembers = it }
    }

    @Nonnull
    @Deprecated("")
    override fun getMembersBag(): org.apache.commons.collections4.Bag<Member> =
        org.apache.commons.collections4.bag
            .HashBag(getMembers())

    @Nonnull
    override fun getMembersMultiSet(): MultiSet<Member> = HashMultiSet(getMembers())

    @Nonnull
    override fun getMentions(vararg types: Message.MentionType): List<IMentionable> {
        if (types.isEmpty()) {
            return getMentions(*Message.MentionType.values())
        }
        val mentions: MutableList<IMentionable> = ArrayList()
        // Convert to set to avoid duplicates
        val set = EnumSet.of(types[0], *types)
        for (type in set) {
            when (type) {
                Message.MentionType.USER -> {
                    val members = getMembers()
                    val users = getUsers()
                    mentions.addAll(members)
                    users
                        .stream()
                        .filter { u -> members.stream().noneMatch { m -> m.idLong == u.idLong } }
                        .forEach { mentions.add(it) }
                }
                Message.MentionType.ROLE -> mentions.addAll(getRoles())
                Message.MentionType.CHANNEL -> mentions.addAll(getChannels())
                else -> {}
            }
        }

        mentions.sortWith(compareBy { values.indexOf(it.id) })
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

        val id = mentionable.id
        for (type in types) {
            when (type) {
                Message.MentionType.USER ->
                    if (mentionable is UserSnowflake) {
                        val mentioned = resolved.optObject("users").map { obj -> obj.hasKey(id) }.orElse(false)
                        if (mentioned) {
                            return true
                        }
                    }
                Message.MentionType.ROLE ->
                    if (mentionable is Member) {
                        val mentioned =
                            mentionable.unsortedRoles
                                .stream()
                                .anyMatch { role -> isMentioned(role, Message.MentionType.ROLE) }
                        if (mentioned) {
                            return true
                        }
                    } else if (mentionable is User) {
                        val mentioned =
                            getMembers()
                                .stream()
                                .filter { it.idLong == mentionable.idLong }
                                .findFirst()
                                .map { member -> isMentioned(member, Message.MentionType.ROLE) }
                                .orElse(false)
                        if (mentioned) {
                            return true
                        }
                    } else if (mentionable is Role) {
                        val mentioned = resolved.optObject("roles").map { obj -> obj.hasKey(id) }.orElse(false)
                        if (mentioned) {
                            return true
                        }
                    }
                Message.MentionType.CHANNEL ->
                    if (mentionable is GuildChannel && getChannels().contains(mentionable)) {
                        return true
                    }
                else -> {}
            }
        }
        return false
    }
}
