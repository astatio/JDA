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

package net.dv8tion.jda.internal.entities.detached.mixin

import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.detached.IDetachableEntity
import net.dv8tion.jda.api.exceptions.DetachedEntityException
import net.dv8tion.jda.api.exceptions.MissingAccessException
import net.dv8tion.jda.api.exceptions.ObfuscatedChannelException
import javax.annotation.Nonnull

interface IDetachableEntityMixin : IDetachableEntity {
    fun checkAttached() {
        if (isDetached) {
            throw detachedException()
        }
    }

    @Suppress("ReturnCount") // faithfully ported early-return guard chain
    fun isDetachedBecauseCachedChannelIsObfuscated(): Boolean {
        if (!isDetached || this !is GuildChannel) {
            return false
        }

        val gc: GuildChannel = this
        if (gc.guild.isDetached) {
            return false
        }

        val cachedChannel = gc.guild.getGuildChannelById(gc.type, gc.idLong)
        return cachedChannel != null && cachedChannel.isObfuscated
    }

    @Nonnull
    fun obfuscatedAccessException(): MissingAccessException = ObfuscatedChannelException(this as GuildChannel)

    @Nonnull
    fun detachedException(): RuntimeException =
        if (isDetachedBecauseCachedChannelIsObfuscated()) {
            obfuscatedAccessException()
        } else {
            DetachedEntityException()
        }

    @Nonnull
    fun detachedRequiresChannelException(): DetachedEntityException =
        DetachedEntityException("Getting/checking permissions requires a GuildChannel")
}
