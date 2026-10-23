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

import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.managers.ApplicationEmojiManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

class ApplicationEmojiManagerImpl(
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField protected val emoji: ApplicationEmoji,
) : ManagerBase<ApplicationEmojiManager>(
        emoji.getJDA(),
        Route.Applications.MODIFY_APPLICATION_EMOJI.compile(emoji.getJDA().getSelfUser().getApplicationId(), emoji.getId()),
    ),
    ApplicationEmojiManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var name: String? = null

    @Nonnull
    override fun getEmoji(): ApplicationEmoji = emoji

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): ApplicationEmojiManagerImpl {
        super.reset(fields)
        if (fields and ApplicationEmojiManager.NAME == ApplicationEmojiManager.NAME) {
            this.name = null
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): ApplicationEmojiManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    override fun setName(
        @Nonnull name: String,
    ): ApplicationEmojiManager {
        Checks.inRange(name, 2, CustomEmoji.EMOJI_NAME_MAX_LENGTH, "Emoji name")
        Checks.matches(name, Checks.ALPHANUMERIC_WITH_DASH, "Emoji name")
        this.name = name
        set = set or ApplicationEmojiManager.NAME
        return this
    }

    override fun finalizeData(): RequestBody {
        val json = DataObject.empty()
        if (shouldUpdate(ApplicationEmojiManager.NAME)) {
            json.put("name", name)
        }
        reset()
        return getRequestBody(json)
    }
}
