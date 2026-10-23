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

package net.dv8tion.jda.internal.managers

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.automod.AutoModResponse
import net.dv8tion.jda.api.entities.automod.AutoModRule
import net.dv8tion.jda.api.entities.automod.AutoModTriggerType
import net.dv8tion.jda.api.entities.automod.build.TriggerConfig
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.managers.AutoModRuleManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.EnumMap
import javax.annotation.Nonnull

class AutoModRuleManagerImpl(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val guild: Guild,
    ruleId: String,
) : ManagerBase<AutoModRuleManager>(
        guild.getJDA(),
        Route.AutoModeration.UPDATE_RULE.compile(guild.getId(), ruleId),
    ),
    AutoModRuleManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var name: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var enabled: Boolean = false

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var responses: EnumMap<AutoModResponse.Type, AutoModResponse>? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var exemptRoles: MutableList<Role>? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var exemptChannels: MutableList<GuildChannel>? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var triggerConfig: TriggerConfig? = null

    @Nonnull
    override fun setName(
        @Nonnull name: String,
    ): AutoModRuleManager {
        Checks.notEmpty(name, "Name")
        Checks.notLonger(name, AutoModRule.MAX_RULE_NAME_LENGTH, "Name")
        this.name = name
        set = set or AutoModRuleManager.NAME
        return this
    }

    @Nonnull
    override fun setEnabled(enabled: Boolean): AutoModRuleManager {
        this.enabled = enabled
        set = set or AutoModRuleManager.ENABLED
        return this
    }

    @Nonnull
    override fun setResponses(
        @Nonnull responses: Collection<AutoModResponse>,
    ): AutoModRuleManager {
        Checks.noneNull(responses, "Responses")
        Checks.notEmpty(responses, "Responses")
        val map = EnumMap<AutoModResponse.Type, AutoModResponse>(AutoModResponse.Type::class.java)
        for (response in responses) {
            val type = response.getType()
            Checks.check(type != AutoModResponse.Type.UNKNOWN, "Cannot add response with unknown response type!")
            map[type] = response
        }
        this.responses = map
        set = set or AutoModRuleManager.RESPONSE
        return this
    }

    @Nonnull
    override fun setExemptRoles(
        @Nonnull roles: Collection<Role>,
    ): AutoModRuleManager {
        Checks.noneNull(roles, "Roles")
        Checks.check(
            roles.size <= AutoModRule.MAX_EXEMPT_ROLES,
            "Cannot have more than %d exempt roles!",
            AutoModRule.MAX_EXEMPT_ROLES,
        )
        for (role in roles) {
            Checks.check(role.getGuild() == guild, "Role %s is not from the same guild as this rule!", role)
        }
        exemptRoles = ArrayList(roles)
        set = set or AutoModRuleManager.EXEMPT_ROLES
        return this
    }

    @Nonnull
    override fun setExemptChannels(
        @Nonnull channels: Collection<GuildChannel>,
    ): AutoModRuleManager {
        Checks.noneNull(channels, "Channels")
        Checks.check(
            channels.size <= AutoModRule.MAX_EXEMPT_CHANNELS,
            "Cannot have more than %d exempt channels!",
            AutoModRule.MAX_EXEMPT_CHANNELS,
        )
        for (channel in channels) {
            Checks.check(
                channel.getGuild() == guild,
                "Channel %s is not from the same guild as this rule!",
                channel,
            )
        }
        exemptChannels = ArrayList(channels)
        set = set or AutoModRuleManager.EXEMPT_CHANNELS
        return this
    }

    @Nonnull
    override fun setTriggerConfig(
        @Nonnull config: TriggerConfig,
    ): AutoModRuleManager {
        Checks.notNull(config, "TriggerConfig")
        Checks.check(config.getType() != AutoModTriggerType.UNKNOWN, "Unknown trigger type!")
        triggerConfig = config
        set = set or AutoModRuleManager.TRIGGER_METADATA
        return this
    }

    override fun finalizeData(): RequestBody {
        val body = DataObject.empty()

        if (shouldUpdate(AutoModRuleManager.NAME)) {
            body.put("name", name)
        }
        if (shouldUpdate(AutoModRuleManager.ENABLED)) {
            body.put("enabled", enabled)
        }
        if (shouldUpdate(AutoModRuleManager.RESPONSE)) {
            body.put("actions", DataArray.fromCollection(responses!!.values))
        }
        if (shouldUpdate(AutoModRuleManager.EXEMPT_ROLES)) {
            body.put(
                "exempt_roles",
                DataArray.fromCollection(exemptRoles!!.map { it.getId() }),
            )
        }
        if (shouldUpdate(AutoModRuleManager.EXEMPT_CHANNELS)) {
            body.put(
                "exempt_channels",
                DataArray.fromCollection(exemptChannels!!.map { it.getId() }),
            )
        }
        if (shouldUpdate(AutoModRuleManager.TRIGGER_METADATA)) {
            body.put("trigger_type", triggerConfig!!.getType().getKey())
            body.put("trigger_metadata", triggerConfig!!.toData())
        }

        reset()
        return getRequestBody(body)
    }
}
