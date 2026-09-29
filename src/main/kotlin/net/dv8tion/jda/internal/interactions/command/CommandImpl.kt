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

package net.dv8tion.jda.internal.interactions.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationMap
import net.dv8tion.jda.api.interactions.commands.privileges.IntegrationPrivilege
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.CommandEditAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.CommandEditActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import net.dv8tion.jda.internal.utils.localization.LocalizationUtils
import java.util.Collections
import java.util.EnumSet
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors
import javax.annotation.Nonnull

class CommandImpl(
    private val api: JDAImpl,
    private val guild: Guild?,
    json: DataObject,
) : Command {
    private val name: String
    private val description: String
    private val nameLocalizations: LocalizationMap
    private val descriptionLocalizations: LocalizationMap
    private val options: List<Command.Option>
    private val groups: List<Command.SubcommandGroup>
    private val subcommands: List<Command.Subcommand>
    private val id: Long
    private val guildId: Long
    private val applicationId: Long
    private val version: Long
    private val nsfw: Boolean
    private val contexts: Set<InteractionContextType>
    private val integrationTypes: Set<IntegrationType>
    private val type: Command.Type
    private val defaultMemberPermissions: DefaultMemberPermissions

    init {
        name = json.getString("name")
        nameLocalizations = LocalizationUtils.unmodifiableFromProperty(json, "name_localizations")
        description = json.getString("description", "")
        descriptionLocalizations = LocalizationUtils.unmodifiableFromProperty(json, "description_localizations")
        type = Command.Type.fromId(json.getInt("type", 1))
        id = json.getUnsignedLong("id")
        guildId = if (guild != null) guild.idLong else 0L
        applicationId = json.getUnsignedLong("application_id", api.selfUser.applicationIdLong)
        options = parseOptions(json, OPTION_TEST) { Command.Option(it) }
        groups = parseOptions(json, GROUP_TEST) { Command.SubcommandGroup(this, it) }
        subcommands = parseOptions(json, SUBCOMMAND_TEST) { Command.Subcommand(this, it) }
        version = json.getUnsignedLong("version", id)

        defaultMemberPermissions =
            if (json.isNull("default_member_permissions")) {
                DefaultMemberPermissions.ENABLED
            } else {
                DefaultMemberPermissions.enabledFor(json.getLong("default_member_permissions"))
            }

        contexts =
            if (!json.isNull("contexts")) {
                json
                    .getArray("contexts")
                    .stream { a, i -> a.getString(i) }
                    .map { InteractionContextType.fromKey(it) }
                    .collect(Helpers.toUnmodifiableEnumSet(InteractionContextType::class.java))
            } else if (guildId != 0L) {
                // If the command is in a guild, it can only be guild,
                // otherwise up to the dm_permission flag
                Helpers.unmodifiableEnumSet(InteractionContextType.GUILD)
            } else {
                val dmPermission = json.getBoolean("dm_permission", true)
                if (dmPermission) {
                    Helpers.unmodifiableEnumSet(InteractionContextType.GUILD, InteractionContextType.BOT_DM)
                } else {
                    Helpers.unmodifiableEnumSet(InteractionContextType.GUILD)
                }
            }

        integrationTypes =
            if (!json.isNull("integration_types")) {
                json
                    .getArray("integration_types")
                    .stream { a, i -> a.getString(i) }
                    .map { IntegrationType.fromKey(it) }
                    .collect(Helpers.toUnmodifiableEnumSet(IntegrationType::class.java))
            } else {
                Helpers.unmodifiableEnumSet(IntegrationType.GUILD_INSTALL)
            }

        nsfw = json.getBoolean("nsfw")
    }

    @Nonnull
    override fun delete(): RestAction<Void> {
        checkSelfUser("Cannot delete a command from another bot!")
        val route: Route.CompiledRoute
        val appId = jda.selfUser.applicationId
        route =
            if (guildId != 0L) {
                Route.Interactions.DELETE_GUILD_COMMAND.compile(appId, java.lang.Long.toUnsignedString(guildId), getId())
            } else {
                Route.Interactions.DELETE_COMMAND.compile(appId, getId())
            }
        return RestActionImpl(api, route)
    }

    @Nonnull
    override fun editCommand(): CommandEditAction {
        checkSelfUser("Cannot edit a command from another bot!")
        return if (guild == null) {
            CommandEditActionImpl(api, type, getId())
        } else {
            CommandEditActionImpl(guild, type, getId())
        }
    }

    @Nonnull
    override fun retrievePrivileges(
        @Nonnull guild: Guild,
    ): RestAction<List<IntegrationPrivilege>> {
        checkSelfUser("Cannot retrieve privileges for a command from another bot!")
        Checks.notNull(guild, "Guild")
        return guild.retrieveIntegrationPrivilegesById(id)
    }

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun getType(): Command.Type = type

    @Nonnull
    override fun getName(): String = name

    @Nonnull
    override fun getNameLocalizations(): LocalizationMap = nameLocalizations

    @Nonnull
    override fun getFullCommandName(): String = name

    @Nonnull
    override fun getDescription(): String = description

    @Nonnull
    override fun getDescriptionLocalizations(): LocalizationMap = descriptionLocalizations

    @Nonnull
    override fun getOptions(): List<Command.Option> = options

    @Nonnull
    override fun getSubcommands(): List<Command.Subcommand> = subcommands

    @Nonnull
    override fun getSubcommandGroups(): List<Command.SubcommandGroup> = groups

    override fun getApplicationIdLong(): Long = applicationId

    override fun getVersion(): Long = version

    @Nonnull
    override fun getDefaultPermissions(): DefaultMemberPermissions = defaultMemberPermissions

    @Nonnull
    override fun getContexts(): EnumSet<InteractionContextType> = Helpers.copyEnumSet(InteractionContextType::class.java, contexts)

    @Nonnull
    override fun getIntegrationTypes(): EnumSet<IntegrationType> = Helpers.copyEnumSet(IntegrationType::class.java, integrationTypes)

    override fun isNSFW(): Boolean = nsfw

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getAsMention(): String {
        if (type != Command.Type.SLASH) {
            throw IllegalStateException("Only slash commands can be mentioned")
        }
        return super.getAsMention()
    }

    override fun toString(): String = EntityString(this).setType(type).setName(name).toString()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Command) {
            return false
        }
        return id == other.idLong
    }

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    private fun checkSelfUser(s: String) {
        if (applicationId != api.selfUser.applicationIdLong) {
            throw IllegalStateException(s)
        }
    }

    companion object {
        @JvmField
        val OPTIONS: EnumSet<OptionType> =
            EnumSet.complementOf(EnumSet.of(OptionType.SUB_COMMAND, OptionType.SUB_COMMAND_GROUP))

        @JvmField
        val OPTION_TEST: Predicate<DataObject> = Predicate { OPTIONS.contains(OptionType.fromKey(it.getInt("type"))) }

        @JvmField
        val SUBCOMMAND_TEST: Predicate<DataObject> =
            Predicate { OptionType.fromKey(it.getInt("type")) == OptionType.SUB_COMMAND }

        @JvmField
        val GROUP_TEST: Predicate<DataObject> =
            Predicate { OptionType.fromKey(it.getInt("type")) == OptionType.SUB_COMMAND_GROUP }

        @JvmStatic
        fun <T> parseOptions(
            json: DataObject,
            test: Predicate<DataObject>,
            transform: Function<DataObject, T>,
        ): List<T> =
            json
                .optArray("options")
                .map { arr ->
                    arr
                        .stream { a, i -> a.getObject(i) }
                        .filter(test)
                        .map(transform)
                        .collect(Collectors.toList())
                }.orElse(Collections.emptyList())
    }
}
