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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion
import net.dv8tion.jda.api.managers.channel.concrete.NewsChannelManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractStandardGuildMessageChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.NewsChannelMixin
import net.dv8tion.jda.internal.managers.channel.concrete.NewsChannelManagerImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import javax.annotation.Nonnull

class NewsChannelImpl(
    id: Long,
    guild: GuildImpl,
) : AbstractStandardGuildMessageChannelImpl<NewsChannelImpl>(id, guild),
    NewsChannel,
    DefaultGuildChannelUnion,
    NewsChannelMixin<NewsChannelImpl> {
    override fun isDetached(): Boolean = false

    @Nonnull
    override fun getGuild(): GuildImpl = super.getGuild() as GuildImpl

    @Nonnull
    override fun getType(): ChannelType = ChannelType.NEWS

    @Nonnull
    override fun getMembers(): List<Member> =
        getGuild()
            .membersView
            .stream()
            .filter { m -> m.hasPermission(this, Permission.VIEW_CHANNEL) }
            .collect(Helpers.toUnmodifiableList())

    @Nonnull
    override fun follow(
        @Nonnull targetChannelId: String,
    ): RestAction<Webhook.WebhookReference> {
        Checks.notNull(targetChannelId, "Target Channel ID")

        val route = Route.Channels.FOLLOW_CHANNEL.compile(getId())
        val body = DataObject.empty().put("webhook_channel_id", targetChannelId)
        return RestActionImpl(jda, route, body) { response, request ->
            val json = response.getObject()
            Webhook.WebhookReference(
                request.jda,
                json.getUnsignedLong("webhook_id"),
                json.getUnsignedLong("channel_id"),
            )
        }
    }

    @Nonnull
    override fun getManager(): NewsChannelManager = NewsChannelManagerImpl(this)
}
