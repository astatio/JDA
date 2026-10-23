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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.WebhookManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

class WebhookManagerImpl(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val webhook: Webhook,
) : ManagerBase<WebhookManager>(
        webhook.getJDA(),
        Route.Webhooks.MODIFY_WEBHOOK.compile(webhook.getId()),
    ),
    WebhookManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var name: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var channel: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var avatar: Icon? = null

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    @Nonnull
    override fun getWebhook(): Webhook = webhook

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): WebhookManagerImpl {
        super.reset(fields)
        if (fields and WebhookManager.NAME == WebhookManager.NAME) {
            name = null
        }
        if (fields and WebhookManager.CHANNEL == WebhookManager.CHANNEL) {
            channel = null
        }
        if (fields and WebhookManager.AVATAR == WebhookManager.AVATAR) {
            avatar = null
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): WebhookManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): WebhookManagerImpl {
        super.reset()
        name = null
        channel = null
        avatar = null
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): WebhookManagerImpl {
        Checks.notBlank(name, "Name")
        this.name = name
        set = set or WebhookManager.NAME
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setAvatar(icon: Icon?): WebhookManagerImpl {
        avatar = icon
        set = set or WebhookManager.AVATAR
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setChannel(
        @Nonnull channel: TextChannel,
    ): WebhookManagerImpl {
        Checks.notNull(channel, "Channel")
        Checks.check(channel.getGuild() == getGuild(), "Channel is not from the same guild")
        this.channel = channel.getId()
        set = set or WebhookManager.CHANNEL
        return this
    }

    override fun finalizeData(): RequestBody {
        val data = DataObject.empty()
        if (shouldUpdate(WebhookManager.NAME)) {
            data.put("name", name)
        }
        if (shouldUpdate(WebhookManager.CHANNEL)) {
            data.put("channel_id", channel)
        }
        if (shouldUpdate(WebhookManager.AVATAR)) {
            data.put("avatar", avatar?.getEncoding())
        }

        return getRequestBody(data)
    }

    override fun checkPermissions(): Boolean {
        val selfMember = getGuild().getSelfMember()
        val guildChannel = getChannel() as GuildChannel
        Checks.checkAccess(selfMember, guildChannel)
        if (!selfMember.hasPermission(guildChannel, Permission.MANAGE_WEBHOOKS)) {
            throw InsufficientPermissionException(guildChannel, Permission.MANAGE_WEBHOOKS)
        }
        return super.checkPermissions()
    }
}
