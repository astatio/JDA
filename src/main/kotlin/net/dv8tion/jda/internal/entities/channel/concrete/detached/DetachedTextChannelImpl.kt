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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractStandardGuildMessageChannelImpl
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IInteractionPermissionMixin
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.TextChannelMixin
import net.dv8tion.jda.internal.interactions.ChannelInteractionPermissions
import javax.annotation.Nonnull

class DetachedTextChannelImpl(
    id: Long,
    guild: Guild,
) : AbstractStandardGuildMessageChannelImpl<DetachedTextChannelImpl>(id, guild),
    TextChannel,
    DefaultGuildChannelUnion,
    TextChannelMixin<DetachedTextChannelImpl>,
    IInteractionPermissionMixin<DetachedTextChannelImpl> {
    private var slowmode: Int = 0
    private var interactionPermissionsValue: ChannelInteractionPermissions? = null

    override fun isDetached(): Boolean = true

    @Nonnull
    override fun getType(): ChannelType = ChannelType.TEXT

    @Nonnull
    override fun getMembers(): List<Member> = throw detachedException()

    override fun getSlowmode(): Int = slowmode

    @Nonnull
    override fun getManager(): TextChannelManager = throw detachedException()

    @Nonnull
    override val interactionPermissions: ChannelInteractionPermissions
        get() = interactionPermissionsValue!!

    override fun setSlowmode(slowmode: Int): DetachedTextChannelImpl {
        this.slowmode = slowmode
        return this
    }

    @Nonnull
    override fun setInteractionPermissions(
        @Nonnull interactionPermissions: ChannelInteractionPermissions,
    ): DetachedTextChannelImpl {
        this.interactionPermissionsValue = interactionPermissions
        return this
    }
}
