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

package net.dv8tion.jda.internal.managers

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.managers.DirectAudioController
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.utils.Checks
import javax.annotation.Nonnull

class DirectAudioControllerImpl(
    private val api: JDAImpl,
) : DirectAudioController {
    @Nonnull
    override fun getJDA(): JDAImpl = api

    override fun connect(
        @Nonnull channel: AudioChannel,
    ) {
        Checks.notNull(channel, "Audio Channel")
        val jda = getJDA()
        val client = jda.getClient()
        client.queueAudioConnect(channel)
    }

    override fun disconnect(
        @Nonnull guild: Guild,
    ) {
        Checks.notNull(guild, "Guild")
        val jda = getJDA()
        val client = jda.getClient()
        client.queueAudioDisconnect(guild)
    }

    override fun reconnect(
        @Nonnull channel: AudioChannel,
    ) {
        Checks.notNull(channel, "Audio Channel")
        val jda = getJDA()
        val client = jda.getClient()
        client.queueAudioReconnect(channel)
    }

    /**
     * Used to update the internal state of the voice request. When a connection
     * was successfully established JDA will stop sending requests for the initial connect.
     * <br>This is done to retry the voice updates in case of a partial service failure.
     *
     * <p>Should be called when:
     * <ol>
     *     <li>Receiving a Voice State Update for the current account and we were previously connected (moved or disconnected)</li>
     *     <li>Receiving a Voice Server Update (initial connect or region change)</li>
     * </ol>
     *
     * Note that the voice state update will always be received prior to a voice server update.
     * <br>The internal dispatch handlers already call this when needed, a library end-user never needs to call this method.
     *
     * @param guild
     *        The guild to update the state for
     * @param channel
     *        The new channel, or null to signal disconnect
     */
    fun update(
        guild: Guild,
        channel: AudioChannel?,
    ) {
        Checks.notNull(guild, "Guild")
        val jda = getJDA()
        val client = jda.getClient()
        client.updateAudioConnection(guild.getIdLong(), channel)
    }
}
