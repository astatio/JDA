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
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.CommandEditAction
import net.dv8tion.jda.internal.interactions.CommandDataImpl
import net.dv8tion.jda.internal.interactions.command.CommandImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull

private const val UNDEFINED = "undefined"
private const val NAME_SET = 1 shl 0
private const val DESCRIPTION_SET = 1 shl 1
private const val OPTIONS_SET = 1 shl 2
private const val PERMISSIONS_SET = 1 shl 3
private const val NSFW_SET = 1 shl 4
private const val INTERACTION_CONTEXTS_SET = 1 shl 5
private const val INTEGRATION_TYPES_SET = 1 shl 6

class CommandEditActionImpl :
    RestActionImpl<Command>,
    CommandEditAction {
    private val guild: Guild?
    private var mask = 0
    private var data: CommandDataImpl

    constructor(api: JDA, type: Command.Type, id: String) :
        super(api, Route.Interactions.EDIT_COMMAND.compile(api.selfUser.applicationId, id)) {
        this.guild = null
        this.data = CommandDataImpl.of(type, UNDEFINED, UNDEFINED)
        reset()
    }

    constructor(guild: Guild, type: Command.Type, id: String) : super(
        guild.jda,
        Route.Interactions.EDIT_GUILD_COMMAND.compile(guild.jda.selfUser.applicationId, guild.id, id),
    ) {
        this.guild = guild
        this.data = CommandDataImpl.of(type, UNDEFINED, UNDEFINED)
        reset()
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): CommandEditAction = super.setCheck(checks) as CommandEditAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): CommandEditAction = super.deadline(timestamp) as CommandEditAction

    @Nonnull
    override fun apply(
        @Nonnull commandData: CommandData,
    ): CommandEditAction {
        Checks.notNull(commandData, "Command Data")
        this.mask = NAME_SET or
            DESCRIPTION_SET or
            OPTIONS_SET or
            PERMISSIONS_SET or
            NSFW_SET or
            INTERACTION_CONTEXTS_SET or
            INTEGRATION_TYPES_SET
        this.data = commandData as CommandDataImpl
        return this
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun addCheck(
        @Nonnull checks: BooleanSupplier,
    ): CommandEditAction = super.addCheck(checks) as CommandEditAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): CommandEditAction = super.timeout(timeout, unit) as CommandEditAction

    @Nonnull
    override fun setName(name: String?): CommandEditAction {
        if (name == null) {
            this.mask = this.mask and NAME_SET.inv()
            return this
        }
        data.setName(name)
        this.mask = this.mask or NAME_SET
        return this
    }

    @Nonnull
    override fun setContexts(
        @Nonnull contexts: Collection<InteractionContextType>,
    ): CommandEditAction {
        data.setContexts(contexts)
        this.mask = this.mask or INTERACTION_CONTEXTS_SET
        return this
    }

    @Nonnull
    override fun setIntegrationTypes(
        @Nonnull integrationTypes: Collection<IntegrationType>,
    ): CommandEditAction {
        data.setIntegrationTypes(integrationTypes)
        this.mask = this.mask or INTEGRATION_TYPES_SET
        return this
    }

    @Nonnull
    override fun setNSFW(nsfw: Boolean): CommandEditAction {
        data.setNSFW(nsfw)
        this.mask = this.mask or NSFW_SET
        return this
    }

    @Nonnull
    override fun setDefaultPermissions(
        @Nonnull permission: DefaultMemberPermissions,
    ): CommandEditAction {
        data.setDefaultPermissions(permission)
        this.mask = this.mask or PERMISSIONS_SET
        return this
    }

    @Nonnull
    override fun setDescription(description: String?): CommandEditAction {
        if (description == null) {
            this.mask = this.mask and DESCRIPTION_SET.inv()
            return this
        }
        data.setDescription(description)
        this.mask = this.mask or DESCRIPTION_SET
        return this
    }

    @Nonnull
    override fun clearOptions(): CommandEditAction {
        data.removeAllOptions()
        this.mask = this.mask or OPTIONS_SET
        return this
    }

    @Nonnull
    override fun addOptions(
        @Nonnull vararg options: OptionData,
    ): CommandEditAction {
        data.addOptions(*options)
        this.mask = this.mask or OPTIONS_SET
        return this
    }

    @Nonnull
    override fun addSubcommands(
        @Nonnull vararg subcommands: SubcommandData,
    ): CommandEditAction {
        data.addSubcommands(*subcommands)
        this.mask = this.mask or OPTIONS_SET
        return this
    }

    @Nonnull
    override fun addSubcommandGroups(
        @Nonnull vararg groups: SubcommandGroupData,
    ): CommandEditAction {
        data.addSubcommandGroups(*groups)
        this.mask = this.mask or OPTIONS_SET
        return this
    }

    private fun isUnchanged(flag: Int): Boolean = (mask and flag) != flag

    override fun finalizeData(): RequestBody? {
        val json = data.toData()
        if (isUnchanged(NAME_SET)) {
            json.remove("name")
        }
        if (isUnchanged(DESCRIPTION_SET)) {
            json.remove("description")
        }
        if (isUnchanged(OPTIONS_SET)) {
            json.remove("options")
        }
        if (isUnchanged(PERMISSIONS_SET)) {
            json.remove("default_member_permissions")
        }
        if (isUnchanged(NSFW_SET)) {
            json.remove("nsfw")
        }
        if (isUnchanged(INTERACTION_CONTEXTS_SET)) {
            json.remove("contexts")
        }
        if (isUnchanged(INTEGRATION_TYPES_SET)) {
            json.remove("integration_types")
        }
        reset()
        return getRequestBody(json)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<Command>,
    ) {
        val json = response.getObject()
        request.onSuccess(CommandImpl(api, guild, json))
    }

    private fun reset() {
        mask = 0
        data = CommandDataImpl.of(data.type, UNDEFINED, UNDEFINED)
    }
}
