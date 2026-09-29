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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.ISlowmodeChannelMixin
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.StandardGuildMessageChannelMixin
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull

interface TextChannelMixin<T : TextChannelMixin<T>> :
    TextChannel,
    StandardGuildMessageChannelMixin<T>,
    ISlowmodeChannelMixin<T> {
    @Nonnull
    override fun createCopy(
        @Nonnull guild: Guild,
    ): ChannelAction<TextChannel> {
        Checks.notNull(guild, "Guild")
        val action =
            guild
                .createTextChannel(name)
                .setNSFW(isNSFW)
                .setTopic(topic)
                .setSlowmode(slowmode)
        if (guild == this.guild) {
            val parent = parentCategory
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
}
