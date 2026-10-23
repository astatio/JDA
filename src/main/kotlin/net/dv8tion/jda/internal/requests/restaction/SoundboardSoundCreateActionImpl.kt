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

package net.dv8tion.jda.internal.requests.restaction

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.SoundboardSound
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.SoundboardSoundCreateAction
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.IOUtil
import okhttp3.RequestBody
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val MIN_VOLUME = 0.0
private const val MAX_VOLUME = 1.0
private const val DEFAULT_VOLUME = 1.0

class SoundboardSoundCreateActionImpl(
    api: JDA,
    route: Route.CompiledRoute,
    private val name: String,
    private val file: FileUpload,
) : AuditableRestActionImpl<SoundboardSound>(api, route),
    SoundboardSoundCreateAction {
    private var volume = DEFAULT_VOLUME
    private var emoji: Emoji? = null

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): SoundboardSoundCreateAction = super.timeout(timeout, unit) as SoundboardSoundCreateAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun addCheck(
        @Nonnull checks: BooleanSupplier,
    ): SoundboardSoundCreateAction = super.addCheck(checks) as SoundboardSoundCreateAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): SoundboardSoundCreateAction = super.setCheck(checks) as SoundboardSoundCreateAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): SoundboardSoundCreateAction = super.deadline(timestamp) as SoundboardSoundCreateAction

    @Nonnull
    override fun setVolume(volume: Double): SoundboardSoundCreateAction {
        Checks.check(volume >= MIN_VOLUME && volume <= MAX_VOLUME, "Volume must be between 0 and 1")
        this.volume = volume
        return this
    }

    @Nonnull
    override fun setEmoji(
        @Nullable emoji: Emoji?,
    ): SoundboardSoundCreateAction {
        this.emoji = emoji
        return this
    }

    override fun finalizeData(): RequestBody? {
        try {
            val json =
                DataObject
                    .empty()
                    .put("name", name)
                    .put("sound", "data:" + getMime() + ";base64," + getBase64Sound())
                    .put("volume", volume)

            if (emoji is UnicodeEmoji) {
                json.put("emoji_name", (emoji as UnicodeEmoji).name)
            } else if (emoji is CustomEmoji) {
                json.put("emoji_id", (emoji as CustomEmoji).id)
            }

            return getRequestBody(json)
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to get request body when creating a guild soundboard sound", e)
        }
    }

    @Nonnull
    @Throws(IOException::class)
    private fun getBase64Sound(): String {
        val data = IOUtil.readFully(file.data)
        val b64 = Base64.getEncoder().encode(data)
        return String(b64, StandardCharsets.UTF_8)
    }

    @Nonnull
    private fun getMime(): String {
        val index = file.name.lastIndexOf('.')
        Checks.check(
            index > -1,
            "Filename for soundboard sound is missing file extension. Provided: '" + file.name +
                "'. Must be MP3 or OGG.",
        )

        val extension = file.name.substring(index + 1).lowercase(Locale.ROOT)
        val mime =
            when (extension) {
                "mp3" -> "audio/mpeg"
                "ogg" -> "audio/ogg"
                else -> throw IllegalArgumentException(
                    "Unsupported file extension: '." + extension + "', must be MP3 or OGG.",
                )
            }
        return mime
    }

    override fun handleSuccess(
        response: Response,
        request: Request<SoundboardSound>,
    ) {
        request.onSuccess(api.entityBuilder.createSoundboardSound(response.getObject()))
    }
}
