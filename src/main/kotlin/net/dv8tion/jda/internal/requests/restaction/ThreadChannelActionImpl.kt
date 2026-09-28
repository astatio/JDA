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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.ISlowmodeChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.ChannelUtil
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

open class ThreadChannelActionImpl private constructor(
    @JvmField protected val guild: Guild,
    @JvmField protected val channel: GuildChannel,
    @JvmField protected val type: ChannelType,
    @JvmField protected val parentMessageId: String?,
    name: String,
    route: Route.CompiledRoute,
) : AuditableRestActionImpl<ThreadChannel>(channel.jda, route),
    ThreadChannelAction {
    @JvmField
    protected var name: String = name

    @JvmField
    protected var autoArchiveDuration: ThreadChannel.AutoArchiveDuration? = null

    @JvmField
    protected var slowmode: Int? = null

    @JvmField
    protected var invitable: Boolean? = null

    constructor(channel: GuildChannel, name: String, type: ChannelType) : this(
        channel.guild,
        channel,
        type,
        null,
        name,
        Route.Channels.CREATE_THREAD.compile(channel.id),
    )

    constructor(channel: GuildChannel, name: String, parentMessageId: String) : this(
        channel.guild,
        channel,
        if (channel.type == ChannelType.TEXT) ChannelType.GUILD_PUBLIC_THREAD else ChannelType.GUILD_NEWS_THREAD,
        parentMessageId,
        name,
        Route.Channels.CREATE_THREAD_FROM_MESSAGE.compile(channel.id, parentMessageId),
    )

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun reason(reason: String?): ThreadChannelActionImpl = super.reason(reason) as ThreadChannelActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): ThreadChannelActionImpl = super.setCheck(checks) as ThreadChannelActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): ThreadChannelActionImpl = super<AuditableRestActionImpl>.timeout(timeout, unit) as ThreadChannelActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): ThreadChannelActionImpl =
        super<AuditableRestActionImpl>.deadline(timestamp) as ThreadChannelActionImpl

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nonnull
    override fun getType(): ChannelType = type

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): ThreadChannelActionImpl {
        Checks.notEmpty(name, "Name")
        Checks.notLonger(name, Channel.MAX_NAME_LENGTH, "Name")
        this.name = name
        return this
    }

    @Nonnull
    override fun setAutoArchiveDuration(
        @Nonnull autoArchiveDuration: ThreadChannel.AutoArchiveDuration,
    ): ThreadChannelAction {
        Checks.notNull(autoArchiveDuration, "autoArchiveDuration")
        this.autoArchiveDuration = autoArchiveDuration
        return this
    }

    @Nonnull
    override fun setSlowmode(slowmode: Int): ThreadChannelAction {
        Checks.checkSupportedChannelTypes(ChannelUtil.SLOWMODE_SUPPORTED, type, "slowmode")
        Checks.check(
            slowmode <= ISlowmodeChannel.MAX_SLOWMODE && slowmode >= 0,
            "Slowmode per user must be between 0 and %d (seconds)!",
            ISlowmodeChannel.MAX_SLOWMODE,
        )
        if (!guild.selfMember.hasPermission(channel, Permission.MANAGE_THREADS)) {
            throw InsufficientPermissionException(
                channel,
                Permission.MANAGE_THREADS,
                "You must have Permission.MANAGE_THREADS on the parent channel to set a slowmode!",
            )
        }
        this.slowmode = slowmode
        return this
    }

    @Nonnull
    override fun setInvitable(invitable: Boolean): ThreadChannelAction {
        if (type != ChannelType.GUILD_PRIVATE_THREAD) {
            throw UnsupportedOperationException("Can only set invitable on private threads")
        }

        this.invitable = invitable
        return this
    }

    override fun finalizeData(): RequestBody? {
        val json = DataObject.empty()

        json.put("name", name)

        // The type is selected by discord itself if we are using a parent message,
        // so don't send it.
        if (parentMessageId == null) {
            json.put("type", type.id)
        }

        if (autoArchiveDuration != null) {
            json.put("auto_archive_duration", autoArchiveDuration!!.minutes)
        }
        if (slowmode != null) {
            json.put("rate_limit_per_user", slowmode)
        }
        if (invitable != null) {
            json.put("invitable", invitable)
        }

        return getRequestBody(json)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<ThreadChannel>,
    ) {
        val channel = api.entityBuilder.createThreadChannel(response.getObject(), guild.idLong)
        request.onSuccess(channel)
    }
}
