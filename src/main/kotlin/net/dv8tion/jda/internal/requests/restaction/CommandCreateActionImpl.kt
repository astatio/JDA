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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationMap
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.interactions.CommandDataImpl
import net.dv8tion.jda.internal.interactions.command.CommandImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import java.util.function.Predicate
import javax.annotation.Nonnull

class CommandCreateActionImpl :
    RestActionImpl<Command>,
    CommandCreateAction {
    private val guild: Guild?
    private val data: CommandDataImpl

    constructor(api: JDAImpl, command: CommandDataImpl) :
        super(api, Route.Interactions.CREATE_COMMAND.compile(api.selfUser.applicationId)) {
        this.guild = null
        this.data = command
    }

    constructor(guild: Guild, command: CommandDataImpl) : super(
        guild.jda,
        Route.Interactions.CREATE_GUILD_COMMAND.compile(guild.jda.selfUser.applicationId, guild.id),
    ) {
        this.guild = guild
        this.data = command
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun addCheck(
        @Nonnull checks: BooleanSupplier,
    ): CommandCreateAction = super.addCheck(checks) as CommandCreateAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): CommandCreateAction = super.setCheck(checks) as CommandCreateAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): CommandCreateAction = super.deadline(timestamp) as CommandCreateAction

    @Nonnull
    override fun setDefaultPermissions(
        @Nonnull permission: DefaultMemberPermissions,
    ): CommandCreateAction {
        data.setDefaultPermissions(permission)
        return this
    }

    @Nonnull
    override fun setContexts(
        @Nonnull contexts: Collection<InteractionContextType>,
    ): CommandCreateAction {
        data.setContexts(contexts)
        return this
    }

    @Nonnull
    override fun setIntegrationTypes(
        @Nonnull integrationTypes: Collection<IntegrationType>,
    ): CommandCreateAction {
        data.setIntegrationTypes(integrationTypes)
        return this
    }

    @Nonnull
    override fun setNSFW(nsfw: Boolean): CommandCreateAction {
        data.setNSFW(nsfw)
        return this
    }

    @Nonnull
    override fun setLocalizationFunction(
        @Nonnull localizationFunction: LocalizationFunction,
    ): CommandCreateAction {
        data.setLocalizationFunction(localizationFunction)
        return this
    }

    @Nonnull
    override fun getName(): String = data.name

    @Nonnull
    override fun getNameLocalizations(): LocalizationMap = data.nameLocalizations

    @Nonnull
    override fun getType(): Command.Type = data.type

    @Nonnull
    override fun getDefaultPermissions(): DefaultMemberPermissions = data.defaultPermissions

    @Nonnull
    override fun getContexts(): Set<InteractionContextType> = data.contexts

    @Nonnull
    override fun getIntegrationTypes(): Set<IntegrationType> = data.integrationTypes

    override fun isNSFW(): Boolean = data.isNSFW

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): CommandCreateAction = super.timeout(timeout, unit) as CommandCreateAction

    @Nonnull
    override fun setName(
        @Nonnull name: String,
    ): CommandCreateAction {
        data.setName(name)
        return this
    }

    @Nonnull
    override fun setNameLocalization(
        @Nonnull locale: DiscordLocale,
        @Nonnull name: String,
    ): CommandCreateAction {
        data.setNameLocalization(locale, name)
        return this
    }

    @Nonnull
    override fun setNameLocalizations(
        @Nonnull map: Map<DiscordLocale, String>,
    ): CommandCreateAction {
        data.setNameLocalizations(map)
        return this
    }

    @Nonnull
    override fun setDescription(
        @Nonnull description: String,
    ): CommandCreateAction {
        data.setDescription(description)
        return this
    }

    @Nonnull
    override fun setDescriptionLocalization(
        @Nonnull locale: DiscordLocale,
        @Nonnull description: String,
    ): CommandCreateAction {
        data.setDescriptionLocalization(locale, description)
        return this
    }

    @Nonnull
    override fun setDescriptionLocalizations(
        @Nonnull map: Map<DiscordLocale, String>,
    ): CommandCreateAction {
        data.setDescriptionLocalizations(map)
        return this
    }

    @Nonnull
    override fun getDescription(): String = data.description

    @Nonnull
    override fun getDescriptionLocalizations(): LocalizationMap = data.descriptionLocalizations

    override fun removeOptions(
        @Nonnull condition: Predicate<in OptionData>,
    ): Boolean = data.removeOptions(condition)

    override fun removeSubcommands(
        @Nonnull condition: Predicate<in SubcommandData>,
    ): Boolean = data.removeSubcommands(condition)

    override fun removeSubcommandGroups(
        @Nonnull condition: Predicate<in SubcommandGroupData>,
    ): Boolean = data.removeSubcommandGroups(condition)

    @Nonnull
    override fun getSubcommands(): List<SubcommandData> = data.subcommands

    @Nonnull
    override fun getSubcommandGroups(): List<SubcommandGroupData> = data.subcommandGroups

    @Nonnull
    override fun getOptions(): List<OptionData> = data.options

    @Nonnull
    override fun addOptions(
        @Nonnull vararg options: OptionData,
    ): CommandCreateAction {
        data.addOptions(*options)
        return this
    }

    @Nonnull
    override fun addSubcommands(
        @Nonnull vararg subcommand: SubcommandData,
    ): CommandCreateAction {
        data.addSubcommands(*subcommand)
        return this
    }

    @Nonnull
    override fun addSubcommandGroups(
        @Nonnull vararg group: SubcommandGroupData,
    ): CommandCreateAction {
        data.addSubcommandGroups(*group)
        return this
    }

    override fun finalizeData(): RequestBody? = getRequestBody(data.toData())

    override fun handleSuccess(
        response: Response,
        request: Request<Command>,
    ) {
        val json = response.getObject()
        request.onSuccess(CommandImpl(api, guild, json))
    }

    @Nonnull
    override fun toData(): DataObject = data.toData()
}
