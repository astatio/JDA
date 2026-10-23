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

package net.dv8tion.jda.internal.entities.channel.mixin.middleman

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import net.dv8tion.jda.api.exceptions.MissingAccessException

interface AudioChannelMixin<T : AudioChannelMixin<T>> :
    AudioChannelUnion,
    StandardGuildChannelMixin<T> {
    // ---- State Accessors ----

    fun setBitrate(bitrate: Int): T

    fun setUserLimit(userlimit: Int): T

    fun setRegion(region: String): T

    // AudioChannels also require connect permission to grant access
    override fun checkCanAccess() {
        checkAttached()
        if (!hasPermission(Permission.VIEW_CHANNEL)) {
            throw MissingAccessException(this, Permission.VIEW_CHANNEL)
        }
        if (!hasPermission(Permission.VOICE_CONNECT)) {
            throw MissingAccessException(this, Permission.VOICE_CONNECT)
        }
    }
}
