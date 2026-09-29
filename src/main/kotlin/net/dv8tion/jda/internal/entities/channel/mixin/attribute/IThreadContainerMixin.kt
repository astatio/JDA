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
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.GuildChannelMixin
import net.dv8tion.jda.internal.requests.restaction.ThreadChannelActionImpl
import net.dv8tion.jda.internal.requests.restaction.pagination.ThreadChannelPaginationActionImpl
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull

interface IThreadContainerMixin<T : IThreadContainerMixin<T>> :
    IThreadContainer,
    IThreadContainerUnion,
    GuildChannelMixin<T> {
    // ---- Default implementations of interface ----
    @Nonnull
    override fun createThreadChannel(
        @Nonnull name: String,
        isPrivate: Boolean,
    ): ThreadChannelAction {
        Checks.notNull(name, "Name")
        val name = name.trim()
        Checks.notEmpty(name, "Name")
        Checks.notLonger(name, Channel.MAX_NAME_LENGTH, "Name")

        checkAttached()
        Checks.checkAccess(guild.selfMember, this)
        if (isPrivate) {
            checkPermission(Permission.CREATE_PRIVATE_THREADS)
        } else {
            checkPermission(Permission.CREATE_PUBLIC_THREADS)
        }

        val threadType =
            if (isPrivate) {
                ChannelType.GUILD_PRIVATE_THREAD
            } else {
                if (type == ChannelType.TEXT) ChannelType.GUILD_PUBLIC_THREAD else ChannelType.GUILD_NEWS_THREAD
            }

        return ThreadChannelActionImpl(this, name, threadType)
    }

    @Nonnull
    override fun createThreadChannel(
        @Nonnull name: String,
        messageId: Long,
    ): ThreadChannelAction {
        Checks.notNull(name, "Name")
        val name = name.trim()
        Checks.notEmpty(name, "Name")
        Checks.notLonger(name, Channel.MAX_NAME_LENGTH, "Name")

        checkAttached()
        Checks.checkAccess(guild.selfMember, this)
        checkPermission(Permission.CREATE_PUBLIC_THREADS)

        return ThreadChannelActionImpl(this, name, java.lang.Long.toUnsignedString(messageId))
    }

    @Nonnull
    override fun retrieveArchivedPublicThreadChannels(): ThreadChannelPaginationAction {
        checkAttached()
        Checks.checkAccess(guild.selfMember, this)
        checkPermission(Permission.MESSAGE_HISTORY)

        val route = Route.Channels.LIST_PUBLIC_ARCHIVED_THREADS.compile(id)
        return ThreadChannelPaginationActionImpl(jda, route, this, false)
    }

    @Nonnull
    override fun retrieveArchivedPrivateThreadChannels(): ThreadChannelPaginationAction {
        checkAttached()
        Checks.checkAccess(guild.selfMember, this)
        checkPermission(Permission.MESSAGE_HISTORY)
        checkPermission(Permission.MANAGE_THREADS)

        val route = Route.Channels.LIST_PRIVATE_ARCHIVED_THREADS.compile(id)
        return ThreadChannelPaginationActionImpl(jda, route, this, false)
    }

    @Nonnull
    override fun retrieveArchivedPrivateJoinedThreadChannels(): ThreadChannelPaginationAction {
        checkAttached()
        Checks.checkAccess(guild.selfMember, this)
        checkPermission(Permission.MESSAGE_HISTORY)

        val route = Route.Channels.LIST_JOINED_PRIVATE_ARCHIVED_THREADS.compile(id)
        return ThreadChannelPaginationActionImpl(jda, route, this, true)
    }

    fun setDefaultThreadSlowmode(slowmode: Int): T
}
