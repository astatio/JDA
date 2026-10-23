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

package net.dv8tion.jda.internal.utils.localization

import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationMap
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.interactions.command.localization.UnmodifiableLocalizationMap
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.JDALogger
import org.slf4j.Logger
import java.util.Collections
import javax.annotation.Nonnull

object LocalizationUtils {
    @JvmField
    val LOG: Logger = JDALogger.getLog(LocalizationUtils::class.java)

    @JvmStatic
    @Nonnull
    fun mapFromData(
        @Nonnull data: DataObject,
    ): Map<DiscordLocale, String> {
        Checks.notNull(data, "Data")

        val map = HashMap<DiscordLocale, String>()

        for (key in data.keys()) {
            val locale = DiscordLocale.from(key)
            if (locale == DiscordLocale.UNKNOWN) {
                LOG.debug("Discord provided an unknown locale, locale tag: {}", key)
                continue
            }

            map[locale] = data.getString(key)
        }

        return map
    }

    @JvmStatic
    @Nonnull
    fun mapFromProperty(
        @Nonnull json: DataObject,
        @Nonnull localizationProperty: String,
    ): Map<DiscordLocale, String> =
        json
            .optObject(localizationProperty)
            .map(LocalizationUtils::mapFromData)
            .orElse(Collections.emptyMap())

    @JvmStatic
    @Nonnull
    fun unmodifiableFromProperty(
        @Nonnull json: DataObject,
        @Nonnull localizationProperty: String,
    ): LocalizationMap = UnmodifiableLocalizationMap(mapFromProperty(json, localizationProperty))
}
