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

package net.dv8tion.jda.internal.entities.channel.middleman

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.StandardGuildMessageChannelMixin
import javax.annotation.Nullable

abstract class AbstractStandardGuildMessageChannelImpl<T : AbstractStandardGuildMessageChannelImpl<T>>(
    id: Long,
    guild: Guild,
) : AbstractStandardGuildChannelImpl<T>(id, guild),
    StandardGuildMessageChannelMixin<T> {
    @JvmField
    protected var topic: String? = null

    @JvmField
    protected var nsfw: Boolean = false

    @JvmField
    protected var latestMessageId: Long = 0

    @JvmField
    protected var defaultThreadSlowmode: Int = 0

    @Nullable
    override fun getTopic(): String? = topic

    override fun isNSFW(): Boolean = nsfw

    override fun getLatestMessageIdLong(): Long = latestMessageId

    override fun getDefaultThreadSlowmode(): Int = defaultThreadSlowmode

    @Suppress("UNCHECKED_CAST")
    override fun setTopic(topic: String?): T {
        this.topic = topic
        return this as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun setNSFW(ageRestricted: Boolean): T {
        this.nsfw = ageRestricted
        return this as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun setLatestMessageIdLong(latestMessageId: Long): T {
        this.latestMessageId = latestMessageId
        return this as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun setDefaultThreadSlowmode(slowmode: Int): T {
        this.defaultThreadSlowmode = slowmode
        return this as T
    }
}
