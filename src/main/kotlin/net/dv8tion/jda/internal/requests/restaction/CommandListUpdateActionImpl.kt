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
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.interactions.command.CommandImpl
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import java.util.function.BooleanSupplier
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.annotation.Nonnull

class CommandListUpdateActionImpl(
    api: JDA,
    private val guild: GuildImpl?,
    route: Route.CompiledRoute,
) : RestActionImpl<@JvmSuppressWildcards List<Command>>(api, route),
    CommandListUpdateAction {
    private val commands: MutableList<CommandData> = ArrayList()
    private var slash = 0
    private var user = 0
    private var message = 0

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): CommandListUpdateAction = super.timeout(timeout, unit) as CommandListUpdateAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun addCheck(
        @Nonnull checks: BooleanSupplier,
    ): CommandListUpdateAction = super.addCheck(checks) as CommandListUpdateAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): CommandListUpdateAction = super.setCheck(checks) as CommandListUpdateAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): CommandListUpdateAction = super.deadline(timestamp) as CommandListUpdateAction

    @Nonnull
    override fun addCommands(
        @Nonnull commands: Collection<CommandData>,
    ): CommandListUpdateAction {
        Checks.noneNull(commands, "Command")
        var newSlash = 0
        var newUser = 0
        var newMessage = 0
        for (command in commands) {
            when (command.type) {
                Command.Type.SLASH -> newSlash++
                Command.Type.MESSAGE -> newMessage++
                Command.Type.USER -> newUser++
                Command.Type.UNKNOWN -> throw IllegalArgumentException("Provided command of unknown type")
            }
        }

        Checks.check(
            slash + newSlash <= Commands.MAX_SLASH_COMMANDS,
            "Cannot have more than %d slash commands! Try using subcommands instead.",
            Commands.MAX_SLASH_COMMANDS,
        )
        Checks.check(
            user + newUser <= Commands.MAX_USER_COMMANDS,
            "Cannot have more than %d user context commands!",
            Commands.MAX_USER_COMMANDS,
        )
        Checks.check(
            message + newMessage <= Commands.MAX_MESSAGE_COMMANDS,
            "Cannot have more than %d message context commands!",
            Commands.MAX_MESSAGE_COMMANDS,
        )

        Checks.checkUnique(
            Stream.concat(commands.stream(), this.commands.stream()).map { c: CommandData ->
                c.type.toString() + " " + c.name
            },
            "Cannot have multiple commands of the same type with identical names. " +
                "Name: \"%s\" with type %s appeared %d times!",
            BiFunction { count: Long, value: String ->
                val tuple = value.split(" ".toRegex(), limit = 2)
                arrayOf<Any>(tuple[1], tuple[0], count)
            },
        )

        slash += newSlash
        user += newUser
        message += newMessage

        this.commands.addAll(commands)
        return this
    }

    override fun finalizeData(): RequestBody? {
        val json = DataArray.empty()
        json.addAll(commands)
        return getRequestBody(json)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<List<Command>>,
    ) {
        val commands =
            response
                .getArray()
                .stream { array: DataArray, i: Int -> array.getObject(i) }
                .map { obj -> CommandImpl(api, guild, obj) }
                .collect(Collectors.toList())
        request.onSuccess(commands)
    }
}
