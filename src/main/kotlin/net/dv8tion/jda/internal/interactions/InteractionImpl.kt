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

import net.dv8tion.jda.api.entities.Entitlement
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.IntegrationOwners
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.InteractionEntityBuilder
import net.dv8tion.jda.internal.entities.MemberImpl
import net.dv8tion.jda.internal.utils.Helpers
import javax.annotation.Nonnull
import javax.annotation.Nullable

open class InteractionImpl(
    jda: JDAImpl,
    data: DataObject,
) : Interaction {
    @JvmField
    protected val id: Long

    @JvmField
    protected val channelId: Long

    @JvmField
    protected val interactionType: Int

    // Widened from protected for InteractionHookImpl, a same-package non-subclass reader.
    @JvmField
    internal val token: String

    @JvmField
    protected val guild: Guild?

    @JvmField
    protected val member: Member?

    @JvmField
    protected val user: User

    @JvmField
    protected val channel: Channel?

    @JvmField
    protected val userLocale: DiscordLocale

    @JvmField
    protected val entitlements: List<Entitlement>

    @JvmField
    protected val context: InteractionContextType

    @JvmField
    protected val integrationOwners: IntegrationOwners

    @JvmField
    protected val api: JDAImpl

    @JvmField
    protected val interactionEntityBuilder: InteractionEntityBuilder

    // This is used to give a proper error when an interaction is ack'd twice
    // By default, discord only responds with "unknown interaction"
    // which is horrible UX so we add a check manually here
    private var isAck: Boolean = false

    init {
        val userObj = data.optObject("member").orElse(data).getObject("user")
        this.api = jda
        this.interactionEntityBuilder =
            InteractionEntityBuilder(jda, data.getLong("channel_id"), userObj.getUnsignedLong("id"))
        this.id = data.getUnsignedLong("id")
        this.token = data.getString("token")
        this.interactionType = data.getInt("type")
        val guild =
            data
                .optObject("guild")
                .map { guildJson ->
                    if (!guildJson.hasKey("preferred_locale")) {
                        guildJson.put("preferred_locale", data.getString("guild_locale", "en-US"))
                    }
                    interactionEntityBuilder.getOrCreateGuild(guildJson)
                }.orElse(null)
        this.guild = guild
        this.channelId = data.getUnsignedLong("channel_id", 0L)
        this.userLocale = DiscordLocale.from(data.getString("locale", "en-US"))
        this.context = InteractionContextType.fromKey(data.getString("context"))
        this.integrationOwners = IntegrationOwnersImpl(data.getObject("authorizing_integration_owners"))

        val channelJson = data.getObject("channel")
        val channelType = ChannelType.fromId(channelJson.getInt("type"))

        if (guild != null) {
            val member = interactionEntityBuilder.createMember(guild, data.getObject("member"))
            this.member = member
            this.user = member.user

            if (!guild.isDetached && member is MemberImpl) {
                jda.entityBuilder.updateMemberCache(member)
            }

            this.channel =
                if (channelType.isThread) {
                    interactionEntityBuilder.createThreadChannel(guild, channelJson)
                } else {
                    interactionEntityBuilder.createGuildChannel(guild, channelJson)
                }
        } else {
            user = jda.entityBuilder.createUser(userObj)
            member = null
            when (channelType) {
                ChannelType.PRIVATE ->
                    this.channel = interactionEntityBuilder.createPrivateChannel(channelJson, user)

                ChannelType.GROUP -> this.channel = interactionEntityBuilder.createGroupChannel(channelJson)

                else ->
                    throw IllegalArgumentException(
                        "Received interaction in unexpected channel type! Type $channelType is not supported yet!",
                    )
            }
        }

        if (channel == null) {
            throw IllegalStateException(
                "Failed to create channel instance for interaction! Channel Type: ${channelJson.getInt("type")}",
            )
        }

        this.entitlements =
            data
                .optArray("entitlements")
                .orElseGet { DataArray.empty() }
                .stream { a, i -> a.getObject(i) }
                .map { jda.entityBuilder.createEntitlement(it) }
                .collect(Helpers.toUnmodifiableList())
    }

    // Used to allow interaction hook to send messages after acknowledgements
    // This is implemented only in DeferrableInteractionImpl where a hook is present!
    @Synchronized
    open fun releaseHook(success: Boolean) {}

    // Ensures that one cannot acknowledge an interaction twice
    @Synchronized
    fun ack(): Boolean {
        val wasAck = isAck
        isAck = true
        return wasAck
    }

    @Synchronized
    override fun isAcknowledged(): Boolean = isAck

    override fun getIdLong(): Long = id

    override fun getTypeRaw(): Int = interactionType

    @Nonnull
    override fun getToken(): String = token

    @Nullable
    override fun getGuild(): Guild? = guild

    @Nullable
    override fun getChannel(): Channel? = channel

    // Java subclasses call super.getChannel(); the interface member cannot be invoked directly from Kotlin.
    @Nullable
    protected fun getChannelChannel(): Channel? = channel

    override fun getChannelIdLong(): Long = channelId

    @Nonnull
    override fun getUserLocale(): DiscordLocale = userLocale

    @Nonnull
    override fun getContext(): InteractionContextType = context

    @Nonnull
    override fun getIntegrationOwners(): IntegrationOwners = integrationOwners

    @Nonnull
    override fun getUser(): User = user

    @Nullable
    override fun getMember(): Member? = member

    @Nonnull
    override fun getEntitlements(): List<Entitlement> = entitlements

    @Nonnull
    override fun getJDA(): JDAImpl = api

    @Nonnull
    fun getInteractionEntityBuilder(): InteractionEntityBuilder = interactionEntityBuilder
}
