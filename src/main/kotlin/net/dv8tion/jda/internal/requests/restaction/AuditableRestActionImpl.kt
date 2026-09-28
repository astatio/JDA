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
import net.dv8tion.jda.api.audit.ThreadLocalReason
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.EncodingUtil
import okhttp3.RequestBody
import org.apache.commons.collections4.map.CaseInsensitiveMap
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import java.util.function.BooleanSupplier
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

open class AuditableRestActionImpl<T> :
    RestActionImpl<T>,
    AuditableRestAction<T> {
    @JvmField
    protected var reason: String? = null

    constructor(api: JDA, route: Route.CompiledRoute) : super(api, route)

    constructor(api: JDA, route: Route.CompiledRoute, data: RequestBody) : super(api, route, data)

    constructor(api: JDA, route: Route.CompiledRoute, data: DataObject) : super(api, route, data)

    constructor(api: JDA, route: Route.CompiledRoute, handler: BiFunction<Response, Request<T>, T>) :
        super(api, route, handler)

    constructor(api: JDA, route: Route.CompiledRoute, data: DataObject, handler: BiFunction<Response, Request<T>, T>) :
        super(api, route, data, handler)

    constructor(api: JDA, route: Route.CompiledRoute, data: RequestBody, handler: BiFunction<Response, Request<T>, T>) :
        super(api, route, data, handler)

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): AuditableRestAction<T> = super.setCheck(checks) as AuditableRestAction<T>

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): AuditableRestAction<T> = super<RestActionImpl>.timeout(timeout, unit) as AuditableRestAction<T>

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): AuditableRestAction<T> = super<RestActionImpl>.deadline(timestamp) as AuditableRestAction<T>

    @Nonnull
    @CheckReturnValue
    override fun reason(
        @Nullable reason: String?,
    ): AuditableRestActionImpl<T> {
        this.reason = reason
        return this
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    override fun finalizeHeaders(): CaseInsensitiveMap<String, String>? {
        val headers = super.finalizeHeaders()

        if (reason == null || reason!!.isEmpty()) {
            val localReason = ThreadLocalReason.getCurrent()
            if (localReason == null || localReason.isEmpty()) {
                return headers
            } else {
                return generateHeaders(headers, localReason)
            }
        }

        return generateHeaders(headers, reason!!)
    }

    @Nonnull
    private fun generateHeaders(
        headers: CaseInsensitiveMap<String, String>?,
        reason: String,
    ): CaseInsensitiveMap<String, String> {
        val headers = headers ?: CaseInsensitiveMap()

        headers["X-Audit-Log-Reason"] = uriEncode(reason)
        return headers
    }

    private fun uriEncode(input: String): String {
        val formEncode = EncodingUtil.encodeUTF8(input)
        return formEncode.replace('+', ' ')
    }
}
