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

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.MemberImpl
import net.dv8tion.jda.internal.interactions.InteractionImpl
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class CommandInteractionPayloadImpl(
    jda: JDAImpl,
    data: DataObject,
) : InteractionImpl(jda, data),
    CommandInteractionPayload {
    private val commandId: Long
    private val options: MutableList<OptionMapping> = ArrayList()
    private val resolved: TLongObjectMap<Any> = TLongObjectHashMap()
    private val name: String
    private val isGuildCommand: Boolean
    private var subcommand: String? = null
    private var group: String? = null
    private val commandType: Command.Type

    init {
        val commandData = data.getObject("data")
        commandId = commandData.getUnsignedLong("id")
        name = commandData.getString("name")
        commandType = Command.Type.fromId(commandData.getInt("type", 1))
        // guild_id is always either null or the owner guild
        // (same as interaction guild_id)
        isGuildCommand = !commandData.isNull("guild_id")

        var options = commandData.optArray("options").orElseGet { DataArray.empty() }
        val resolveJson = commandData.optObject("resolved").orElseGet { DataObject.empty() }

        if (options.length() == 1) {
            var option = options.getObject(0)
            when (OptionType.fromKey(option.getInt("type"))) {
                OptionType.SUB_COMMAND_GROUP -> {
                    group = option.getString("name")
                    options = option.getArray("options")
                    option = options.getObject(0)
                    subcommand = option.getString("name")
                    options = option.optArray("options").orElseGet { DataArray.empty() } // Flatten options
                }

                OptionType.SUB_COMMAND -> {
                    subcommand = option.getString("name")
                    options = option.optArray("options").orElseGet { DataArray.empty() } // Flatten options
                }

                else -> {}
            }
        }

        parseResolved(resolveJson)
        parseOptions(options)
    }

    private fun parseOptions(options: DataArray) {
        options
            .stream { a, i -> a.getObject(i) }
            .map { json -> OptionMapping(json, resolved, api, guild) }
            .forEach { options_ -> this.options.add(options_) }
    }

    private fun parseResolved(resolveJson: DataObject) {
        val entityBuilder = api.entityBuilder

        resolveJson.optObject("users").ifPresent { users ->
            users.keys().forEach { userId ->
                val userJson = users.getObject(userId)
                val userArg = entityBuilder.createUser(userJson)
                resolved.put(userArg.idLong, userArg)
            }
        }

        resolveJson.optObject("attachments").ifPresent { attachments ->
            attachments.keys().forEach { id ->
                val json = attachments.getObject(id)
                val file = entityBuilder.createMessageAttachment(json)
                resolved.put(file.idLong, file)
            }
        }

        if (guild != null) {
            resolveJson.optObject("members").ifPresent { members ->
                val users = resolveJson.getObject("users")
                members.keys().forEach { memberId ->
                    val memberJson = members.getObject(memberId)
                    memberJson.put("user", users.getObject(memberId)) // Add user json as well for parsing
                    val optionMember = interactionEntityBuilder.createMember(guild, memberJson)
                    if (member is MemberImpl) {
                        entityBuilder.updateMemberCache(optionMember as MemberImpl)
                    }
                    resolved.put(optionMember.idLong, optionMember) // This basically upgrades user to member
                }
            }
            resolveJson.optObject("roles").ifPresent { roles ->
                roles
                    .keys()
                    .stream()
                    .map { roleId ->
                        if (!guild.isDetached) {
                            guild.getRoleById(roleId)
                        } else {
                            interactionEntityBuilder.createRole(guild, roles.getObject(roleId))
                        }
                    }.filter { Objects.nonNull(it) }
                    .forEach { role -> resolved.put(role!!.idLong, role) }
            }
            resolveJson.optObject("channels").ifPresent { channels ->
                channels
                    .keys()
                    .forEach { id ->
                        val channelJson = channels.getObject(id)
                        val channelType = ChannelType.fromId(channelJson.getInt("type"))
                        val channelObj: ISnowflake? =
                            if (channelType.isThread) {
                                interactionEntityBuilder.createThreadChannel(guild, channelJson)
                            } else if (channelType.isGuild) {
                                interactionEntityBuilder.createGuildChannel(guild, channelJson)
                            } else {
                                null
                            }
                        if (channelObj != null) {
                            resolved.put(channelObj.idLong, channelObj)
                        }
                    }
            }
        }
    }

    @Nullable
    override fun getChannel(): MessageChannelUnion = getChannelChannel() as MessageChannelUnion

    @Nonnull
    override fun getCommandType(): Command.Type = commandType

    @Nonnull
    override fun getName(): String = name

    override fun getSubcommandName(): String? = subcommand

    override fun getSubcommandGroup(): String? = group

    override fun getCommandIdLong(): Long = commandId

    override fun isGuildCommand(): Boolean = isGuildCommand

    @Nonnull
    override fun getOptions(): List<OptionMapping> = options
}
