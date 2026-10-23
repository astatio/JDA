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

package net.dv8tion.jda.internal.entities.channel.mixin.attribute

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.entities.channel.attribute.IInviteContainer
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.InviteAction
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.GuildChannelMixin
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.InviteActionImpl
import javax.annotation.Nonnull

interface IInviteContainerMixin<T : IInviteContainerMixin<T>> :
    IInviteContainer,
    GuildChannelMixin<T> {
    // ---- Default implementations of interface ----
    @Nonnull
    override fun createInvite(): InviteAction {
        checkAttached()
        checkPermission(Permission.CREATE_INSTANT_INVITE)

        return InviteActionImpl(jda, id)
    }

    @Nonnull
    override fun retrieveInvites(): RestAction<List<Invite>> {
        checkAttached()
        checkPermission(Permission.MANAGE_CHANNEL)

        val route = Route.Invites.GET_CHANNEL_INVITES.compile(id)

        val jda = jda as JDAImpl
        return RestActionImpl(jda, route) { response, _ ->
            val entityBuilder = jda.entityBuilder
            val array = response.array
            val invites = ArrayList<Invite>(array.length())
            for (i in 0 until array.length()) {
                invites.add(entityBuilder.createInvite(array.getObject(i)))
            }
            java.util.Collections.unmodifiableList(invites)
        }
    }
}
