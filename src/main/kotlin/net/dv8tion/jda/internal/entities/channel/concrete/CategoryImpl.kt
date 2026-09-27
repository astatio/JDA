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
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.managers.channel.concrete.CategoryManager
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.requests.restaction.order.CategoryOrderAction
import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractGuildChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.CategoryMixin
import net.dv8tion.jda.internal.managers.channel.concrete.CategoryManagerImpl
import net.dv8tion.jda.internal.utils.PermissionUtil
import javax.annotation.Nonnull

class CategoryImpl(
    id: Long,
    guild: GuildImpl,
) : AbstractGuildChannelImpl<CategoryImpl>(id, guild),
    Category,
    CategoryMixin<CategoryImpl> {
    private val overrides: TLongObjectMap<PermissionOverride> = MiscUtil.newLongMap()

    private var position: Int = 0

    override fun isDetached(): Boolean = false

    @Nonnull
    override fun getGuild(): GuildImpl = super.getGuild() as GuildImpl

    @Nonnull
    override fun getType(): ChannelType = ChannelType.CATEGORY

    override fun getPositionRaw(): Int = position

    @Nonnull
    override fun createTextChannel(
        @Nonnull name: String,
    ): ChannelAction<TextChannel> = trySync(getGuild().createTextChannel(name, this))

    @Nonnull
    override fun createNewsChannel(
        @Nonnull name: String,
    ): ChannelAction<NewsChannel> = trySync(getGuild().createNewsChannel(name, this))

    @Nonnull
    override fun createVoiceChannel(
        @Nonnull name: String,
    ): ChannelAction<VoiceChannel> = trySync(getGuild().createVoiceChannel(name, this))

    @Nonnull
    override fun createStageChannel(
        @Nonnull name: String,
    ): ChannelAction<StageChannel> = trySync(getGuild().createStageChannel(name, this))

    @Nonnull
    override fun createForumChannel(
        @Nonnull name: String,
    ): ChannelAction<ForumChannel> = trySync(getGuild().createForumChannel(name, this))

    @Nonnull
    override fun createMediaChannel(
        @Nonnull name: String,
    ): ChannelAction<MediaChannel> = trySync(getGuild().createMediaChannel(name, this))

    @Nonnull
    override fun modifyTextChannelPositions(): CategoryOrderAction = getGuild().modifyTextChannelPositions(this)

    @Nonnull
    override fun modifyVoiceChannelPositions(): CategoryOrderAction = getGuild().modifyVoiceChannelPositions(this)

    @Nonnull
    override fun createCopy(): ChannelAction<Category> = createCopy(getGuild())

    @Nonnull
    override fun getManager(): CategoryManager = CategoryManagerImpl(this)

    override val permissionOverrideMap: TLongObjectMap<PermissionOverride>
        get() = overrides

    override fun setPosition(position: Int): CategoryImpl {
        this.position = position
        return this
    }

    @Suppress("ReturnCount") // faithfully ported early-return guard chain
    private fun <T : GuildChannel> trySync(action: ChannelAction<T>): ChannelAction<T> {
        val selfMember: Member = getGuild().selfMember
        if (!selfMember.canSync(this)) {
            val botPerms = PermissionUtil.getEffectivePermission(this, selfMember)
            for (override in permissionOverrides) {
                val perms = override.deniedRaw or override.allowedRaw
                if ((perms and botPerms.inv()) != 0L) {
                    return action
                }
            }
        }
        return action.syncPermissionOverrides()
    }
}
