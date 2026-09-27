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
import net.dv8tion.jda.api.entities.Entitlement
import net.dv8tion.jda.api.entities.Entitlement.EntitlementType
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.internal.requests.CompletedRestAction
import net.dv8tion.jda.internal.requests.RestActionImpl
import java.time.OffsetDateTime
import javax.annotation.Nonnull
import javax.annotation.Nullable

class EntitlementImpl(
    private val api: JDA,
    private val id: Long,
    private val skuId: Long,
    private val applicationId: Long,
    private val userId: Long,
    private val guildId: Long,
    private val type: EntitlementType,
    private val deleted: Boolean,
    private val startsAt: OffsetDateTime?,
    private val endsAt: OffsetDateTime?,
    private val consumed: Boolean,
) : Entitlement {
    override fun getIdLong(): Long = id

    override fun getSkuIdLong(): Long = skuId

    override fun getApplicationIdLong(): Long = applicationId

    override fun getUserIdLong(): Long = userId

    override fun getGuildIdLong(): Long = guildId

    @Nonnull
    override fun getType(): EntitlementType = type

    override fun isDeleted(): Boolean = deleted

    @Nullable
    override fun getTimeStarting(): OffsetDateTime? = startsAt

    @Nullable
    override fun getTimeEnding(): OffsetDateTime? = endsAt

    override fun isConsumed(): Boolean = consumed

    @Nonnull
    override fun consume(): RestAction<Void> {
        if (consumed) {
            return CompletedRestAction(api, null)
        }

        val route = Route.Applications.CONSUME_ENTITLEMENT.compile(getApplicationId(), getId())
        return RestActionImpl(api, route)
    }
}
