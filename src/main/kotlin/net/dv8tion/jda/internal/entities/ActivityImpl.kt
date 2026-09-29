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

import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Activity.ActivityType
import net.dv8tion.jda.api.entities.Activity.Timestamps
import net.dv8tion.jda.api.entities.RichPresence
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

open class ActivityImpl protected constructor(
    @JvmField protected val name: String,
    @JvmField protected val state: String?,
    @JvmField protected val url: String?,
    @JvmField protected val type: ActivityType,
    @JvmField protected val timestamps: Timestamps?,
    @JvmField protected val emoji: EmojiUnion?,
) : Activity {
    protected constructor(name: String) : this(name, null, null, ActivityType.PLAYING, null, null)

    protected constructor(name: String, url: String?) : this(name, null, url, ActivityType.STREAMING, null, null)

    protected constructor(name: String, url: String?, type: ActivityType) : this(name, null, url, type, null, null)

    protected constructor(name: String, state: String?, url: String?, type: ActivityType) :
        this(name, state, url, type, null, null)

    override fun isRich(): Boolean = false

    override fun asRichPresence(): RichPresence? = null

    @Nonnull
    override fun getName(): String = name

    @Nullable
    override fun getState(): String? = state

    @Nullable
    override fun getUrl(): String? = url

    @Nonnull
    override fun getType(): ActivityType = type

    @Nullable
    override fun getTimestamps(): Timestamps? = timestamps

    @Nullable
    override fun getEmoji(): EmojiUnion? = emoji

    @Nonnull
    override fun withState(
        @Nullable state: String?,
    ): Activity {
        var newState = state
        if (newState != null) {
            newState = newState.trim()
            if (newState.isEmpty()) {
                newState = null
            } else {
                Checks.notLonger(newState, Activity.MAX_ACTIVITY_STATE_LENGTH, "State")
            }
        }

        return ActivityImpl(name, newState, url, type)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ActivityImpl) {
            return false
        }

        return other.type == type &&
            Objects.equals(name, other.getName()) &&
            Objects.equals(state, other.state) &&
            Objects.equals(url, other.getUrl()) &&
            Objects.equals(timestamps, other.timestamps)
    }

    override fun hashCode(): Int = Objects.hash(name, state, type, url, timestamps)

    override fun toString(): String {
        val entityString = EntityString(this).setType(type).setName(name)
        if (url != null) {
            entityString.addMetadata("url", url)
        }

        return entityString.toString()
    }
}
