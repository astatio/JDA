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
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.InviteAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

class InviteActionImpl(
    api: JDA,
    channelId: String,
) : AuditableRestActionImpl<Invite>(api, Route.Invites.CREATE_INVITE.compile(channelId)),
    InviteAction {
    private var maxAge: Int? = null
    private var maxUses: Int? = null
    private var temporary: Boolean? = null
    private var unique: Boolean? = null
    private var targetApplication: Long? = null
    private var targetUser: Long? = null
    private var targetType: Invite.TargetType? = null

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): InviteActionImpl = super.setCheck(checks) as InviteActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): InviteActionImpl = super.timeout(timeout, unit) as InviteActionImpl

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): InviteActionImpl = super.deadline(timestamp) as InviteActionImpl

    @Nonnull
    @CheckReturnValue
    override fun setMaxAge(maxAge: Int?): InviteActionImpl {
        if (maxAge != null) {
            Checks.notNegative(maxAge, "maxAge")
        }

        this.maxAge = maxAge
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setMaxAge(
        maxAge: Long?,
        @Nonnull timeUnit: TimeUnit,
    ): InviteActionImpl {
        if (maxAge == null) {
            return this.setMaxAge(null)
        }

        Checks.notNegative(maxAge, "maxAge")
        Checks.notNull(timeUnit, "timeUnit")

        return this.setMaxAge(Math.toIntExact(timeUnit.toSeconds(maxAge)))
    }

    @Nonnull
    @CheckReturnValue
    override fun setMaxUses(maxUses: Int?): InviteActionImpl {
        if (maxUses != null) {
            Checks.notNegative(maxUses, "maxUses")
        }

        this.maxUses = maxUses
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setTemporary(temporary: Boolean?): InviteActionImpl {
        this.temporary = temporary
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setUnique(unique: Boolean?): InviteActionImpl {
        this.unique = unique
        return this
    }

    @Nonnull
    override fun setTargetApplication(applicationId: Long): InviteAction {
        if (applicationId == 0L) {
            this.targetType = null
            this.targetApplication = null
            return this
        }

        this.targetType = Invite.TargetType.EMBEDDED_APPLICATION
        this.targetApplication = applicationId
        return this
    }

    @Nonnull
    override fun setTargetStream(userId: Long): InviteAction {
        if (userId == 0L) {
            this.targetType = null
            this.targetUser = null
            return this
        }

        this.targetType = Invite.TargetType.STREAM
        this.targetUser = userId
        return this
    }

    override fun finalizeData(): RequestBody? {
        val json = DataObject.empty()

        if (this.maxAge != null) {
            json.put("max_age", this.maxAge)
        }
        if (this.maxUses != null) {
            json.put("max_uses", this.maxUses)
        }
        if (this.temporary != null) {
            json.put("temporary", this.temporary)
        }
        if (this.unique != null) {
            json.put("unique", this.unique)
        }
        if (this.targetType != null) {
            json.put("target_type", targetType!!.id)
        }
        if (this.targetUser != null) {
            json.put("target_user_id", targetUser)
        }
        if (this.targetApplication != null) {
            json.put("target_application_id", targetApplication)
        }

        return getRequestBody(json)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<Invite>,
    ) {
        request.onSuccess(api.entityBuilder.createInvite(response.getObject()))
    }
}
