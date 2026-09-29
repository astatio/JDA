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

package net.dv8tion.jda.internal.entities.automod

import gnu.trove.list.TLongList
import gnu.trove.list.array.TLongArrayList
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.automod.AutoModEventType
import net.dv8tion.jda.api.entities.automod.AutoModResponse
import net.dv8tion.jda.api.entities.automod.AutoModRule
import net.dv8tion.jda.api.entities.automod.AutoModTriggerType
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.util.Collections
import java.util.EnumSet
import java.util.stream.Collectors
import javax.annotation.Nonnull

class AutoModRuleImpl(
    private var guild: Guild,
    private val id: Long,
) : AutoModRule {
    private var ownerId: Long = 0
    private var name: String = ""
    private var eventType: AutoModEventType = AutoModEventType.UNKNOWN
    private var triggerType: AutoModTriggerType = AutoModTriggerType.UNKNOWN
    private var enabled: Boolean = false
    private var exemptRoles: TLongList = TLongArrayList()
    private var exemptChannels: TLongList = TLongArrayList()
    private var actions: List<AutoModResponse> = Collections.emptyList()
    private var filteredKeywords: List<String> = Collections.emptyList()
    private var filteredRegex: List<String> = Collections.emptyList()
    private var filteredPresets: EnumSet<AutoModRule.KeywordPreset> = EnumSet.noneOf(AutoModRule.KeywordPreset::class.java)
    private var allowlist: List<String> = Collections.emptyList()
    private var mentionLimit: Int = -1
    private var isMentionRaidProtectionEnabled: Boolean = false

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getGuild(): Guild {
        val realGuild = guild.jda.getGuildById(guild.idLong)
        if (realGuild != null) {
            guild = realGuild
        }
        return guild
    }

    override fun getCreatorIdLong(): Long = ownerId

    @Nonnull
    override fun getName(): String = name

    @Nonnull
    override fun getEventType(): AutoModEventType = eventType

    @Nonnull
    override fun getTriggerType(): AutoModTriggerType = triggerType

    override fun isEnabled(): Boolean = enabled

    @Nonnull
    override fun getExemptRoles(): List<Role> {
        val roles: MutableList<Role> = ArrayList(exemptRoles.size())
        for (i in 0 until exemptRoles.size()) {
            val roleId = exemptRoles.get(i)
            val role = guild.getRoleById(roleId)
            if (role != null) {
                roles.add(role)
            }
        }
        return Collections.unmodifiableList(roles)
    }

    @Nonnull
    override fun getExemptChannels(): List<GuildChannel> {
        val channels: MutableList<GuildChannel> = ArrayList(exemptChannels.size())
        for (i in 0 until exemptChannels.size()) {
            val channelId = exemptChannels.get(i)
            val channel = guild.getGuildChannelById(channelId)
            if (channel != null) {
                channels.add(channel)
            }
        }
        return Collections.unmodifiableList(channels)
    }

    @Nonnull
    override fun getActions(): List<AutoModResponse> = actions

    @Nonnull
    override fun getFilteredKeywords(): List<String> = filteredKeywords

    @Nonnull
    override fun getFilteredRegex(): List<String> = filteredRegex

    @Nonnull
    override fun getFilteredPresets(): EnumSet<AutoModRule.KeywordPreset> =
        Helpers.copyEnumSet(AutoModRule.KeywordPreset::class.java, filteredPresets)

    @Nonnull
    override fun getAllowlist(): List<String> = allowlist

    override fun getMentionLimit(): Int = mentionLimit

    override fun isMentionRaidProtectionEnabled(): Boolean = isMentionRaidProtectionEnabled

    fun setName(name: String): AutoModRuleImpl {
        this.name = name
        return this
    }

    fun setEnabled(enabled: Boolean): AutoModRuleImpl {
        this.enabled = enabled
        return this
    }

    fun setOwnerId(ownerId: Long): AutoModRuleImpl {
        this.ownerId = ownerId
        return this
    }

    fun setEventType(eventType: AutoModEventType): AutoModRuleImpl {
        this.eventType = eventType
        return this
    }

    fun setTriggerType(triggerType: AutoModTriggerType): AutoModRuleImpl {
        this.triggerType = triggerType
        return this
    }

    fun setExemptRoles(exemptRoles: TLongList): AutoModRuleImpl {
        this.exemptRoles = exemptRoles
        return this
    }

    fun setExemptChannels(exemptChannels: TLongList): AutoModRuleImpl {
        this.exemptChannels = exemptChannels
        return this
    }

    fun setActions(actions: List<AutoModResponse>): AutoModRuleImpl {
        this.actions = actions
        return this
    }

    fun setFilteredKeywords(filteredKeywords: List<String>): AutoModRuleImpl {
        this.filteredKeywords = filteredKeywords
        return this
    }

    fun setFilteredRegex(filteredRegex: List<String>): AutoModRuleImpl {
        this.filteredRegex = filteredRegex
        return this
    }

    fun setFilteredPresets(filteredPresets: EnumSet<AutoModRule.KeywordPreset>): AutoModRuleImpl {
        this.filteredPresets = filteredPresets
        return this
    }

    fun setAllowlist(allowlist: List<String>): AutoModRuleImpl {
        this.allowlist = allowlist
        return this
    }

    fun setMentionLimit(mentionLimit: Int): AutoModRuleImpl {
        this.mentionLimit = mentionLimit
        return this
    }

    fun setMentionRaidProtectionEnabled(mentionRaidProtectionEnabled: Boolean): AutoModRuleImpl {
        isMentionRaidProtectionEnabled = mentionRaidProtectionEnabled
        return this
    }

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is AutoModRuleImpl) {
            return false
        }
        return this.id == other.id
    }

    override fun toString(): String =
        EntityString(this)
            .setType(triggerType)
            .setName(name)
            .addMetadata("id", getId())
            .toString()

    companion object {
        @JvmStatic
        fun fromData(
            guild: Guild,
            data: DataObject,
        ): AutoModRuleImpl {
            val id = data.getUnsignedLong("id")
            val rule = AutoModRuleImpl(guild, id)

            rule
                .setName(data.getString("name"))
                .setEnabled(data.getBoolean("enabled", true))
                .setOwnerId(data.getUnsignedLong("creator_id", 0L))
                .setEventType(AutoModEventType.fromKey(data.getInt("event_type", -1)))
                .setTriggerType(AutoModTriggerType.fromKey(data.getInt("trigger_type", -1)))

            data.optArray("exempt_roles").ifPresent { array -> rule.setExemptRoles(parseList(array)) }
            data.optArray("exempt_channels").ifPresent { array -> rule.setExemptChannels(parseList(array)) }

            data.optArray("actions").ifPresent { array ->
                rule.setActions(
                    array
                        .stream { a, i -> a.getObject(i) }
                        .map { obj -> AutoModResponseImpl(guild, obj) }
                        .collect(Helpers.toUnmodifiableList()),
                )
            }

            data.optObject("trigger_metadata").ifPresent { metadata ->
                // Only for KEYWORD type
                metadata.optArray("keyword_filter").ifPresent { array ->
                    rule.setFilteredKeywords(
                        array.stream { a, i -> a.getString(i) }.collect(Helpers.toUnmodifiableList()),
                    )
                }
                metadata.optArray("regex_patterns").ifPresent { array ->
                    rule.setFilteredRegex(
                        array.stream { a, i -> a.getString(i) }.collect(Helpers.toUnmodifiableList()),
                    )
                }
                // Both KEYWORD and KEYWORD_PRESET
                metadata.optArray("allow_list").ifPresent { array ->
                    rule.setAllowlist(
                        array.stream { a, i -> a.getString(i) }.collect(Helpers.toUnmodifiableList()),
                    )
                }
                // Only KEYWORD_PRESET
                metadata.optArray("presets").ifPresent { array ->
                    rule.setFilteredPresets(
                        array
                            .stream { a, i -> a.getInt(i) }
                            .map { AutoModRule.KeywordPreset.fromKey(it) }
                            .collect(
                                Collectors.toCollection {
                                    EnumSet.noneOf(AutoModRule.KeywordPreset::class.java)
                                },
                            ),
                    )
                }
                // Only for MENTION type
                rule.setMentionLimit(metadata.getInt("mention_total_limit", 0))
                rule.setMentionRaidProtectionEnabled(metadata.getBoolean("mention_raid_protection_enabled"))
            }

            return rule
        }

        private fun parseList(array: DataArray): TLongList {
            val list = TLongArrayList(array.length())
            for (i in 0 until array.length()) {
                list.add(array.getUnsignedLong(i))
            }
            return list
        }
    }
}
