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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.StageInstance
import net.dv8tion.jda.api.entities.StageInstance.PrivacyLevel
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.StageInstanceManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.internal.managers.StageInstanceManagerImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.EntityString
import java.util.EnumSet
import javax.annotation.Nonnull

class StageInstanceImpl(
    private val id: Long,
    channel: StageChannel,
) : StageInstance {
    private var channel: StageChannel = channel

    private lateinit var topic: String
    private lateinit var privacyLevel: PrivacyLevel

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getGuild(): Guild = getChannel().guild

    @Nonnull
    override fun getChannel(): StageChannel {
        val real = channel.jda.getStageChannelById(channel.idLong)
        if (real != null) {
            channel = real
        }
        return channel
    }

    @Nonnull
    override fun getTopic(): String = topic

    @Nonnull
    override fun getPrivacyLevel(): PrivacyLevel = privacyLevel

    @Nonnull
    override fun delete(): RestAction<Void> {
        checkPermissions()
        val route = Route.StageInstances.DELETE_INSTANCE.compile(channel.id)
        return RestActionImpl(channel.jda, route)
    }

    @Nonnull
    override fun getManager(): StageInstanceManager {
        checkPermissions()
        return StageInstanceManagerImpl(this)
    }

    fun setTopic(topic: String): StageInstanceImpl {
        this.topic = topic
        return this
    }

    fun setPrivacyLevel(privacyLevel: PrivacyLevel): StageInstanceImpl {
        this.privacyLevel = privacyLevel
        return this
    }

    override fun toString(): String = EntityString(this).addMetadata("channel", getChannel()).toString()

    private fun checkPermissions() {
        val permissions = getGuild().selfMember.getPermissions(getChannel())
        val required =
            EnumSet.of(Permission.MANAGE_CHANNEL, Permission.VOICE_MUTE_OTHERS, Permission.VOICE_MOVE_OTHERS)
        for (perm in required) {
            if (!permissions.contains(perm)) {
                throw InsufficientPermissionException(
                    getChannel(),
                    perm,
                    "You must be a stage moderator to manage a stage instance! Missing Permission: $perm",
                )
            }
        }
    }
}
