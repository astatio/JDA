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

import net.dv8tion.jda.api.entities.Activity.ActivityType
import net.dv8tion.jda.api.entities.Activity.Timestamps
import net.dv8tion.jda.api.entities.ActivityFlag
import net.dv8tion.jda.api.entities.RichPresence
import net.dv8tion.jda.api.entities.RichPresence.Image
import net.dv8tion.jda.api.entities.RichPresence.Party
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.internal.utils.EntityString
import java.util.EnumSet
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

open class RichPresenceImpl protected constructor(
    type: ActivityType,
    name: String,
    url: String?,
    @JvmField protected val applicationId: Long,
    emoji: EmojiUnion?,
    @JvmField protected val party: Party?,
    @JvmField protected val details: String?,
    state: String?,
    timestamps: Timestamps?,
    @JvmField protected val syncId: String?,
    @JvmField protected val sessionId: String?,
    @JvmField protected val flags: Int,
    largeImageKey: String?,
    largeImageText: String?,
    smallImageKey: String?,
    smallImageText: String?,
) : ActivityImpl(name, state, url, type, timestamps, emoji),
    RichPresence {
    @JvmField protected val largeImage: Image? =
        if (largeImageKey != null) Image(applicationId, largeImageKey, largeImageText) else null

    @JvmField protected val smallImage: Image? =
        if (smallImageKey != null) Image(applicationId, smallImageKey, smallImageText) else null

    override fun isRich(): Boolean = true

    override fun asRichPresence(): RichPresence = this

    override fun getApplicationIdLong(): Long = applicationId

    @Nonnull
    override fun getApplicationId(): String = java.lang.Long.toUnsignedString(applicationId)

    @Nullable
    override fun getSessionId(): String? = sessionId

    @Nullable
    override fun getSyncId(): String? = syncId

    override fun getFlags(): Int = flags

    @Nonnull
    override fun getFlagSet(): EnumSet<ActivityFlag> = ActivityFlag.getFlags(flags)

    @Nullable
    override fun getDetails(): String? = details

    @Nullable
    override fun getParty(): Party? = party

    @Nullable
    override fun getLargeImage(): Image? = largeImage

    @Nullable
    override fun getSmallImage(): Image? = smallImage

    override fun toString(): String = EntityString(this).setName(name).addMetadata("applicationId", applicationId).toString()

    override fun hashCode(): Int =
        Objects.hash(applicationId, state, details, party, sessionId, syncId, flags, timestamps, largeImage, smallImage)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is RichPresenceImpl) {
            return false
        }
        return applicationId == other.applicationId &&
            Objects.equals(name, other.name) &&
            Objects.equals(url, other.url) &&
            Objects.equals(type, other.type) &&
            Objects.equals(state, other.state) &&
            Objects.equals(details, other.details) &&
            Objects.equals(party, other.party) &&
            Objects.equals(sessionId, other.sessionId) &&
            Objects.equals(syncId, other.syncId) &&
            flags == other.flags &&
            Objects.equals(timestamps, other.timestamps) &&
            Objects.equals(largeImage, other.largeImage) &&
            Objects.equals(smallImage, other.smallImage)
    }
}
