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

package net.dv8tion.jda.internal.entities.channel.concrete

import gnu.trove.map.TLongObjectMap
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.channel.attribute.IPostContainer.SortOrder
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel.Layout
import net.dv8tion.jda.api.entities.channel.forums.ForumTag
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.managers.channel.concrete.ForumChannelManager
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.ForumChannelMixin
import net.dv8tion.jda.internal.entities.emoji.CustomEmojiImpl
import net.dv8tion.jda.internal.managers.channel.concrete.ForumChannelManagerImpl
import net.dv8tion.jda.internal.utils.Helpers
import net.dv8tion.jda.internal.utils.cache.SortedSnowflakeCacheViewImpl
import java.util.Comparator
import java.util.function.Function
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ForumChannelImpl(
    id: Long,
    guild: GuildImpl,
) : AbstractGuildChannelImpl<ForumChannelImpl>(id, guild),
    ForumChannel,
    GuildChannelUnion,
    ForumChannelMixin<ForumChannelImpl> {
    private val overrides: TLongObjectMap<PermissionOverride> = MiscUtil.newLongMap()
    private val tagCache: SortedSnowflakeCacheViewImpl<ForumTag> =
        SortedSnowflakeCacheViewImpl(
            ForumTag::class.java,
            Function { tag: ForumTag -> tag.name },
            Comparator.naturalOrder(),
        )

    private var defaultReaction: Emoji? = null
    private var topic: String? = null
    private var parentCategoryId: Long = 0
    private var nsfw: Boolean = false
    private var position: Int = 0
    private var slowmode: Int = 0
    private var defaultSortOrder: Int = 0
    private var defaultLayout: Int = 0

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var defaultThreadSlowmode: Int = 0

    override fun isDetached(): Boolean = false

    @Nonnull
    override fun getGuild(): GuildImpl = super.getGuild() as GuildImpl

    @Nonnull
    override fun getManager(): ForumChannelManager = ForumChannelManagerImpl(this)

    @Nonnull
    override fun getMembers(): List<Member> =
        getGuild()
            .members
            .stream()
            .filter { m -> m.hasPermission(this, Permission.VIEW_CHANNEL) }
            .collect(Helpers.toUnmodifiableList())

    override fun getAvailableTagCache(): SortedSnowflakeCacheViewImpl<ForumTag> = tagCache

    override val permissionOverrideMap: TLongObjectMap<PermissionOverride>
        get() = overrides

    override fun isNSFW(): Boolean = nsfw

    override fun getPositionRaw(): Int = position

    override fun getParentCategoryIdLong(): Long = parentCategoryId

    override fun getSlowmode(): Int = slowmode

    override fun getTopic(): String? = topic

    @Nullable
    override fun getDefaultReaction(): EmojiUnion = defaultReaction as EmojiUnion

    override fun getDefaultThreadSlowmode(): Int = defaultThreadSlowmode

    @Nonnull
    override fun getDefaultSortOrder(): SortOrder = SortOrder.fromKey(defaultSortOrder)

    @Nonnull
    override fun getDefaultLayout(): Layout = Layout.fromKey(defaultLayout)

    override val rawSortOrder: Int
        get() = defaultSortOrder

    fun getRawLayout(): Int = defaultLayout

    // Setters

    override fun setParentCategory(parentCategoryId: Long): ForumChannelImpl {
        this.parentCategoryId = parentCategoryId
        return this
    }

    override fun setPosition(position: Int): ForumChannelImpl {
        this.position = position
        return this
    }

    override fun setDefaultThreadSlowmode(slowmode: Int): ForumChannelImpl {
        this.defaultThreadSlowmode = slowmode
        return this
    }

    override fun setNSFW(ageRestricted: Boolean): ForumChannelImpl {
        this.nsfw = ageRestricted
        return this
    }

    override fun setSlowmode(slowmode: Int): ForumChannelImpl {
        this.slowmode = slowmode
        return this
    }

    override fun setTopic(topic: String?): ForumChannelImpl {
        this.topic = topic
        return this
    }

    override fun setDefaultReaction(emoji: DataObject?): ForumChannelImpl {
        defaultReaction =
            if (emoji != null && !emoji.isNull("emoji_id")) {
                CustomEmojiImpl("", emoji.getUnsignedLong("emoji_id"), false)
            } else if (emoji != null && !emoji.isNull("emoji_name")) {
                Emoji.fromUnicode(emoji.getString("emoji_name"))
            } else {
                null
            }
        return this
    }

    override fun setDefaultSortOrder(defaultSortOrder: Int): ForumChannelImpl {
        this.defaultSortOrder = defaultSortOrder
        return this
    }

    override fun setDefaultLayout(layout: Int): ForumChannelImpl {
        this.defaultLayout = layout
        return this
    }
}
