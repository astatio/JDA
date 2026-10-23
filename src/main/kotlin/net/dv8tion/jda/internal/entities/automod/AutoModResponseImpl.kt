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

package net.dv8tion.jda.internal.entities.automod

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.automod.AutoModResponse
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.utils.data.DataObject
import java.time.Duration
import javax.annotation.Nonnull
import javax.annotation.Nullable

class AutoModResponseImpl private constructor(
    private val type: AutoModResponse.Type,
    private val channel: GuildMessageChannel?,
    private val customMessage: String?,
    private val timeoutDuration: Long,
) : AutoModResponse {
    constructor(type: AutoModResponse.Type) : this(type, null, null, 0)

    constructor(type: AutoModResponse.Type, channel: GuildMessageChannel) : this(type, channel, null, 0)

    constructor(type: AutoModResponse.Type, customMessage: String) : this(type, null, customMessage, 0)

    constructor(type: AutoModResponse.Type, duration: Duration) : this(type, null, null, duration.seconds)

    constructor(guild: Guild, json: DataObject) : this(
        AutoModResponse.Type.fromKey(json.getInt("type", -1)),
        guild.getChannelById(
            GuildMessageChannel::class.java,
            json.optObject("metadata").orElseGet { DataObject.empty() }.getUnsignedLong("channel_id", 0L),
        ),
        json.optObject("metadata").orElseGet { DataObject.empty() }.getString("custom_message", null),
        json.optObject("metadata").orElseGet { DataObject.empty() }.getUnsignedLong("duration_seconds", 0L),
    )

    @Nonnull
    override fun getType(): AutoModResponse.Type = type

    @Nullable
    override fun getChannel(): GuildMessageChannel? = channel

    @Nullable
    override fun getCustomMessage(): String? = customMessage

    @Nullable
    override fun getTimeoutDuration(): Duration? = if (timeoutDuration == 0L) null else Duration.ofSeconds(timeoutDuration)

    @Nonnull
    override fun toData(): DataObject {
        val action = DataObject.empty()
        action.put("type", type.key)
        if (type == AutoModResponse.Type.BLOCK_MESSAGE && customMessage == null) {
            return action
        }

        val metadata = DataObject.empty()
        if (customMessage != null) {
            metadata.put("custom_message", customMessage)
        }
        if (channel != null) {
            metadata.put("channel_id", channel.id)
        }
        if (timeoutDuration > 0) {
            metadata.put("duration_seconds", timeoutDuration)
        }
        action.put("metadata", metadata)
        return action
    }

    override fun hashCode(): Int = type.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is AutoModResponseImpl) {
            return false
        }
        return type == other.type
    }
}
