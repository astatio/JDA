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

package net.dv8tion.jda.internal.entities.channel.concrete.detached

import gnu.trove.map.TLongObjectMap
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.managers.channel.concrete.CategoryManager
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.requests.restaction.order.CategoryOrderAction
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IInteractionPermissionMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.CategoryMixin
import net.dv8tion.jda.internal.interactions.ChannelInteractionPermissions
import javax.annotation.Nonnull

class DetachedCategoryImpl(
    id: Long,
    guild: Guild,
) : AbstractGuildChannelImpl<DetachedCategoryImpl>(id, guild),
    Category,
    CategoryMixin<DetachedCategoryImpl>,
    IInteractionPermissionMixin<DetachedCategoryImpl> {
    private var interactionPermissionsValue: ChannelInteractionPermissions? = null

    private var position: Int = 0

    override fun isDetached(): Boolean = true

    @Nonnull
    override fun getType(): ChannelType = ChannelType.CATEGORY

    override fun getPositionRaw(): Int = position

    @Nonnull
    override fun createTextChannel(
        @Nonnull name: String,
    ): ChannelAction<TextChannel> = throw detachedException()

    @Nonnull
    override fun createNewsChannel(
        @Nonnull name: String,
    ): ChannelAction<NewsChannel> = throw detachedException()

    @Nonnull
    override fun createVoiceChannel(
        @Nonnull name: String,
    ): ChannelAction<VoiceChannel> = throw detachedException()

    @Nonnull
    override fun createStageChannel(
        @Nonnull name: String,
    ): ChannelAction<StageChannel> = throw detachedException()

    @Nonnull
    override fun createForumChannel(
        @Nonnull name: String,
    ): ChannelAction<ForumChannel> = throw detachedException()

    @Nonnull
    override fun createMediaChannel(
        @Nonnull name: String,
    ): ChannelAction<MediaChannel> = throw detachedException()

    @Nonnull
    override fun modifyTextChannelPositions(): CategoryOrderAction = throw detachedException()

    @Nonnull
    override fun modifyVoiceChannelPositions(): CategoryOrderAction = throw detachedException()

    @Nonnull
    override fun createCopy(): ChannelAction<Category> = throw detachedException()

    @Nonnull
    override fun getManager(): CategoryManager = throw detachedException()

    override val permissionOverrideMap: TLongObjectMap<PermissionOverride>
        get() = throw detachedException()

    @Nonnull
    override val interactionPermissions: ChannelInteractionPermissions
        get() = interactionPermissionsValue!!

    override fun setPosition(position: Int): DetachedCategoryImpl {
        this.position = position
        return this
    }

    @Nonnull
    override fun setInteractionPermissions(
        @Nonnull interactionPermissions: ChannelInteractionPermissions,
    ): DetachedCategoryImpl {
        this.interactionPermissionsValue = interactionPermissions
        return this
    }
}
