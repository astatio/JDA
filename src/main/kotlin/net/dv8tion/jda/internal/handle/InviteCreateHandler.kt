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

package net.dv8tion.jda.internal.handle

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.InviteImpl
import java.time.OffsetDateTime
import java.util.Optional

class InviteCreateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getUnsignedLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }
        val realGuild: Guild? = getJDA().getGuildById(guildId)
        if (realGuild == null) {
            EventCache.LOG.debug("Caching INVITE_CREATE for unknown guild with id {}", guildId)
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            return null
        }

        val channelId = content.getUnsignedLong("channel_id")
        val realChannel: GuildChannel? = realGuild.getGuildChannelById(channelId)
        if (realChannel == null) {
            EventCache.LOG.debug(
                "Caching INVITE_CREATE for unknown channel with id {} in guild with id {}",
                channelId,
                guildId,
            )
            getJDA().getEventCache().cache(EventCache.Type.CHANNEL, channelId, responseNumber, allContent, this::handle)
            return null
        }

        val code = content.getString("code")
        val temporary = content.getBoolean("temporary")
        val guest = (content.getInt("flags", 0) and 1) == 1
        val maxAge = content.getInt("max_age", -1)
        val maxUses = content.getInt("max_uses", -1)
        val creationTime: OffsetDateTime? =
            content
                .opt("created_at")
                .map { obj: Any -> obj.toString() }
                .map { s: String -> OffsetDateTime.parse(s) }
                .orElse(null)

        val inviterJson: Optional<DataObject> = content.optObject("inviter")
        val expanded = maxUses != -1

        val inviter: User? =
            inviterJson
                .map { json: DataObject -> getJDA().getEntityBuilder().createUser(json) }
                .orElse(null)
        val channel = InviteImpl.ChannelImpl(realChannel)
        val guild = InviteImpl.GuildImpl(realGuild)

        val targetType = Invite.TargetType.fromId(content.getInt("target_type", 0))
        val target: Invite.InviteTarget? =
            when (targetType) {
                Invite.TargetType.STREAM -> {
                    val targetUserObject = content.getObject("target_user")
                    InviteImpl.InviteTargetImpl(
                        targetType,
                        null,
                        getJDA().getEntityBuilder().createUser(targetUserObject),
                    )
                }

                Invite.TargetType.EMBEDDED_APPLICATION -> {
                    val applicationObject = content.getObject("target_application")
                    val application =
                        InviteImpl.EmbeddedApplicationImpl(
                            applicationObject.getString("icon", null),
                            applicationObject.getString("name"),
                            applicationObject.getString("description"),
                            applicationObject.getString("summary"),
                            applicationObject.getLong("id"),
                            applicationObject.getInt("max_participants", -1),
                        )
                    InviteImpl.InviteTargetImpl(targetType, application, null)
                }

                Invite.TargetType.NONE -> null

                else -> InviteImpl.InviteTargetImpl(targetType, null, null)
            }

        val invite: Invite =
            InviteImpl(
                getJDA(),
                code,
                expanded,
                inviter,
                maxAge,
                maxUses,
                temporary,
                guest,
                creationTime,
                0,
                channel,
                guild,
                null,
                target,
                Invite.InviteType.GUILD,
            )
        getJDA().handleEvent(GuildInviteCreateEvent(getJDA(), responseNumber, invite, realChannel))
        return null
    }
}
