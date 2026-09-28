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

package net.dv8tion.jda.internal.audio

import net.dv8tion.jda.api.utils.data.DataArray
import java.util.EnumSet
import java.util.Locale
import java.util.Objects
import java.util.stream.Collectors

// ordered by priority descending
enum class AudioEncryption {
    AEAD_AES256_GCM_RTPSIZE,
    AEAD_XCHACHA20_POLY1305_RTPSIZE,
    ;

    val key: String = name.lowercase(Locale.ROOT)

    companion object {
        @JvmStatic
        fun getPreferredMode(array: DataArray): AudioEncryption? {
            var encryption: AudioEncryption? = null
            for (o in array) {
                try {
                    val name = "$o".uppercase(Locale.ROOT)
                    val e = valueOf(name)
                    if (encryption == null || e.ordinal < encryption.ordinal) {
                        encryption = e
                    }
                } catch (ignored: IllegalArgumentException) {
                    // Unknown mode names are skipped, matching the Java original.
                }
            }
            return encryption
        }

        @JvmStatic
        fun fromArray(modes: DataArray): EnumSet<AudioEncryption> =
            modes
                .stream { a, i -> a.getString(i) }
                .map { mode -> mode.lowercase(Locale.ROOT) }
                .map { mode -> forMode(mode) }
                .filter { mode -> Objects.nonNull(mode) }
                .collect(Collectors.toCollection { EnumSet.noneOf(AudioEncryption::class.java) })

        @JvmStatic
        fun forMode(mode: String): AudioEncryption? =
            when (mode) {
                "aead_aes256_gcm_rtpsize" -> AEAD_AES256_GCM_RTPSIZE
                "aead_xchacha20_poly1305_rtpsize" -> AEAD_XCHACHA20_POLY1305_RTPSIZE
                else -> null
            }
    }
}
