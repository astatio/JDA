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

package net.dv8tion.jda.internal.interactions

import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationMap
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.data.SerializableData
import net.dv8tion.jda.internal.interactions.command.localization.LocalizationMapper
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Collections
import java.util.EnumSet
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.annotation.Nonnull
import javax.annotation.Nullable

open class CommandDataImpl : SlashCommandData {
    @JvmField
    protected val options: MutableList<SerializableData> = ArrayList(CommandData.MAX_OPTIONS)

    @JvmField
    protected var name: String = ""

    @JvmField
    protected var description: String = ""

    private var localizationMapper: LocalizationMapper? = null
    private val nameLocalizations = LocalizationMap(Consumer { name -> checkName(name) })
    private val descriptionLocalizations = LocalizationMap(Consumer { description -> checkDescription(description) })

    private var allowSubcommands = true
    private var allowOption = true
    private var allowRequired = true
    private var contexts: EnumSet<InteractionContextType> =
        EnumSet.of(InteractionContextType.GUILD, InteractionContextType.BOT_DM)
    private var integrationTypes: EnumSet<IntegrationType> = EnumSet.of(IntegrationType.GUILD_INSTALL)
    private var nsfw = false
    private var defaultMemberPermissions: DefaultMemberPermissions = DefaultMemberPermissions.ENABLED

    private val type: Command.Type

    constructor(
        @Nonnull name: String,
        @Nonnull description: String,
    ) {
        type = Command.Type.SLASH
        setName(name)
        setDescription(description)
    }

    constructor(
        @Nonnull type: Command.Type,
        @Nonnull name: String,
    ) {
        this.type = type
        Checks.notNull(type, "Command Type")
        Checks.check(
            type != Command.Type.SLASH,
            "Cannot create slash command without description. Use `new CommandDataImpl(name, description)` instead.",
        )
        setName(name)
    }

    protected fun checkType(
        required: Command.Type,
        action: String,
    ) {
        if (required != type) {
            throw IllegalStateException("Cannot $action for commands of type $type")
        }
    }

    fun checkName(
        @Nonnull name: String,
    ) {
        Checks.inRange(name, 1, CommandData.MAX_NAME_LENGTH, "Name")
        if (type == Command.Type.SLASH) {
            Checks.matches(name, Checks.ALPHANUMERIC_WITH_DASH, "Name")
            Checks.isLowercase(name, "Name")
        }
    }

    fun checkDescription(
        @Nonnull description: String,
    ) {
        checkType(Command.Type.SLASH, "set description")
        Checks.inRange(description, 1, CommandData.MAX_DESCRIPTION_LENGTH, "Description")
    }

    @Nonnull
    override fun toData(): DataObject {
        val options = DataArray.fromCollection(this.options)

        localizationMapper?.localizeCommand(this, options)

        val json =
            DataObject
                .empty()
                .put("type", type.getId())
                .put("name", name)
                .put("nsfw", nsfw)
                .put("options", options)
                .put("contexts", contexts.stream().map { it.type }.collect(Collectors.toList()))
                .put("integration_types", integrationTypes.stream().map { it.type }.collect(Collectors.toList()))
                .put(
                    "default_member_permissions",
                    if (DefaultMemberPermissions.ENABLED == defaultMemberPermissions) {
                        null
                    } else {
                        java.lang.Long.toUnsignedString(defaultMemberPermissions.permissionsRaw!!)
                    },
                ).put("name_localizations", nameLocalizations)

        if (type == Command.Type.SLASH) {
            json.put("description", description).put("description_localizations", descriptionLocalizations)
        }
        return json
    }

    @Nonnull
    override fun getType(): Command.Type = type

    @Nonnull
    override fun getDefaultPermissions(): DefaultMemberPermissions = defaultMemberPermissions

    @Nonnull
    override fun getContexts(): Set<InteractionContextType> = Collections.unmodifiableSet(contexts)

    @Nonnull
    override fun getIntegrationTypes(): Set<IntegrationType> = integrationTypes

    override fun isNSFW(): Boolean = nsfw

    @Nonnull
    override fun getOptions(): List<OptionData> =
        options
            .stream()
            .filter { it is OptionData }
            .map { it as OptionData }
            .collect(Helpers.toUnmodifiableList())

    @Nonnull
    override fun getSubcommands(): List<SubcommandData> =
        options
            .stream()
            .filter { it is SubcommandData }
            .map { it as SubcommandData }
            .collect(Helpers.toUnmodifiableList())

