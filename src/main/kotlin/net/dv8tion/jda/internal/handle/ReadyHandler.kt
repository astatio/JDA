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

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.requests.WebSocketClient

class ReadyHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    protected override fun handleInternally(content: DataObject): Long? {
        val builder = getJDA().getEntityBuilder()

        val guilds: DataArray = content.getArray("guilds")
        // Make sure we don't have any duplicates here!
        val distinctGuilds: TLongObjectMap<DataObject> = TLongObjectHashMap()
        for (i in 0 until guilds.length()) {
            val guild = guilds.getObject(i)
            val id = guild.getUnsignedLong("id")
            val previous = distinctGuilds.put(id, guild)
            if (previous != null) {
                WebSocketClient.LOG.warn("Found duplicate guild for id {} in ready payload", id)
            }
        }

        val selfJson = content.getObject("user")
        // Inject the application id which isn't added to the self user by default
        selfJson.put(
            "application_id", // Used to update SelfUser#getApplicationId
            content
                .optObject("application")
                .map { obj: DataObject -> obj.getUnsignedLong("id") }
                .orElse(selfJson.getUnsignedLong("id")),
        )
        // SelfUser is already created in login(...) but this just updates it to the current state
        // from the api, and injects the application id
        builder.createSelfUser(selfJson)

        if (getJDA().getGuildSetupController().setIncompleteCount(distinctGuilds.size())) {
            distinctGuilds.forEachEntry { id, guild ->
                getJDA().getGuildSetupController().onReady(id, guild)
                true
            }
        }

        handleReady(content)
        return null
    }

    fun handleReady(content: DataObject) {
        val builder: EntityBuilder = getJDA().getEntityBuilder()
        val privateChannels: DataArray = content.getArray("private_channels")

        for (i in 0 until privateChannels.length()) {
            val chan = privateChannels.getObject(i)
            val type = ChannelType.fromId(chan.getInt("type"))

            when (type) {
                ChannelType.PRIVATE -> builder.createPrivateChannel(chan)
                else ->
                    WebSocketClient.LOG.warn(
                        "Received a Channel in the private_channels array in READY of an unknown type! Type: {}",
                        type,
                    )
            }
        }
    }
}
