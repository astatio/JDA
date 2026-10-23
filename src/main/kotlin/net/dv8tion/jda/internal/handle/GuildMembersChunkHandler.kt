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
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.MemberImpl
import net.dv8tion.jda.internal.requests.WebSocketClient
import net.dv8tion.jda.internal.utils.Helpers
import java.util.function.ToLongFunction

class GuildMembersChunkHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getLong("guild_id")
        val members = content.getArray("members")
        val guild = getJDA().getGuildById(guildId) as GuildImpl?
        if (guild != null) {
            if (api.getClient().getChunkManager().handleChunk(guildId, content)) {
                return null
            }
            WebSocketClient.LOG.debug(
                "Received member chunk for guild that is already in cache. GuildId: {} Count: {} Index: {}/{}",
                guildId,
                members.length(),
                content.getInt("chunk_index"),
                content.getInt("chunk_count"),
            )
            // Chunk handling
            val builder: EntityBuilder = getJDA().getEntityBuilder()
            val presences: TLongObjectMap<DataObject> =
                content
                    .optArray("presences")
                    .map { array: DataArray ->
                        Helpers.convertToMap(
                            ToLongFunction { o: DataObject -> o.getObject("user").getUnsignedLong("id") },
                            array,
                        )
                    }.orElseGet { TLongObjectHashMap<DataObject>() }
            for (i in 0 until members.length()) {
                val `object` = members.getObject(i)
                val userId = `object`.getObject("user").getUnsignedLong("id")
                val presence = presences.get(userId)
                val member: MemberImpl = builder.createMember(guild, `object`, null, presence)
                builder.updateMemberCache(member)
            }
            return null
        }
        getJDA().getGuildSetupController().onMemberChunk(guildId, content)
        return null
    }
}
