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
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Widget
import net.dv8tion.jda.api.entities.Widget.Member
import net.dv8tion.jda.api.entities.Widget.VoiceState
import net.dv8tion.jda.api.utils.DiscordAssets
import net.dv8tion.jda.api.utils.ImageFormat
import net.dv8tion.jda.api.utils.ImageProxy
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Collections
import javax.annotation.Nonnull
import javax.annotation.Nullable

class WidgetImpl : Widget {
    private val isAvailable: Boolean
    private val id: Long
    private val name: String?
    private val invite: String?
    private val channels: TLongObjectMap<VoiceChannelImpl>
    private val members: TLongObjectMap<Member>

    /**
     * Constructs an unavailable Widget
     */
    constructor(guildId: Long) {
        isAvailable = false
        id = guildId
        name = null
        invite = null
        channels = TLongObjectHashMap()
        members = TLongObjectHashMap()
    }

    /**
     * Constructs an available Widget
     *
     * @param json
     *        The [net.dv8tion.jda.api.utils.data.DataObject] to construct the Widget from
     */
    constructor(json: DataObject) {
        var inviteCode = json.getString("instant_invite", null)
        if (inviteCode != null) {
            inviteCode = inviteCode.substring(inviteCode.lastIndexOf("/") + 1)
        }

        isAvailable = true
        id = json.getLong("id")
        name = json.getString("name")
        invite = inviteCode
        channels = MiscUtil.newLongMap()
        members = MiscUtil.newLongMap()

        val channelsJson = json.getArray("channels")
        for (i in 0 until channelsJson.length()) {
            val channel = channelsJson.getObject(i)
            channels.put(channel.getLong("id"), VoiceChannelImpl(channel, this))
        }

        val membersJson = json.getArray("members")
        for (i in 0 until membersJson.length()) {
            val memberJson = membersJson.getObject(i)
            val member = MemberImpl(memberJson, this)
            if (!memberJson.isNull("channel_id")) { // voice state
                val channel = channels.get(memberJson.getLong("channel_id"))
                member.setVoiceState(
                    VoiceStateImpl(
                        channel,
                        memberJson.getBoolean("mute"),
                        memberJson.getBoolean("deaf"),
                        memberJson.getBoolean("suppress"),
                        memberJson.getBoolean("self_mute"),
                        memberJson.getBoolean("self_deaf"),
                        member,
                        this,
                    ),
                )
                channel.addMember(member)
            }
            members.put(member.idLong, member)
        }
    }

    override fun isAvailable(): Boolean = isAvailable

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getName(): String {
        checkAvailable()

        return name!!
    }

    @Nullable
    override fun getInviteCode(): String? {
        checkAvailable()

        return invite
    }

    @Nonnull
    override fun getVoiceChannels(): List<Widget.VoiceChannel> {
        checkAvailable()

        return Collections.unmodifiableList(ArrayList(channels.valueCollection()))
    }

    @Nullable
    override fun getVoiceChannelById(id: String): Widget.VoiceChannel? {
        checkAvailable()

        return channels.get(MiscUtil.parseSnowflake(id))
    }

    @Nullable
    override fun getVoiceChannelById(id: Long): Widget.VoiceChannel? {
        checkAvailable()

        return channels.get(id)
    }

    @Nonnull
    override fun getMembers(): List<Member> {
        checkAvailable()

        return Collections.unmodifiableList(ArrayList(members.valueCollection()))
    }

    @Nullable
    override fun getMemberById(id: String): Member? {
        checkAvailable()

        return members.get(MiscUtil.parseSnowflake(id))
    }