    @Nonnull
    override fun getSubcommandGroups(): List<SubcommandGroupData> =
        options
            .stream()
            .filter { it is SubcommandGroupData }
            .map { it as SubcommandGroupData }
            .collect(Helpers.toUnmodifiableList())

    @Nonnull
    override fun setDefaultPermissions(
        @Nonnull permissions: DefaultMemberPermissions,
    ): CommandDataImpl {
        Checks.notNull(permissions, "Permissions")
        defaultMemberPermissions = permissions
        return this
    }

    @Nonnull
    override fun setContexts(
        @Nonnull contexts: Collection<InteractionContextType>,
    ): CommandDataImpl {
        Checks.notEmpty(contexts, "Contexts")
        this.contexts = Helpers.copyEnumSet(InteractionContextType::class.java, contexts)
        return this
    }

    @Nonnull
    override fun setIntegrationTypes(
        @Nonnull integrationTypes: Collection<IntegrationType>,
    ): CommandDataImpl {
        Checks.notEmpty(contexts, "Contexts")
        this.integrationTypes = Helpers.copyEnumSet(IntegrationType::class.java, integrationTypes)
        return this
    }

    @Nonnull
    override fun setNSFW(nsfw: Boolean): CommandDataImpl {
        this.nsfw = nsfw
        return this
    }

    @Nonnull
    override fun addOptions(
        @Nonnull vararg options: OptionData,
    ): CommandDataImpl {
        Checks.noneNull(options, "Option")
        if (options.isEmpty()) {
            return this
        }
        checkType(Command.Type.SLASH, "add options")
        Checks.check(
            options.size + this.options.size <= CommandData.MAX_OPTIONS,
            "Cannot have more than %d options for a command!",
            CommandData.MAX_OPTIONS,
        )
        Checks.check(allowOption, "You cannot mix options with subcommands/groups.")
        var allowRequired = this.allowRequired
        for (option in options) {
            Checks.check(
                option.type != OptionType.SUB_COMMAND,
                "Cannot add a subcommand with addOptions(...). Use addSubcommands(...) instead!",
            )
            Checks.check(
                option.type != OptionType.SUB_COMMAND_GROUP,
                "Cannot add a subcommand group with addOptions(...). Use addSubcommandGroups(...) instead!",
            )
            Checks.check(
                allowRequired || !option.isRequired,
                "Cannot add required options after non-required options!",
            )
            // prevent adding required options after non-required options
            allowRequired = option.isRequired
        }

        Checks.checkUnique(
            Stream.concat(getOptions().stream(), options.asList().stream()).map { it.name },
            "Cannot have multiple options with the same name. Name: \"%s\" appeared %d times!",
            BiFunction { count, value -> arrayOf(value, count) },
        )

        allowSubcommands = false
        this.allowRequired = allowRequired
        this.options.addAll(options)
        return this
    }

    @Nonnull
    override fun addSubcommands(
        @Nonnull vararg subcommands: SubcommandData,
    ): CommandDataImpl {
        Checks.noneNull(subcommands, "Subcommands")
        if (subcommands.isEmpty()) {
            return this
        }
        checkType(Command.Type.SLASH, "add subcommands")
        if (!allowSubcommands) {
            throw IllegalArgumentException("You cannot mix options with subcommands/groups.")
        }
        Checks.check(
            subcommands.size + this.options.size <= CommandData.MAX_OPTIONS,
            "Cannot have more than %d subcommands for a command!",
            CommandData.MAX_OPTIONS,
        )
        Checks.checkUnique(
            Stream.concat(getSubcommands().stream(), subcommands.asList().stream()).map { it.name },
            "Cannot have multiple subcommands with the same name. Name: \"%s\" appeared %d times!",
            BiFunction { count, value -> arrayOf(value, count) },
        )

        allowOption = false
        this.options.addAll(subcommands)
        return this
    }

