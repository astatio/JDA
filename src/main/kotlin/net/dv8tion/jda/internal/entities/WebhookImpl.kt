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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.Webhook.ChannelReference
import net.dv8tion.jda.api.entities.Webhook.GuildReference
import net.dv8tion.jda.api.entities.WebhookClient
import net.dv8tion.jda.api.entities.WebhookType
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer
import net.dv8tion.jda.api.entities.channel.unions.IWebhookContainerUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.WebhookManager
import net.dv8tion.jda.api.requests.RestConfig
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageDeleteAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageRetrieveAction
import net.dv8tion.jda.internal.managers.WebhookManagerImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageCreateActionImpl
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageEditActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * The implementation for [net.dv8tion.jda.api.entities.Webhook].
 */
open class WebhookImpl(
    private val channel: IWebhookContainer?,
    api: JDA,
    id: Long,
    private val type: WebhookType,
) : AbstractWebhookClient<Message>(id, null, api),
    Webhook {
    private var owner: Member? = null
    private var user: User? = null
    private var ownerUser: User? = null
    private var sourceChannel: ChannelReference? = null
    private var sourceGuild: GuildReference? = null

    constructor(channel: IWebhookContainer, id: Long, type: WebhookType) : this(channel, channel.jda, id, type)

    @Nonnull
    override fun getType(): WebhookType = type

    override fun isPartial(): Boolean = channel == null

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun getGuild(): Guild {
        if (channel == null) {
            throw IllegalStateException(
                "Cannot provide guild for this Webhook instance because it does not belong to this shard",
            )
        }
        return getChannel().guild
    }

    @Nonnull
    override fun getChannel(): IWebhookContainerUnion {
        if (channel == null) {
            throw IllegalStateException(
                "Cannot provide channel for this Webhook instance because it does not belong to this shard",
            )
        }
        return channel as IWebhookContainerUnion
    }

    @Nullable
    override fun getOwner(): Member? {
        if (owner == null && channel != null && ownerUser != null) {
            return getGuild().getMember(ownerUser!!) // maybe it exists later?
        }
        return owner
    }

    @Nullable
    override fun getOwnerAsUser(): User? = ownerUser

    @Nonnull
    override fun getDefaultUser(): User = user!!

    @Nonnull
    override fun getName(): String = user!!.name

    @Nonnull
    override fun getUrl(): String = RestConfig.DEFAULT_BASE_URL + "webhooks/" + id + (if (token == null) "" else "/" + token)

    @Nullable
    override fun getSourceChannel(): ChannelReference? = sourceChannel

    @Nullable
    override fun getSourceGuild(): GuildReference? = sourceGuild

    @Nonnull
    override fun delete(): AuditableRestAction<Void> {
        if (token != null) {
            return delete(token!!)
        }

        if (!getGuild().selfMember.hasPermission(getChannel(), Permission.MANAGE_WEBHOOKS)) {
            throw InsufficientPermissionException(getChannel(), Permission.MANAGE_WEBHOOKS)
        }

        val route = Route.Webhooks.DELETE_WEBHOOK.compile(id.toString())
        return AuditableRestActionImpl(getJDA(), route)
    }

    @Nonnull
    override fun delete(token: String): AuditableRestAction<Void> {
        Checks.notNull(token, "Token")
        val route = Route.Webhooks.DELETE_TOKEN_WEBHOOK.compile(id.toString(), token)
        return AuditableRestActionImpl(getJDA(), route)
    }

    @Nonnull
    override fun getManager(): WebhookManager = WebhookManagerImpl(this)

    // Webhook execution

    @Suppress("UNCHECKED_CAST")
    override fun sendRequest(): WebhookMessageCreateActionImpl<Message> {
        checkToken()
        val client = WebhookClient.createClient(api, getId(), token!!) as AbstractWebhookClient<Message>
        return client.sendRequest()
    }

    @Suppress("UNCHECKED_CAST")
    override fun editRequest(messageId: String): WebhookMessageEditActionImpl<Message> {
        checkToken()
        val client = WebhookClient.createClient(api, getId(), token!!) as AbstractWebhookClient<Message>
        return client.editRequest(messageId)
    }

    @Nonnull
    override fun deleteMessageById(messageId: String): WebhookMessageDeleteAction {
        checkToken()
        return WebhookClient.createClient(api, getId(), token!!).deleteMessageById(messageId)
    }

    @Nonnull
    override fun retrieveMessageById(messageId: String): WebhookMessageRetrieveAction {
        checkToken()
        return WebhookClient.createClient(api, getId(), token!!).retrieveMessageById(messageId)
    }

    private fun checkToken() {
        if (token == null) {
            throw UnsupportedOperationException("Cannot execute webhook without a token!")
        }
    }

    // -- Impl Setters --

    fun setOwner(
        member: Member?,
        user: User?,
    ): WebhookImpl {
        this.owner = member
        this.ownerUser = user
        return this
    }

    fun setToken(token: String?): WebhookImpl {
        this.token = token
        return this
    }

    fun setUser(user: User?): WebhookImpl {
        this.user = user
        return this
    }

    fun setSourceGuild(reference: GuildReference?): WebhookImpl {
        this.sourceGuild = reference
        return this
    }

    fun setSourceChannel(reference: ChannelReference?): WebhookImpl {
        this.sourceChannel = reference
        return this
    }

    // -- Object Overrides --

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is WebhookImpl) {
            return false
        }
        return other.id == id
    }

    override fun toString(): String = EntityString(this).setName(getName()).toString()
}
