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

package net.dv8tion.jda.internal.requests.restaction

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.Region
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.IPostContainer
import net.dv8tion.jda.api.entities.channel.attribute.ISlowmodeChannel
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.utils.ChannelUtil
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.PermissionUtil
import okhttp3.RequestBody
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

private const val MIN_BITRATE = 8000

open class ChannelActionImpl<T : GuildChannel> :
    AuditableRestActionImpl<T>,
    ChannelAction<T> {
    @JvmField
    protected val overrides: TLongObjectMap<PermOverrideData> = TLongObjectHashMap()

    @JvmField
    protected val guild: Guild

    @JvmField
    protected val clazz: Class<T>

    @JvmField
    protected val type: ChannelType

    // --all channels--
    @JvmField
    protected var name: String

    @JvmField
    protected var parent: Category? = null

    @JvmField
    protected var position: Int? = null

    // --forum only--
    @JvmField
    protected var availableTags: List<BaseForumTag>? = null

    @JvmField
    protected var defaultReactionEmoji: Emoji? = null

    // --text/forum/voice only--
    @JvmField
    protected var slowmode: Int? = null

    @JvmField
    protected var defaultThreadSlowmode: Int? = null

    // --text/forum/voice/news--
    @JvmField
    protected var topic: String? = null

    @JvmField
    protected var nsfw: Boolean? = null

    // --voice only--
    @JvmField
    protected var userlimit: Int? = null

    // --audio only--
    @JvmField
    protected var bitrate: Int? = null

    @JvmField
    protected var region: Region? = null

    // --forum only--
    @JvmField
    protected var defaultLayout: Int? = null

    @JvmField
    protected var defaultSortOrder: Int? = null

    constructor(clazz: Class<T>, name: String, guild: Guild, type: ChannelType) :
        super(guild.jda, Route.Guilds.CREATE_CHANNEL.compile(guild.id)) {
        this.clazz = clazz
        this.guild = guild
        this.type = type
        this.name = name
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun reason(reason: String?): ChannelActionImpl<T> = super.reason(reason) as ChannelActionImpl<T>

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): ChannelActionImpl<T> = super.setCheck(checks) as ChannelActionImpl<T>

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): ChannelActionImpl<T> = super<AuditableRestActionImpl>.timeout(timeout, unit) as ChannelActionImpl<T>

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): ChannelActionImpl<T> =
        super<AuditableRestActionImpl>.deadline(timestamp) as ChannelActionImpl<T>

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nonnull
    override fun getType(): ChannelType = type

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): ChannelActionImpl<T> {
        Checks.notEmpty(name, "Name")
        Checks.notLonger(name, Channel.MAX_NAME_LENGTH, "Name")
        this.name = name
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setParent(category: Category?): ChannelActionImpl<T> {
        if (category != null) {
            Checks.check(category.guild == guild, "Category is not from same guild!")
            if (type == ChannelType.CATEGORY) {
                throw UnsupportedOperationException("Cannot set a parent Category on a Category")
            }
        }

        this.parent = category
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setPosition(position: Int?): ChannelActionImpl<T> {
        Checks.check(position == null || position >= 0, "Position must be >= 0!")
        this.position = position
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setTopic(topic: String?): ChannelActionImpl<T> {
        Checks.checkSupportedChannelTypes(ChannelUtil.TOPIC_SUPPORTED, type, "Topic")
        if (topic != null) {
            if (ChannelUtil.POST_CONTAINERS.contains(type)) {
                Checks.notLonger(topic, IPostContainer.MAX_POST_CONTAINER_TOPIC_LENGTH, "Topic")
            } else {
                Checks.notLonger(topic, StandardGuildMessageChannel.MAX_TOPIC_LENGTH, "Topic")
            }
        }
        this.topic = topic
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setNSFW(nsfw: Boolean): ChannelActionImpl<T> {
        Checks.checkSupportedChannelTypes(ChannelUtil.NSFW_SUPPORTED, type, "NSFW (age-restricted)")
        this.nsfw = nsfw
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setSlowmode(slowmode: Int): ChannelActionImpl<T> {
        Checks.checkSupportedChannelTypes(ChannelUtil.SLOWMODE_SUPPORTED, type, "Slowmode")
        Checks.check(
            slowmode <= ISlowmodeChannel.MAX_SLOWMODE && slowmode >= 0,
            "Slowmode must be between 0 and %d (seconds)!",
            ISlowmodeChannel.MAX_SLOWMODE,
        )
        this.slowmode = slowmode
        return this
    }

    @Nonnull
    override fun setDefaultThreadSlowmode(slowmode: Int): ChannelAction<T> {
        Checks.checkSupportedChannelTypes(ChannelUtil.THREAD_CONTAINERS, type, "Default Thread Slowmode")
        Checks.check(
            slowmode <= ISlowmodeChannel.MAX_SLOWMODE && slowmode >= 0,
            "Slowmode must be between 0 and %d (seconds)!",
            ISlowmodeChannel.MAX_SLOWMODE,
        )
        this.defaultThreadSlowmode = slowmode
        return this
    }

    @Nonnull
    override fun setDefaultReaction(emoji: Emoji?): ChannelAction<T> {
        Checks.checkSupportedChannelTypes(ChannelUtil.POST_CONTAINERS, type, "Default Reaction")
        this.defaultReactionEmoji = emoji
        return this
    }

    @Nonnull
    override fun setDefaultLayout(
        @Nonnull layout: ForumChannel.Layout,
    ): ChannelAction<T> {
        Checks.checkSupportedChannelTypes(EnumSet.of(ChannelType.FORUM), type, "Default Layout")
        Checks.notNull(layout, "layout")
        Checks.check(layout != ForumChannel.Layout.UNKNOWN, "Layout type cannot be UNKNOWN.")
        this.defaultLayout = layout.key
        return this
    }

    @Nonnull
    override fun setDefaultSortOrder(
        @Nonnull sortOrder: IPostContainer.SortOrder,
    ): ChannelAction<T> {
        Checks.checkSupportedChannelTypes(ChannelUtil.POST_CONTAINERS, type, "Default Sort Order")
        Checks.notNull(sortOrder, "SortOrder")
        Checks.check(sortOrder != IPostContainer.SortOrder.UNKNOWN, "Sort Order cannot be UNKNOWN.")
        this.defaultSortOrder = sortOrder.key
        return this
    }

    @Nonnull
    override fun setAvailableTags(
        @Nonnull tags: List<BaseForumTag>,
    ): ChannelAction<T> {
        Checks.checkSupportedChannelTypes(ChannelUtil.POST_CONTAINERS, type, "Available Tags")
        Checks.noneNull(tags, "Tags")
        this.availableTags = ArrayList(tags)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun addMemberPermissionOverride(
        userId: Long,
        allow: Long,
        deny: Long,
    ): ChannelActionImpl<T> = addOverride(userId, PermOverrideData.MEMBER_TYPE, allow, deny)

    @Nonnull
    @CheckReturnValue
    override fun addRolePermissionOverride(
        roleId: Long,
        allow: Long,
        deny: Long,
    ): ChannelActionImpl<T> = addOverride(roleId, PermOverrideData.ROLE_TYPE, allow, deny)

    @Nonnull
    override fun removePermissionOverride(id: Long): ChannelAction<T> {
        overrides.remove(id)
        return this
    }

    @Nonnull
    override fun clearPermissionOverrides(): ChannelAction<T> {
        overrides.clear()
        return this
    }

    @Nonnull
    override fun syncPermissionOverrides(): ChannelAction<T> {
        if (parent == null) {
            throw IllegalStateException(
                "Cannot sync overrides without parent category! Use setParent(category) first!",
            )
        }
        clearPermissionOverrides()
        val selfMember = guild.selfMember
        var canSetRoles = selfMember.hasPermission(parent!!, Permission.MANAGE_ROLES)
        // You can only set MANAGE_ROLES if you have ADMINISTRATOR or MANAGE_PERMISSIONS as an
        // override on the channel
        // That is why we explicitly exclude it here!
        // This is by far the most complex and weird permission logic in the entire API...
        val botPerms =
            PermissionUtil.getEffectivePermission(selfMember) and Permission.MANAGE_PERMISSIONS.rawValue.inv()

        parent!!.rolePermissionOverrides.forEach { override ->
            var allow = override.allowedRaw
            var deny = override.deniedRaw
            if (!canSetRoles) {
                allow = allow and botPerms
                deny = deny and botPerms
            }
            addRolePermissionOverride(override.idLong, allow, deny)
        }

        parent!!.memberPermissionOverrides.forEach { override ->
            var allow = override.allowedRaw
            var deny = override.deniedRaw
            if (!canSetRoles) {
                allow = allow and botPerms
                deny = deny and botPerms
            }
            addMemberPermissionOverride(override.idLong, allow, deny)
        }
        return this
    }

    private fun addOverride(
        targetId: Long,
        type: Int,
        allow: Long,
        deny: Long,
    ): ChannelActionImpl<T> {
        val selfMember = guild.selfMember
        var canSetRoles = selfMember.hasPermission(Permission.ADMINISTRATOR)
        if (!canSetRoles && parent != null) {
            // You can also set MANAGE_ROLES if you have it on the category (apparently?)
            canSetRoles = selfMember.hasPermission(parent!!, Permission.MANAGE_ROLES)
        }
        if (!canSetRoles) {
            // Prevent permission escalation
            // You can only set MANAGE_ROLES if you have ADMINISTRATOR or MANAGE_PERMISSIONS as an
            // override on the channel
            // That is why we explicitly exclude it here!
            // This is by far the most complex and weird permission logic in the entire API...
            val botPerms =
                PermissionUtil.getEffectivePermission(selfMember) and Permission.MANAGE_PERMISSIONS.rawValue.inv()

            val missingPerms = Permission.getPermissions((allow or deny) and botPerms.inv())
            if (!missingPerms.isEmpty()) {
                throw InsufficientPermissionException(
                    guild,
                    Permission.MANAGE_PERMISSIONS,
                    "You must have Permission.MANAGE_PERMISSIONS on the channel explicitly in order to set permissions you don't already have!",
                )
            }
        }

        overrides.put(targetId, PermOverrideData(type, targetId, allow, deny))
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setBitrate(bitrate: Int?): ChannelActionImpl<T> {
        if (!type.isAudio) {
            throw UnsupportedOperationException("Can only set the bitrate for an Audio Channel!")
        }
        if (bitrate != null) {
            val maxBitrate = guild.maxBitrate
            require(bitrate >= MIN_BITRATE) { "Bitrate must be greater than $MIN_BITRATE." }
            require(bitrate <= maxBitrate) { "Bitrate must be less than $maxBitrate" }
        }

        this.bitrate = bitrate
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setUserlimit(userlimit: Int?): ChannelActionImpl<T> {
        if (userlimit != null) {
            Checks.notNegative(userlimit, "Userlimit")
            if (type == ChannelType.VOICE) {
                Checks.check(
                    userlimit <= VoiceChannel.MAX_USERLIMIT,
                    "Userlimit may not be greater than %d for voice channels",
                    VoiceChannel.MAX_USERLIMIT,
                )
            } else if (type == ChannelType.STAGE) {
                Checks.check(
                    userlimit <= StageChannel.MAX_USERLIMIT,
                    "Userlimit may not be greater than %d for stage channels",
                    StageChannel.MAX_USERLIMIT,
                )
            } else {
                throw IllegalStateException("Can only set userlimit on audio channels")
            }
        }
        this.userlimit = userlimit
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setRegion(region: Region?): ChannelActionImpl<T> {
        if (!type.isAudio) {
            throw UnsupportedOperationException("Can only set the region for AudioChannels!")
        }
        this.region = region
        return this
    }

    override fun finalizeData(): RequestBody? {
        val json = DataObject.empty()

        // All channel types
        json.put("name", name)
        json.put("type", type.id)
        json.put("permission_overwrites", DataArray.fromCollection(overrides.valueCollection()))
        if (position != null) {
            json.put("position", position)
        }
        if (parent != null) {
            json.put("parent_id", parent!!.id)
        }

        // Text and Forum
        if (slowmode != null) {
            json.put("rate_limit_per_user", slowmode)
        }
        if (defaultThreadSlowmode != null) {
            json.put("default_thread_rate_limit_per_user", defaultThreadSlowmode)
        }

        // Text, Forum, and News
        if (topic != null && !topic!!.isEmpty()) {
            json.put("topic", topic)
        }
        if (nsfw != null) {
            json.put("nsfw", nsfw)
        }

        // Forum/Media only
        if (defaultReactionEmoji is CustomEmoji) {
            json.put(
                "default_reaction_emoji",
                DataObject.empty().put("emoji_id", (defaultReactionEmoji as CustomEmoji).id),
            )
        } else if (defaultReactionEmoji is UnicodeEmoji) {
            json.put("default_reaction_emoji", DataObject.empty().put("emoji_name", defaultReactionEmoji!!.name))
        }
        if (availableTags != null) {
            json.put("available_tags", DataArray.fromCollection(availableTags!!))
        }
        if (defaultSortOrder != null) {
            json.put("default_sort_order", defaultSortOrder)
        }

        // Forum only
        if (defaultLayout != null) {
            json.put("default_forum_layout", defaultLayout)
        }

        // Voice only
        if (userlimit != null) {
            json.put("user_limit", userlimit)
        }

        // Voice and Stage
        if (bitrate != null) {
            json.put("bitrate", bitrate)
        }
        if (region != null) {
            json.put("rtc_region", region!!.key)
        }

        return getRequestBody(json)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<T>,
    ) {
        val builder: EntityBuilder = api.entityBuilder
        val channel = builder.createGuildChannel(guild as GuildImpl, response.getObject())
        if (channel == null) {
            request.onFailure(IllegalStateException("Created channel of unknown type!"))
        } else {
            request.onSuccess(clazz.cast(channel))
        }
    }
}
