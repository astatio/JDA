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

package net.dv8tion.jda.internal.managers.channel

import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.Region
import net.dv8tion.jda.api.entities.IPermissionHolder
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelFlag
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.attribute.IPostContainer
import net.dv8tion.jda.api.entities.channel.attribute.ISlowmodeChannel
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag
import net.dv8tion.jda.api.entities.channel.forums.ForumTagSnowflake
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.channel.ChannelManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPermissionContainerMixin
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.GuildChannelMixin
import net.dv8tion.jda.internal.managers.ManagerBase
import net.dv8tion.jda.internal.requests.restaction.PermOverrideData
import net.dv8tion.jda.internal.utils.ChannelUtil
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.PermissionUtil
import okhttp3.RequestBody
import java.util.ArrayList
import java.util.EnumSet
import java.util.stream.Collectors
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

private const val MIN_BITRATE = 8000

// We do a lot of (M) and (T) casting that we know is correct but the compiler warns about.
@Suppress("unchecked", "UNCHECKED_CAST", "ProtectedMemberInFinalClass")
open class ChannelManagerImpl<T : GuildChannel, M : ChannelManager<T, M>> protected constructor(
    @JvmField protected var channel: T,
) : ManagerBase<M>(
        channel.getJDA(),
        Route.Channels.MODIFY_CHANNEL.compile(channel.getId()),
    ),
    ChannelManager<T, M> {
    @JvmField
    protected val flags: EnumSet<ChannelFlag> = EnumSet.copyOf(channel.flags)

    // Kept protected to match the Java field shape, even though this class is final
    @JvmField
    protected var autoArchiveDuration: ThreadChannel.AutoArchiveDuration? = null

    @JvmField
    protected var availableTags: List<BaseForumTag>? = null

    @JvmField
    protected var appliedTags: List<String>? = null

    @JvmField
    protected var defaultReactionEmoji: Emoji? = null

    @JvmField
    protected var defaultLayout: Int = 0

    @JvmField
    protected var defaultSortOrder: Int = 0

    @JvmField
    protected var type: ChannelType = channel.type

    @JvmField
    protected var name: String? = null

    @JvmField
    protected var parent: String? = null

    @JvmField
    protected var topic: String? = null

    @JvmField
    protected var region: String? = null

    @JvmField
    protected var nsfw: Boolean = false

    @JvmField
    protected var archived: Boolean = false

    @JvmField
    protected var locked: Boolean = false

    @JvmField
    protected var invitable: Boolean = false

    @JvmField
    protected var position: Int = 0

    @JvmField
    protected var slowmode: Int = 0

    @JvmField
    protected var defaultThreadSlowmode: Int = 0

    @JvmField
    protected var userLimit: Int = 0

    @JvmField
    protected var bitrate: Int = 0

    @JvmField
    protected val lock: Any = Any()

    @JvmField
    protected val overridesAdd: TLongObjectHashMap<PermOverrideData> = TLongObjectHashMap()

    @JvmField
    protected val overridesRem: TLongSet = TLongHashSet()

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    @Nonnull
    override fun getChannel(): T {
        val realChannel = api.getGuildChannelById(channel.type, channel.idLong) as T?
        if (realChannel != null) {
            channel = realChannel
        }
        return channel
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): M {
        super.reset(fields)
        if (fields and ChannelManager.NAME == ChannelManager.NAME) {
            name = null
        }
        if (fields and ChannelManager.TYPE == ChannelManager.TYPE) {
            type = channel.type
        }
        if (fields and ChannelManager.PARENT == ChannelManager.PARENT) {
            parent = null
        }
        if (fields and ChannelManager.TOPIC == ChannelManager.TOPIC) {
            topic = null
        }
        if (fields and ChannelManager.REGION == ChannelManager.REGION) {
            region = null
        }
        if (fields and ChannelManager.AVAILABLE_TAGS == ChannelManager.AVAILABLE_TAGS) {
            availableTags = null
        }
        if (fields and ChannelManager.APPLIED_TAGS == ChannelManager.APPLIED_TAGS) {
            appliedTags = null
        }
        if (fields and ChannelManager.DEFAULT_REACTION == ChannelManager.DEFAULT_REACTION) {
            defaultReactionEmoji = null
        }
        if (fields and ChannelManager.PERMISSION == ChannelManager.PERMISSION) {
            withLock(lock) {
                overridesRem.clear()
                overridesAdd.clear()
            }
        }

        if (fields and ChannelManager.PINNED == ChannelManager.PINNED) {
            if (channel.flags.contains(ChannelFlag.PINNED)) {
                flags.add(ChannelFlag.PINNED)
            } else {
                flags.remove(ChannelFlag.PINNED)
            }
        }

        if (fields and ChannelManager.REQUIRE_TAG == ChannelManager.REQUIRE_TAG) {
            if (channel.flags.contains(ChannelFlag.REQUIRE_TAG)) {
                flags.add(ChannelFlag.REQUIRE_TAG)
            } else {
                flags.remove(ChannelFlag.REQUIRE_TAG)
            }
        }

        if (fields and ChannelManager.HIDE_MEDIA_DOWNLOAD_OPTIONS == ChannelManager.HIDE_MEDIA_DOWNLOAD_OPTIONS) {
            if (channel.flags.contains(ChannelFlag.HIDE_MEDIA_DOWNLOAD_OPTIONS)) {
                flags.add(ChannelFlag.HIDE_MEDIA_DOWNLOAD_OPTIONS)
            } else {
                flags.remove(ChannelFlag.HIDE_MEDIA_DOWNLOAD_OPTIONS)
            }
        }

        return this as M
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): M {
        super.reset(*fields)
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): M {
        super.reset()
        name = null
        type = channel.type
        parent = null
        topic = null
        region = null
        availableTags = null
        appliedTags = null
        defaultReactionEmoji = null
        flags.clear()
        flags.addAll(channel.flags)
        withLock(lock) {
            overridesRem.clear()
            overridesAdd.clear()
        }
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun clearOverridesAdded(): M {
        withLock(lock) {
            overridesAdd.clear()
            if (overridesRem.isEmpty) {
                set = set and ChannelManager.PERMISSION.inv()
            }
        }
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun clearOverridesRemoved(): M {
        withLock(lock) {
            overridesRem.clear()
            if (overridesAdd.isEmpty) {
                set = set and ChannelManager.PERMISSION.inv()
            }
        }
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun putPermissionOverride(
        @Nonnull permHolder: IPermissionHolder,
        allow: Long,
        deny: Long,
    ): M {
        if (channel !is IPermissionContainer) {
            throw IllegalStateException("Can only set permissions on Channels that implement IPermissionContainer")
        }

        Checks.notNull(permHolder, "PermissionHolder")
        Checks.check(permHolder.guild == getGuild(), "PermissionHolder is not from the same Guild!")
        val id = permHolder.idLong
        val type = if (permHolder is Role) PermOverrideData.ROLE_TYPE else PermOverrideData.MEMBER_TYPE
        putPermissionOverride(PermOverrideData(type, id, allow, deny))
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun putMemberPermissionOverride(
        memberId: Long,
        allow: Long,
        deny: Long,
    ): M {
        putPermissionOverride(PermOverrideData(PermOverrideData.MEMBER_TYPE, memberId, allow, deny))
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun putRolePermissionOverride(
        roleId: Long,
        allow: Long,
        deny: Long,
    ): M {
        putPermissionOverride(PermOverrideData(PermOverrideData.ROLE_TYPE, roleId, allow, deny))
        return this as M
    }

    private fun checkCanPutPermissions(
        allow: Long,
        deny: Long,
    ) {
        val selfMember = getGuild().selfMember

        if (isPermissionChecksEnabled() && !selfMember.hasPermission(Permission.ADMINISTRATOR)) {
            if (!selfMember.hasPermission(channel, Permission.MANAGE_ROLES)) {
                // We can't manage permissions at all!
                throw InsufficientPermissionException(channel, Permission.MANAGE_PERMISSIONS)
            }

            // Check on channel level to make sure we are actually able to set all the permissions!
            val channelPermissions = PermissionUtil.getExplicitPermission(channel, selfMember, false)

            // This implies we can only set permissions the bot also has in the channel!
            if (channelPermissions and Permission.MANAGE_PERMISSIONS.rawValue == 0L) {
                // You can only set MANAGE_ROLES if you have ADMINISTRATOR or MANAGE_PERMISSIONS as
                // an override on the channel
                // That is why we explicitly exclude it here!
                // This is by far the most complex and weird permission logic in the entire API...
                val botPerms =
                    PermissionUtil.getEffectivePermission(channel, selfMember) and
                        Permission.MANAGE_ROLES.rawValue.inv()
                val missing = Permission.getPermissions((allow or deny) and botPerms.inv())
                if (!missing.isEmpty()) {
                    throw InsufficientPermissionException(
                        channel,
                        Permission.MANAGE_PERMISSIONS,
                        "You must have Permission.MANAGE_PERMISSIONS on the channel explicitly in order to set permissions you don't already have!",
                    )
                }
            }
        }
    }

    private fun putPermissionOverride(
        @Nonnull overrideData: PermOverrideData,
    ) {
        checkCanPutPermissions(overrideData.allow, overrideData.deny)
        withLock(lock) {
            overridesRem.remove(overrideData.id)
            overridesAdd.put(overrideData.id, overrideData)
            set = set or ChannelManager.PERMISSION
        }
    }

    @Nonnull
    @CheckReturnValue
    fun removePermissionOverride(
        @Nonnull permHolder: IPermissionHolder,
    ): M {
        if (channel !is IPermissionContainer) {
            throw IllegalStateException("Can only set permissions on Channels that implement IPermissionContainer")
        }

        Checks.notNull(permHolder, "PermissionHolder")
        Checks.check(permHolder.guild == getGuild(), "PermissionHolder is not from the same Guild!")
        return removePermissionOverride(permHolder.idLong)
    }

    @Nonnull
    @CheckReturnValue
    fun removePermissionOverride(id: Long): M {
        if (isPermissionChecksEnabled() &&
            !getGuild().selfMember.hasPermission(getChannel(), Permission.MANAGE_PERMISSIONS)
        ) {
            throw InsufficientPermissionException(getChannel(), Permission.MANAGE_PERMISSIONS)
        }
        withLock(lock) {
            overridesRem.add(id)
            overridesAdd.remove(id)
            set = set or ChannelManager.PERMISSION
        }
        return this as M
    }

    @Suppress("ThrowsCount") // ported verbatim from the Java original; the multiple guards mirror its validation
    @Nonnull
    @CheckReturnValue
    fun sync(
        @Nonnull syncSource: IPermissionContainer,
    ): M {
        if (channel !is IPermissionContainer) {
            throw IllegalStateException("Can only set permissions on Channels that implement IPermissionContainer")
        }

        Checks.notNull(syncSource, "SyncSource")
        Checks.check(getGuild() == syncSource.guild, "Sync only works for channels of same guild")

        val permChannel = channel as IPermissionContainer
        if (syncSource == getChannel()) {
            return this as M
        }

        if (isPermissionChecksEnabled()) {
            val selfMember = getGuild().selfMember
            if (!selfMember.hasPermission(permChannel, Permission.MANAGE_PERMISSIONS)) {
                throw InsufficientPermissionException(getChannel(), Permission.MANAGE_PERMISSIONS)
            }

            if (!selfMember.canSync(permChannel, syncSource)) {
                throw InsufficientPermissionException(
                    getChannel(),
                    Permission.MANAGE_PERMISSIONS,
                    "Cannot sync channel with parent due to permission escalation issues. " +
                        "One of the overrides would set MANAGE_PERMISSIONS or a permission that the bot does not have. " +
                        "This is not possible without explicitly having MANAGE_PERMISSIONS on this channel or ADMINISTRATOR on a role.",
                )
            }
        }

        withLock(lock) {
            overridesRem.clear()
            overridesAdd.clear()

            // set all current overrides to-be-removed
            permChannel
                .getPermissionOverrides()
                .stream()
                .mapToLong { it.idLong }
                .forEach { overridesRem.add(it) }

            // re-add all perm-overrides of syncSource
            syncSource.getPermissionOverrides().forEach { override ->
                val type = if (override.isRoleOverride) PermOverrideData.ROLE_TYPE else PermOverrideData.MEMBER_TYPE
                val id = override.idLong

                overridesRem.remove(id)
                overridesAdd.put(
                    id,
                    PermOverrideData(type, id, override.allowedRaw, override.deniedRaw),
                )
            }

            set = set or ChannelManager.PERMISSION
        }
        return this as M
    }

    @Nonnull
    override fun setName(
        @Nonnull name: String,
    ): M {
        Checks.notBlank(name, "Name")
        var name = name
        name = name.trim()
        Checks.notEmpty(name, "Name")
        Checks.notLonger(name, Channel.MAX_NAME_LENGTH, "Name")
        this.name = name
        set = set or ChannelManager.NAME
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setType(
        @Nonnull type: ChannelType,
    ): M {
        Checks.check(
            type == ChannelType.TEXT || type == ChannelType.NEWS,
            "Can only change ChannelType to TEXT or NEWS",
        )

        if (this.type != ChannelType.TEXT && this.type != ChannelType.NEWS) {
            throw UnsupportedOperationException("Can only set ChannelType for TextChannel and NewsChannels")
        }
        if (type == ChannelType.NEWS && !getGuild().features.contains("NEWS")) {
            throw IllegalStateException("Can only set ChannelType to NEWS for guilds with NEWS feature")
        }

        this.type = type

        // If we've just set the type to be what the channel type already is,
        // then treat it as a reset, not a set.
        if (this.type == channel.type) {
            reset(ChannelManager.TYPE)
        } else {
            set = set or ChannelManager.TYPE
        }

        // After the type is changed, be sure to clean up any properties that are exclusive to a
        // specific channel type
        if (type != ChannelType.TEXT) {
            reset(ChannelManager.SLOWMODE)
        }

        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setRegion(
        @Nonnull region: Region,
    ): M {
        Checks.notNull(region, "Region")
        if (!type.isAudio) {
            throw IllegalStateException("Can only change region on audio channels!")
        }
        this.region = if (region == Region.AUTOMATIC) null else region.key
        set = set or ChannelManager.REGION
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setParent(category: Category?): M {
        if (type == ChannelType.CATEGORY) {
            throw IllegalStateException("Cannot set the parent of a category")
        }

        Checks.check(category == null || category.guild == getGuild(), "Category is not from the same guild")

        parent = category?.id
        set = set or ChannelManager.PARENT
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setPosition(position: Int): M {
        this.position = position
        set = set or ChannelManager.POSITION
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setTopic(topic: String?): M {
        Checks.checkSupportedChannelTypes(ChannelUtil.TOPIC_SUPPORTED, type, "topic")
        if (topic != null) {
            if (channel is IPostContainer) {
                Checks.notLonger(topic, IPostContainer.MAX_POST_CONTAINER_TOPIC_LENGTH, "Topic")
            } else {
                Checks.notLonger(topic, StandardGuildMessageChannel.MAX_TOPIC_LENGTH, "Topic")
            }
        }
        this.topic = topic
        set = set or ChannelManager.TOPIC
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setNSFW(nsfw: Boolean): M {
        Checks.checkSupportedChannelTypes(ChannelUtil.NSFW_SUPPORTED, type, "NSFW (age-restriction)")
        this.nsfw = nsfw
        set = set or ChannelManager.NSFW
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setSlowmode(slowmode: Int): M {
        Checks.checkSupportedChannelTypes(ChannelUtil.SLOWMODE_SUPPORTED, type, "slowmode")
        Checks.check(
            slowmode <= ISlowmodeChannel.MAX_SLOWMODE && slowmode >= 0,
            "Slowmode per user must be between 0 and %d (seconds)!",
            ISlowmodeChannel.MAX_SLOWMODE,
        )
        this.slowmode = slowmode
        set = set or ChannelManager.SLOWMODE
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setDefaultThreadSlowmode(slowmode: Int): M {
        Checks.check(
            channel is IThreadContainer,
            "Cannot set default thread slowmode on channels of type %s!",
            channel.type,
        )
        Checks.check(
            slowmode <= ISlowmodeChannel.MAX_SLOWMODE && slowmode >= 0,
            "Slowmode per user must be between 0 and %d (seconds)!",
            ISlowmodeChannel.MAX_SLOWMODE,
        )
        defaultThreadSlowmode = slowmode
        set = set or ChannelManager.DEFAULT_THREAD_SLOWMODE
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setUserLimit(userLimit: Int): M {
        Checks.notNegative(userLimit, "Userlimit")
        if (type == ChannelType.VOICE) {
            Checks.check(
                userLimit <= VoiceChannel.MAX_USERLIMIT,
                "Userlimit may not be greater than %d for voice channels",
                VoiceChannel.MAX_USERLIMIT,
            )
        } else if (type == ChannelType.STAGE) {
            Checks.check(
                userLimit <= StageChannel.MAX_USERLIMIT,
                "Userlimit may not be greater than %d for stage channels",
                StageChannel.MAX_USERLIMIT,
            )
        } else {
            throw IllegalStateException("Can only set userlimit on audio channels")
        }
        this.userLimit = userLimit
        set = set or ChannelManager.USERLIMIT
        return this as M
    }

    @Nonnull
    @CheckReturnValue
    fun setBitrate(bitrate: Int): M {
        if (!type.isAudio) {
            throw IllegalStateException("Can only set bitrate on voice channels")
        }
        val maxBitrate = getGuild().maxBitrate
        Checks.check(bitrate >= MIN_BITRATE, "Bitrate must be greater or equal to $MIN_BITRATE")
        Checks.check(bitrate <= maxBitrate, "Bitrate must be less or equal to %s", maxBitrate)
        this.bitrate = bitrate
        set = set or ChannelManager.BITRATE
        return this as M
    }

    fun setAutoArchiveDuration(autoArchiveDuration: ThreadChannel.AutoArchiveDuration): M {
        Checks.notNull(autoArchiveDuration, "autoArchiveDuration")

        if (!type.isThread) {
            throw IllegalStateException("Can only set autoArchiveDuration on threads")
        }

        this.autoArchiveDuration = autoArchiveDuration
        set = set or ChannelManager.AUTO_ARCHIVE_DURATION
        return this as M
    }

    fun setArchived(archived: Boolean): M {
        if (!type.isThread) {
            throw IllegalStateException("Can only set archived on threads")
        }

        if (isPermissionChecksEnabled()) {
            val thread = channel as ThreadChannel
            if (!thread.isOwner) {
                checkPermission(
                    Permission.MANAGE_THREADS,
                    "Cannot unarchive a thread without MANAGE_THREADS if not the thread owner",
                )
            }

            if (thread.isLocked) {
                checkPermission(
                    Permission.MANAGE_THREADS,
                    "Cannot unarchive a thread that is locked without MANAGE_THREADS",
                )
            }
        }

        this.archived = archived
        set = set or ChannelManager.ARCHIVED
        return this as M
    }

    fun setLocked(locked: Boolean): M {
        if (!type.isThread) {
            throw IllegalStateException("Can only set locked on threads")
        }

        if (isPermissionChecksEnabled()) {
            checkPermission(
                Permission.MANAGE_THREADS,
                "Cannot modified a thread's locked status without MANAGE_THREADS",
            )
        }

        this.locked = locked
        set = set or ChannelManager.LOCKED
        return this as M
    }

    fun setInvitable(invitable: Boolean): M {
        if (type != ChannelType.GUILD_PRIVATE_THREAD) {
            throw IllegalStateException("Can only set invitable on private threads.")
        }

        if (isPermissionChecksEnabled()) {
            val thread = channel as ThreadChannel
            if (!thread.isOwner) {
                checkPermission(
                    Permission.MANAGE_THREADS,
                    "Cannot modify a thread's invitable status without MANAGE_THREADS if not the thread owner",
                )
            }
        }

        this.invitable = invitable
        set = set or ChannelManager.INVITEABLE
        return this as M
    }

    fun setPinned(pinned: Boolean): M {
        if (!type.isThread) {
            throw IllegalStateException("Can only pin threads.")
        }
        if (pinned) {
            flags.add(ChannelFlag.PINNED)
        } else {
            flags.remove(ChannelFlag.PINNED)
        }
        set = set or ChannelManager.PINNED
        return this as M
    }

    fun setTagRequired(requireTag: Boolean): M {
        if (channel !is IPostContainer) {
            throw IllegalStateException("Can only set tag required flag on forum/media channels.")
        }
        if (requireTag) {
            flags.add(ChannelFlag.REQUIRE_TAG)
        } else {
            flags.remove(ChannelFlag.REQUIRE_TAG)
        }
        set = set or ChannelManager.REQUIRE_TAG
        return this as M
    }

    fun setHideMediaDownloadOption(hideOption: Boolean): M {
        if (channel !is MediaChannel) {
            throw IllegalStateException("Can only set hide media download flag on media channels.")
        }
        if (hideOption) {
            flags.add(ChannelFlag.HIDE_MEDIA_DOWNLOAD_OPTIONS)
        } else {
            flags.remove(ChannelFlag.HIDE_MEDIA_DOWNLOAD_OPTIONS)
        }
        set = set or ChannelManager.HIDE_MEDIA_DOWNLOAD_OPTIONS
        return this as M
    }

    fun setAvailableTags(tags: List<BaseForumTag>): M {
        if (channel !is IPostContainer) {
            throw IllegalStateException("Can only set available tags on forum/media channels.")
        }
        Checks.noneNull(tags, "Available Tags")
        availableTags = ArrayList(tags)
        set = set or ChannelManager.AVAILABLE_TAGS
        return this as M
    }

    @Suppress("ThrowsCount") // ported verbatim from the Java original; the multiple guards mirror its validation
    fun setAppliedTags(tags: Collection<ForumTagSnowflake>): M {
        if (type != ChannelType.GUILD_PUBLIC_THREAD) {
            throw IllegalStateException("Can only set applied tags on forum post thread channels.")
        }
        Checks.noneNull(tags, "Applied Tags")
        Checks.check(
            tags.size <= IPostContainer.MAX_POST_TAGS,
            "Cannot apply more than %d tags to a post thread!",
            ForumChannel.MAX_POST_TAGS,
        )
        val thread = getChannel() as ThreadChannel
        val parentChannel = thread.parentChannel
        if (parentChannel !is IPostContainer) {
            throw IllegalStateException("Cannot apply tags to threads outside of forum/media channels.")
        }
        if (tags.isEmpty() && parentChannel.asForumChannel().isTagRequired) {
            throw IllegalArgumentException(
                "Cannot remove all tags from a forum post which requires at least one tag! See IPostContainer#isRequireTag()",
            )
        }
        appliedTags = tags.stream().map { it.id }.collect(Collectors.toList())
        set = set or ChannelManager.APPLIED_TAGS
        return this as M
    }

    fun setDefaultReaction(emoji: Emoji?): M {
        if (channel !is IPostContainer) {
            throw IllegalStateException("Can only set default reaction on forum/media channels.")
        }
        defaultReactionEmoji = emoji
        set = set or ChannelManager.DEFAULT_REACTION
        return this as M
    }

    fun setDefaultLayout(layout: ForumChannel.Layout): M {
        if (type != ChannelType.FORUM) {
            throw IllegalStateException("Can only set default layout on forum channels.")
        }
        Checks.notNull(layout, "layout")
        if (layout == ForumChannel.Layout.UNKNOWN) {
            throw IllegalStateException("Layout type cannot be UNKNOWN.")
        }
        defaultLayout = layout.key
        set = set or ChannelManager.DEFAULT_LAYOUT
        return this as M
    }

    fun setDefaultSortOrder(sortOrder: IPostContainer.SortOrder): M {
        if (channel !is IPostContainer) {
            throw IllegalStateException("Can only set default layout on forum/media channels.")
        }
        Checks.notNull(sortOrder, "SortOrder")
        if (sortOrder == IPostContainer.SortOrder.UNKNOWN) {
            throw IllegalStateException("SortOrder type cannot be UNKNOWN.")
        }
        defaultSortOrder = sortOrder.key
        set = set or ChannelManager.DEFAULT_SORT_ORDER
        return this as M
    }

    override fun finalizeData(): RequestBody {
        val frame = DataObject.empty()
        if (shouldUpdate(ChannelManager.NAME)) {
            frame.put("name", name)
        }
        if (shouldUpdate(ChannelManager.TYPE)) {
            frame.put("type", type.id)
        }
        if (shouldUpdate(ChannelManager.POSITION)) {
            frame.put("position", position)
        }
        if (shouldUpdate(ChannelManager.TOPIC)) {
            frame.put("topic", topic)
        }
        if (shouldUpdate(ChannelManager.NSFW)) {
            frame.put("nsfw", nsfw)
        }
        if (shouldUpdate(ChannelManager.SLOWMODE)) {
            frame.put("rate_limit_per_user", slowmode)
        }
        if (shouldUpdate(ChannelManager.DEFAULT_THREAD_SLOWMODE)) {
            frame.put("default_thread_rate_limit_per_user", defaultThreadSlowmode)
        }
        if (shouldUpdate(ChannelManager.USERLIMIT)) {
            frame.put("user_limit", userLimit)
        }
        if (shouldUpdate(ChannelManager.BITRATE)) {
            frame.put("bitrate", bitrate)
        }
        if (shouldUpdate(ChannelManager.PARENT)) {
            frame.put("parent_id", parent)
        }
        if (shouldUpdate(ChannelManager.REGION)) {
            frame.put("rtc_region", region)
        }
        if (shouldUpdate(ChannelManager.AUTO_ARCHIVE_DURATION)) {
            frame.put("auto_archive_duration", autoArchiveDuration!!.minutes)
        }
        if (shouldUpdate(ChannelManager.ARCHIVED)) {
            frame.put("archived", archived)
        }
        if (shouldUpdate(ChannelManager.LOCKED)) {
            frame.put("locked", locked)
        }
        if (shouldUpdate(ChannelManager.INVITEABLE)) {
            frame.put("invitable", invitable)
        }
        if (shouldUpdate(ChannelManager.AVAILABLE_TAGS)) {
            frame.put("available_tags", DataArray.fromCollection(availableTags!!))
        }
        if (shouldUpdate(ChannelManager.APPLIED_TAGS)) {
            frame.put("applied_tags", DataArray.fromCollection(appliedTags!!))
        }
        if (shouldUpdate(ChannelManager.PINNED or ChannelManager.REQUIRE_TAG or ChannelManager.HIDE_MEDIA_DOWNLOAD_OPTIONS)) {
            frame.put("flags", ChannelFlag.getRaw(flags))
        }
        if (shouldUpdate(ChannelManager.DEFAULT_REACTION)) {
            if (defaultReactionEmoji is CustomEmoji) {
                frame.put(
                    "default_reaction_emoji",
                    DataObject.empty().put("emoji_id", (defaultReactionEmoji as CustomEmoji).id),
                )
            } else if (defaultReactionEmoji is UnicodeEmoji) {
                frame.put(
                    "default_reaction_emoji",
                    DataObject.empty().put("emoji_name", defaultReactionEmoji!!.name),
                )
            } else {
                frame.put("default_reaction_emoji", null)
            }
        }
        if (shouldUpdate(ChannelManager.DEFAULT_LAYOUT)) {
            frame.put("default_forum_layout", defaultLayout)
        }
        if (shouldUpdate(ChannelManager.DEFAULT_SORT_ORDER)) {
            frame.put("default_sort_order", defaultSortOrder)
        }

        withLock(lock) {
            if (shouldUpdate(ChannelManager.PERMISSION)) {
                frame.put("permission_overwrites", getOverrides())
            }
        }

        reset()
        return getRequestBody(frame)
    }

    override fun checkPermissions(): Boolean {
        val selfMember = getGuild().selfMember

        Checks.checkAccess(selfMember, channel)
        (channel as GuildChannelMixin<*>).checkCanManage()

        return super.checkPermissions()
    }

    protected fun checkPermission(
        permission: Permission,
        errMessage: String,
    ) {
        if (!getGuild().selfMember.hasPermission(getChannel(), permission)) {
            throw InsufficientPermissionException(getChannel(), permission, errMessage)
        }
    }

    protected fun getOverrides(): Collection<PermOverrideData> {
        // note: overridesAdd and overridesRem are mutually disjoint
        val data = TLongObjectHashMap(overridesAdd)

        val impl = getChannel() as IPermissionContainerMixin<*>
        impl.permissionOverrideMap.forEachEntry { id, override ->
            // removed by not adding them here, this data set overrides the existing one
            // we can use remove because it will be reset afterwards either way
            if (!overridesRem.remove(id) && !data.containsKey(id)) {
                data.put(id, PermOverrideData(override))
            }
            true
        }
        return ArrayList(data.valueCollection())
    }
}