    @Nonnull
    override fun addSubcommandGroups(
        @Nonnull vararg groups: SubcommandGroupData,
    ): CommandDataImpl {
        Checks.noneNull(groups, "SubcommandGroups")
        if (groups.isEmpty()) {
            return this
        }
        checkType(Command.Type.SLASH, "add subcommand groups")
        if (!allowSubcommands) {
            throw IllegalArgumentException("You cannot mix options with subcommands/groups.")
        }
        Checks.check(
            groups.size + this.options.size <= CommandData.MAX_OPTIONS,
            "Cannot have more than %d subcommand groups for a command!",
            CommandData.MAX_OPTIONS,
        )
        Checks.checkUnique(
            Stream.concat(getSubcommandGroups().stream(), groups.asList().stream()).map { it.name },
            "Cannot have multiple subcommand groups with the same name. Name: \"%s\" appeared %d times!",
            BiFunction { count, value -> arrayOf(value, count) },
        )

        allowOption = false
        this.options.addAll(groups)
        return this
    }

    @Nonnull
    override fun setLocalizationFunction(
        @Nonnull localizationFunction: LocalizationFunction,
    ): CommandDataImpl {
        Checks.notNull(localizationFunction, "Localization function")

        localizationMapper = LocalizationMapper.fromFunction(localizationFunction)
        return this
    }

    @Nonnull
    override fun setName(
        @Nonnull name: String,
    ): CommandDataImpl {
        checkName(name)
        this.name = name
        return this
    }

    @Nonnull
    override fun setNameLocalization(
        @Nonnull locale: DiscordLocale,
        @Nonnull name: String,
    ): CommandDataImpl {
        // Checks are done in LocalizationMap
        nameLocalizations.setTranslation(locale, name)
        return this
    }

    @Nonnull
    override fun setNameLocalizations(
        @Nonnull map: Map<DiscordLocale, String>,
    ): CommandDataImpl {
        nameLocalizations.setTranslations(map)
        return this
    }

    @Nonnull
    override fun setDescription(
        @Nonnull description: String,
    ): CommandDataImpl {
        checkDescription(description)
        this.description = description
        return this
    }

    @Nonnull
    override fun setDescriptionLocalization(
        @Nonnull locale: DiscordLocale,
        @Nonnull description: String,
    ): CommandDataImpl {
        // Checks are done in LocalizationMap
        descriptionLocalizations.setTranslation(locale, description)
        return this
    }

    @Nonnull
    override fun setDescriptionLocalizations(
        @Nonnull map: Map<DiscordLocale, String>,
    ): CommandDataImpl {
        descriptionLocalizations.setTranslations(map)
        return this
    }

    @Nonnull
    override fun getName(): String = name

    @Nonnull
    override fun getNameLocalizations(): LocalizationMap = nameLocalizations

    @Nonnull
    override fun getDescription(): String = description

    @Nonnull
    override fun getDescriptionLocalizations(): LocalizationMap = descriptionLocalizations

    override fun removeOptions(
        @Nonnull condition: Predicate<in OptionData>,
    ): Boolean {
        Checks.notNull(condition, "Condition")
        val modified = options.removeIf { o -> o is OptionData && condition.test(o) }
        if (modified) {
            updateAllowedOptions()
        }
        return modified
    }

    override fun removeSubcommands(
        @Nonnull condition: Predicate<in SubcommandData>,
    ): Boolean {
        Checks.notNull(condition, "Condition")
        val modified = options.removeIf { o -> o is SubcommandData && condition.test(o) }
        if (modified) {
            updateAllowedOptions()
        }
        return modified
    }

    override fun removeSubcommandGroups(
        @Nonnull condition: Predicate<in SubcommandGroupData>,
    ): Boolean {
        Checks.notNull(condition, "Condition")
        val modified = options.removeIf { o -> o is SubcommandGroupData && condition.test(o) }
        if (modified) {
            updateAllowedOptions()
        }
        return modified
    }

    fun removeAllOptions() {
        options.clear()
        updateAllowedOptions()
    }

    // Update allowed conditions after removing options
    private fun updateAllowedOptions() {
        if (options.isEmpty()) {
            allowRequired = true
            allowOption = true
            allowSubcommands = true
            return
        }

        val last = options[options.size - 1]
        allowOption = last is OptionData
        allowRequired = allowOption && (last as OptionData).isRequired
        allowSubcommands = !allowOption
    }

    companion object {
        @Nonnull
        @JvmStatic
        fun of(
            @Nonnull type: Command.Type,
            @Nonnull name: String,
            @Nullable description: String?,
        ): CommandDataImpl =
            if (type == Command.Type.SLASH) {
                CommandDataImpl(name, description!!)
            } else {
                CommandDataImpl(type, name)
            }
    }
}
