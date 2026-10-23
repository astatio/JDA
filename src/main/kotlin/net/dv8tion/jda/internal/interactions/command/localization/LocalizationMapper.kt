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

package net.dv8tion.jda.internal.interactions.command.localization

import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationMap
import net.dv8tion.jda.api.interactions.commands.localization.ResourceBundleLocalizationFunction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import java.util.ArrayDeque
import java.util.Deque
import java.util.Locale
import java.util.StringJoiner
import java.util.function.Consumer
import java.util.function.Function
import javax.annotation.Nonnull

/**
 * Utility class which maps user-provided translations (from a resource bundle for example) to the command data as well as everything contained in it.
 * This class essentially wraps a [LocalizationFunction] to ask the localization function for translations based on command definitions defined in code.
 * The real brain of this system lies in the localization function.
 * The localization function is where the developer can define *how* to get translations for various parts of commands.
 * The LocalizationMapper is effectively just the context organizer between command definitions and getting their translations.
 *
 * You can find a prebuilt localization function that uses [ResourceBundle][java.util.ResourceBundle] at [ResourceBundleLocalizationFunction].
 *
 * @see LocalizationFunction
 * @see ResourceBundleLocalizationFunction
 */
class LocalizationMapper private constructor(
    private val localizationFunction: LocalizationFunction,
) {
    fun localizeCommand(
        commandData: CommandData,
        optionArray: DataArray,
    ) {
        val ctx = TranslationContext()
        ctx.withKey(commandData.name) {
            ctx.trySetTranslation(commandData.nameLocalizations, "name")
            if (commandData.type == Command.Type.SLASH) {
                val slashCommandData = commandData as SlashCommandData
                ctx.trySetTranslation(slashCommandData.descriptionLocalizations, "description")
                localizeOptionArray(optionArray, ctx)
            }
        }
    }

    private fun localizeOptionArray(
        optionArray: DataArray,
        ctx: TranslationContext,
    ) {
        ctx.forObjects(optionArray, { it.getString("name") }) { obj ->
            if (obj.hasKey("name_localizations")) {
                ctx.trySetTranslation(obj.getObject("name_localizations"), "name")
            }
            if (obj.hasKey("description_localizations")) {
                ctx.trySetTranslation(obj.getObject("description_localizations"), "description")
            }
            if (obj.hasKey("options")) {
                localizeOptionArray(obj.getArray("options"), ctx)
            }
            if (obj.hasKey("choices")) {
                // Puts "choices" between the option name and the choice name
                // This makes it more distinguishable in tree structures
                ctx.withKey("choices") { localizeOptionArray(obj.getArray("choices"), ctx) }
            }
        }
    }

    private inner class TranslationContext {
        private val keyComponents: Deque<String> = ArrayDeque()

        fun forObjects(
            source: DataArray,
            keyExtractor: Function<DataObject, String>,
            consumer: Consumer<DataObject>,
        ) {
            for (i in 0 until source.length()) {
                val item = source.getObject(i)
                val runnable =
                    Runnable {
                        val key = keyExtractor.apply(item)
                        keyComponents.addLast(key)
                        consumer.accept(item)
                        keyComponents.removeLast()
                    }

                // We need to differentiate subcommands/groups from options
                // before inserting the "options" separator
                val type = OptionType.fromKey(item.getInt("type", -1)) // -1 when the object isn't an option
                val isOption =
                    type != OptionType.SUB_COMMAND &&
                        type != OptionType.SUB_COMMAND_GROUP &&
                        type != OptionType.UNKNOWN
                if (isOption) {
                    // At this point the key should look like "path.to.command",
                    // we can insert "options", and the keyExtractor would give option names

                    // Put "options" between the command name and the option name
                    // This makes it more distinguishable in tree structures
                    withKey("options", runnable)
                } else {
                    runnable.run()
                }
            }
        }

        fun withKey(
            key: String,
            runnable: Runnable,
        ) {
            keyComponents.addLast(key)
            runnable.run()
            keyComponents.removeLast()
        }

        private fun getKey(finalComponent: String): String {
            val joiner = StringJoiner(".")
            for (keyComponent in keyComponents) {
                joiner.add(keyComponent.replace(" ", "_")) // Context commands can have spaces, we need to replace them
            }
            joiner.add(finalComponent.replace(" ", "_"))
            return joiner.toString().lowercase(Locale.ROOT)
        }

        // RuntimeException is intentional: the Java original wraps any LocalizationFunction failure in it,
        // and narrowing the type would change the exception contract callers see.
        @Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
        fun trySetTranslation(
            localizationMap: LocalizationMap,
            finalComponent: String,
        ) {
            val key = getKey(finalComponent)
            try {
                val data = localizationFunction.apply(key)
                localizationMap.setTranslations(data)
            } catch (e: Exception) {
                throw RuntimeException(
                    "An uncaught exception occurred while using a LocalizationFunction, localization key: '$key'",
                    e,
                )
            }
        }

        // RuntimeException is intentional: the Java original wraps any LocalizationFunction failure in it,
        // and narrowing the type would change the exception contract callers see.
        @Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
        fun trySetTranslation(
            localizationMap: DataObject,
            finalComponent: String,
        ) {
            val key = getKey(finalComponent)
            try {
                val data = localizationFunction.apply(key)
                data.forEach { (locale, localizedValue) ->
                    Checks.check(
                        locale != DiscordLocale.UNKNOWN,
                        "Localization function returned a map with an 'UNKNOWN' DiscordLocale",
                    )

                    localizationMap.put(locale.getLocale(), localizedValue)
                }
            } catch (e: Exception) {
                throw RuntimeException(
                    "An uncaught exception occurred while using a LocalizationFunction, localization key: '$key'",
                    e,
                )
            }
        }
    }

    companion object {
        /**
         * Creates a new [LocalizationMapper] from the given [LocalizationFunction]
         *
         * @param localizationFunction
         *         The [LocalizationFunction] to use
         *
         * @return The [LocalizationMapper] instance
         *
         * @see ResourceBundleLocalizationFunction
         */
        @Nonnull
        @JvmStatic
        fun fromFunction(
            @Nonnull localizationFunction: LocalizationFunction,
        ): LocalizationMapper = LocalizationMapper(localizationFunction)
    }
}