    @Nullable
    override fun getMemberById(id: Long): Member? {
        checkAvailable()

        return members.get(id)
    }

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is WidgetImpl) {
            return false
        }
        return this === other || this.id == other.idLong
    }

    override fun toString(): String {
        val entityString = EntityString(this)
        if (isAvailable()) {
            entityString.setName(getName())
        }
        return entityString.toString()
    }

    private fun checkAvailable() {
        if (!isAvailable) {
            throw IllegalStateException("The widget for this Guild is unavailable!")
        }
    }

    class MemberImpl internal constructor(
        json: DataObject,
        private val widget: WidgetImpl,
    ) : Member {
        private val bot: Boolean = json.getBoolean("bot")
        private val id: Long = json.getLong("id")
        private val username: String = json.getString("username")
        private val discriminator: String = json.getString("discriminator")
        private val avatar: String? = json.getString("avatar", null)
        private val nickname: String? = json.getString("nick", null)
        private val status: OnlineStatus = OnlineStatus.fromKey(json.getString("status"))
        private val game: Activity? = if (json.isNull("game")) null else EntityBuilder.createActivity(json.getObject("game"))
        private var state: VoiceState? = null

        internal fun setVoiceState(voiceState: VoiceState?) {
            state = voiceState
        }

        override fun isBot(): Boolean = bot

        @Nonnull
        override fun getName(): String = username

        override fun getIdLong(): Long = id

        @Nonnull
        override fun getAsMention(): String = "<@" + getId() + ">"

        @Nonnull
        override fun getDiscriminator(): String = discriminator

        @Nullable
        override fun getAvatarId(): String? = avatar

        @Nullable
        override fun getAvatarUrl(): String? {
            val avatarId = getAvatarId()
            return if (avatarId == null) {
                null
            } else {
                getAvatarUrl(if (avatarId.startsWith("a_")) ImageFormat.ANIMATED_WEBP else ImageFormat.PNG)
            }
        }

        @Nullable
        override fun getAvatarUrl(format: ImageFormat): String? {
            val proxy = getAvatar(format)
            return proxy?.url
        }

        @Nullable
        override fun getAvatar(): ImageProxy? {
            val avatarUrl = getAvatarUrl()
            return if (avatarUrl == null) null else ImageProxy(avatarUrl)
        }

        @Nullable
        override fun getAvatar(format: ImageFormat): ImageProxy? = DiscordAssets.userAvatar(format, getId(), avatar)

        @Nonnull
        override fun getDefaultAvatarId(): String = (Integer.parseInt(getDiscriminator()) % LEGACY_DEFAULT_AVATAR_COUNT).toString()

        @Nonnull
        override fun getDefaultAvatarUrl(): String = getDefaultAvatar().url

        @Nonnull
        override fun getDefaultAvatar(): ImageProxy = DiscordAssets.userDefaultAvatar(ImageFormat.PNG, getDefaultAvatarId())

        @Nonnull
        override fun getEffectiveAvatarUrl(): String {
            val avatarUrl = getAvatarUrl()
            return avatarUrl ?: getDefaultAvatarUrl()
        }

        @Nonnull
        override fun getEffectiveAvatarUrl(preferredFormat: ImageFormat): String {
            val avatarUrl = getAvatarUrl(preferredFormat)
            // Preserves the original Java behavior, which returns the avatar hash rather than its URL here.
            return avatarUrl ?: avatar!!
        }

        @Nonnull
        override fun getEffectiveAvatar(): ImageProxy = ImageProxy(getEffectiveAvatarUrl())

        @Nonnull
        override fun getEffectiveAvatar(preferredFormat: ImageFormat): ImageProxy {
            val avatar = getAvatar(preferredFormat)
            return avatar ?: getDefaultAvatar()
        }

        @Nullable
        override fun getNickname(): String? = nickname

        @Nonnull
        override fun getEffectiveName(): String = nickname ?: username

        @Nonnull
        override fun getOnlineStatus(): OnlineStatus = status

        @Nullable
        override fun getActivity(): Activity? = game

        @Nonnull
        override fun getVoiceState(): VoiceState = state ?: VoiceStateImpl(this, widget)

        @Nonnull
        override fun getWidget(): WidgetImpl = widget

        override fun hashCode(): Int = (widget.getId() + ' ' + id).hashCode()

        override fun equals(other: Any?): Boolean {
            if (other !is Member) {
                return false
            }
            return this === other ||
                (this.id == other.idLong && this.widget.idLong == other.widget.idLong)
        }

        override fun toString(): String = EntityString(this).setName(getName()).toString()
    }

    class VoiceChannelImpl internal constructor(
        json: DataObject,
        private val widget: Widget,
    ) : Widget.VoiceChannel {
        private val position: Int = json.getInt("position")
        private val id: Long = json.getLong("id")
        private val name: String = json.getString("name")
        private val members: MutableList<Member> = ArrayList()

        internal fun addMember(member: Member) {
            members.add(member)
        }

        override fun getPosition(): Int = position

        override fun getIdLong(): Long = id

        @Nonnull
        override fun getName(): String = name

        @Nonnull
        override fun getMembers(): List<Member> = members

        @Nonnull
        override fun getWidget(): Widget = widget

        override fun hashCode(): Int = id.hashCode()

        override fun equals(other: Any?): Boolean {
            if (other !is Widget.VoiceChannel) {
                return false
            }
            return this === other || this.id == other.idLong
        }

        override fun toString(): String = EntityString(this).setName(getName()).toString()
    }

    class VoiceStateImpl internal constructor(
        @Nullable private val channel: Widget.VoiceChannel?,
        private val muted: Boolean,
        private val deafened: Boolean,
        private val suppress: Boolean,
        private val selfMute: Boolean,
        private val selfDeaf: Boolean,
        private val member: Member,
        private val widget: Widget,
    ) : Widget.VoiceState {
        internal constructor(member: Member, widget: Widget) : this(null, false, false, false, false, false, member, widget)

        @Nullable
        override fun getChannel(): Widget.VoiceChannel? = channel

        override fun inVoiceChannel(): Boolean = channel != null

        override fun isGuildMuted(): Boolean = muted

        override fun isGuildDeafened(): Boolean = deafened

        override fun isSuppressed(): Boolean = suppress

        override fun isSelfMuted(): Boolean = selfMute

        override fun isSelfDeafened(): Boolean = selfDeaf

        override fun isMuted(): Boolean = selfMute || muted

        override fun isDeafened(): Boolean = selfDeaf || deafened

        @Nonnull
        override fun getMember(): Member = member

        @Nonnull
        override fun getWidget(): Widget = widget

        override fun hashCode(): Int = member.hashCode()

        override fun equals(other: Any?): Boolean {
            if (other !is Widget.VoiceState) {
                return false
            }
            return this === other || (this.member == other.member && this.widget == other.widget)
        }

        override fun toString(): String =
            EntityString(this)
                .setName(widget.name)
                .addMetadata("memberName", member.effectiveName)
                .toString()
    }
}

// The legacy discriminator-based avatar system used modulo-5 buckets.
private const val LEGACY_DEFAULT_AVATAR_COUNT = 5
