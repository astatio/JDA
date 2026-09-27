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

package net.dv8tion.jda.internal.entities.channel

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.GroupChannel
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.mixin.ChannelMixin
import net.dv8tion.jda.internal.utils.ChannelUtil
import net.dv8tion.jda.internal.utils.EntityString
import javax.annotation.Nonnull

abstract class AbstractChannelImpl<T : AbstractChannelImpl<T>>(
    id: Long,
    api: JDA,
) : ChannelMixin<T> {
    @JvmField
    protected val id: Long = id

    @JvmField
    protected val api: JDAImpl = api as JDAImpl

    @JvmField
    protected var name: String? = null

    @Nonnull
    override fun getJDA(): JDA = api

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getName(): String = name as String

    @Suppress("UNCHECKED_CAST")
    override fun setName(name: String): T {
        this.name = name
        return this as T
    }

    // -- Union Hooks --

    @Nonnull
    override fun asPrivateChannel(): PrivateChannel = ChannelUtil.safeChannelCast(this, PrivateChannel::class.java)

    @Nonnull
    override fun asGroupChannel(): GroupChannel = ChannelUtil.safeChannelCast(this, GroupChannel::class.java)

    @Nonnull
    override fun asTextChannel(): TextChannel = ChannelUtil.safeChannelCast(this, TextChannel::class.java)

    @Nonnull
    override fun asNewsChannel(): NewsChannel = ChannelUtil.safeChannelCast(this, NewsChannel::class.java)

    @Nonnull
    override fun asVoiceChannel(): VoiceChannel = ChannelUtil.safeChannelCast(this, VoiceChannel::class.java)

    @Nonnull
    override fun asStageChannel(): StageChannel = ChannelUtil.safeChannelCast(this, StageChannel::class.java)

    @Nonnull
    override fun asThreadChannel(): ThreadChannel = ChannelUtil.safeChannelCast(this, ThreadChannel::class.java)

    @Nonnull
    override fun asCategory(): Category = ChannelUtil.safeChannelCast(this, Category::class.java)

    @Nonnull
    override fun asForumChannel(): ForumChannel = ChannelUtil.safeChannelCast(this, ForumChannel::class.java)

    @Nonnull
    override fun asMediaChannel(): MediaChannel = ChannelUtil.safeChannelCast(this, MediaChannel::class.java)

    @Nonnull
    override fun asMessageChannel(): MessageChannel = ChannelUtil.safeChannelCast(this, MessageChannel::class.java)

    @Nonnull
    override fun asAudioChannel(): AudioChannel = ChannelUtil.safeChannelCast(this, AudioChannel::class.java)

    @Nonnull
    override fun asThreadContainer(): IThreadContainer = ChannelUtil.safeChannelCast(this, IThreadContainer::class.java)

    @Nonnull
    override fun asGuildChannel(): GuildChannel = ChannelUtil.safeChannelCast(this, GuildChannel::class.java)

    @Nonnull
    override fun asGuildMessageChannel(): GuildMessageChannel = ChannelUtil.safeChannelCast(this, GuildMessageChannel::class.java)

    @Nonnull
    override fun asStandardGuildChannel(): StandardGuildChannel = ChannelUtil.safeChannelCast(this, StandardGuildChannel::class.java)

    @Nonnull
    override fun asStandardGuildMessageChannel(): StandardGuildMessageChannel =
        ChannelUtil.safeChannelCast(this, StandardGuildMessageChannel::class.java)

    // Explicit override avoids Kotlin emitting a bridge method here; Java subclasses overriding
    // detachedException() would otherwise trigger -Xlint:overrides.
    override fun detachedException(): RuntimeException = super.detachedException()

    override fun toString(): String = EntityString(this).setName(name as String).toString()
}
