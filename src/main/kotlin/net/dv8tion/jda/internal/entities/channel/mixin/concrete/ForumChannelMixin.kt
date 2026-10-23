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

package net.dv8tion.jda.internal.entities.channel.mixin.concrete

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.attribute.IPostContainer
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IAgeRestrictedChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IPostContainerMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.ISlowmodeChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.ITopicChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IWebhookContainerMixin
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.StandardGuildChannelMixin
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull

interface ForumChannelMixin<T : ForumChannelMixin<T>> :
    ForumChannel,
    StandardGuildChannelMixin<T>,
    IAgeRestrictedChannelMixin<T>,
    ISlowmodeChannelMixin<T>,
    IWebhookContainerMixin<T>,
    IPostContainerMixin<T>,
    ITopicChannelMixin<T> {
    @Nonnull
    override fun createCopy(
        @Nonnull guild: Guild,
    ): ChannelAction<ForumChannel> {
        Checks.notNull(guild, "Guild")
        val action =
            guild
                .createForumChannel(name)
                .setNSFW(isNSFW)
                .setTopic(topic)
                .setSlowmode(slowmode)
                .setAvailableTags(availableTags)
                .setDefaultLayout(defaultLayout)
        if (rawSortOrder != -1) {
            action.setDefaultSortOrder(IPostContainer.SortOrder.fromKey(rawSortOrder))
        }
        if (defaultReaction is UnicodeEmoji) {
            action.setDefaultReaction(defaultReaction)
        }
        if (guild == this.guild) {
            val parent = parentCategory
            action.setDefaultReaction(defaultReaction)
            if (parent != null) {
                action.setParent(parent)
            }
            for (o in permissionOverrideMap.valueCollection()) {
                if (o.isMemberOverride) {
                    action.addMemberPermissionOverride(o.idLong, o.allowedRaw, o.deniedRaw)
                } else {
                    action.addRolePermissionOverride(o.idLong, o.allowedRaw, o.deniedRaw)
                }
            }
        }
        return action
    }

    fun setDefaultLayout(layout: Int): T
}
