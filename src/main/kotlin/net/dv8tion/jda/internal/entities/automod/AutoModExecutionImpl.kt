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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.automod.AutoModExecution
import net.dv8tion.jda.api.entities.automod.AutoModResponse
import net.dv8tion.jda.api.entities.automod.AutoModTriggerType
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion
import net.dv8tion.jda.api.utils.data.DataObject
import javax.annotation.Nonnull
import javax.annotation.Nullable

class AutoModExecutionImpl(
    private val guild: Guild,
    json: DataObject,
) : AutoModExecution {
    private val channel: GuildMessageChannel? =
        guild.getChannelById(GuildMessageChannel::class.java, json.getUnsignedLong("channel_id", 0L))
    private val response: AutoModResponse = AutoModResponseImpl(guild, json.getObject("action"))
    private val type: AutoModTriggerType = AutoModTriggerType.fromKey(json.getInt("rule_trigger_type", -1))
    private val userId: Long = json.getUnsignedLong("user_id")
    private val ruleId: Long = json.getUnsignedLong("rule_id")
    private val messageId: Long = json.getUnsignedLong("message_id", 0L)
    private val alertMessageId: Long = json.getUnsignedLong("alert_system_message_id", 0L)
    private val content: String = json.getString("content", "")
    private val matchedContent: String? = json.getString("matched_content", null)
    private val matchedKeyword: String? = json.getString("matched_keyword", null)

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nullable
    override fun getChannel(): GuildMessageChannelUnion? = channel as GuildMessageChannelUnion?

    @Nonnull
    override fun getResponse(): AutoModResponse = response

    @Nonnull
    override fun getTriggerType(): AutoModTriggerType = type

    override fun getUserIdLong(): Long = userId

    override fun getRuleIdLong(): Long = ruleId

    override fun getMessageIdLong(): Long = messageId

    override fun getAlertMessageIdLong(): Long = alertMessageId

    @Nonnull
    override fun getContent(): String = content

    @Nullable
    override fun getMatchedContent(): String? = matchedContent

    @Nullable
    override fun getMatchedKeyword(): String? = matchedKeyword
}
