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
import net.dv8tion.jda.api.entities.Entitlement
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.TestEntitlementCreateAction
import net.dv8tion.jda.api.requests.restaction.TestEntitlementCreateAction.OwnerType
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.Nonnull

class TestEntitlementCreateActionImpl(
    api: JDA,
    private var skuId: Long,
    private var ownerId: Long,
    private var type: OwnerType,
) : RestActionImpl<Entitlement>(
        api,
        Route.Applications.CREATE_TEST_ENTITLEMENT.compile(api.selfUser.applicationId),
    ),
    TestEntitlementCreateAction {
    @Nonnull
    override fun setSkuId(skuId: Long): TestEntitlementCreateAction {
        this.skuId = skuId
        return this
    }

    @Nonnull
    override fun setOwnerId(ownerId: Long): TestEntitlementCreateAction {
        this.ownerId = ownerId
        return this
    }

    @Nonnull
    override fun setOwnerType(
        @Nonnull type: OwnerType,
    ): TestEntitlementCreateAction {
        Checks.notNull(type, "type")

        this.type = type
        return this
    }

    override fun handleSuccess(
        response: Response,
        request: Request<Entitlement>,
    ) {
        val json = response.getObject()
        request.onSuccess(api.entityBuilder.createEntitlement(json))
    }

    override fun finalizeData(): RequestBody? {
        val json = DataObject.empty()
        json.put("sku_id", skuId)
        json.put("owner_id", ownerId)
        json.put("owner_type", type.key)

        return getRequestBody(json)
    }
}
