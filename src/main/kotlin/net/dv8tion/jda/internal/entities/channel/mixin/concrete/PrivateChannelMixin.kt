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

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.MessageChannelMixin
import net.dv8tion.jda.internal.requests.CompletedRestAction
import net.dv8tion.jda.internal.requests.RestActionImpl
import javax.annotation.Nonnull

interface PrivateChannelMixin<T : PrivateChannelMixin<T>> :
    PrivateChannel,
    MessageChannelMixin<T> {
    @Nonnull
    override fun getName(): String {
        val user = user
        if (user == null) {
            // don't break or override the contract of @NonNull
            return ""
        }
        return user.name
    }

    @Nonnull
    override fun retrieveUser(): RestAction<User> {
        val user = user
        if (user != null) {
            return CompletedRestAction<User>(jda, user)
        }
        // even if the user blocks the bot, this does not fail.
        return retrievePrivateChannel().map { it.user }
    }

    @Nonnull
    fun retrievePrivateChannel(): RestAction<PrivateChannel> {
        val route = Route.Channels.GET_CHANNEL.compile(id)
        return RestActionImpl(jda, route) { response, _ ->
            (jda as JDAImpl).entityBuilder.createPrivateChannel(response.getObject())
        }
    }
}
