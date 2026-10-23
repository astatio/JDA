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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.managers.Manager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.annotation.Nonnull

abstract class ManagerBase<M : Manager<M>> protected constructor(
    api: JDA,
    route: Route.CompiledRoute,
) : AuditableRestActionImpl<Void>(api, route),
    Manager<M> {
    // Kept protected to match the Java field shape; read and written by Java and Kotlin subclasses
    @JvmField
    protected var set: Long = 0

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): M = super.setCheck(checks) as M

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): M = super.timeout(timeout, unit) as M

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): M = super.deadline(timestamp) as M

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun reset(fields: Long): M {
        // 0101 = fields
        // 1010 = ~fields
        // 1100 = set
        // 1000 = set & ~fields
        set = set and fields.inv()
        return this as M
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST", "ReturnCount")
    override fun reset(
        @Nonnull vararg fields: Long,
    ): M {
        Checks.notNull(fields, "Fields")
        // trivial case
        if (fields.isEmpty()) {
            return this as M
        } else if (fields.size == 1) {
            return reset(fields[0])
        }

        // complex case
        var sum = fields[0]
        for (i in 1 until fields.size) {
            sum = sum or fields[i]
        }
        return reset(sum)
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun reset(): M {
        set = 0
        return this as M
    }

    override fun queue(
        success: Consumer<in Void>?,
        failure: Consumer<in Throwable>?,
    ) {
        if (shouldUpdate()) {
            super<AuditableRestActionImpl>.queue(success, failure)
        } else if (success != null) {
            @Suppress("UNCHECKED_CAST")
            (success as Consumer<Any?>).accept(null)
        } else {
            @Suppress("UNCHECKED_CAST")
            (RestActionImpl.getDefaultSuccess() as Consumer<Any?>).accept(null)
        }
    }

    @Suppress("ReturnCount", "CAST_NEVER_SUCCEEDS")
    override fun complete(shouldQueue: Boolean): Void {
        if (shouldUpdate()) {
            return super<AuditableRestActionImpl>.complete(shouldQueue)
        }
        return null as Void
    }

    override fun finalizeChecks(): BooleanSupplier? =
        if (enablePermissionChecks) BooleanSupplier { checkPermissions() } else super.finalizeChecks()

    protected fun shouldUpdate(): Boolean = set != 0L

    protected fun shouldUpdate(bit: Long): Boolean = set and bit != 0L

    protected fun <E : Any> withLock(
        obj: E,
        consumer: Consumer<in E>,
    ) {
        synchronized(obj) {
            consumer.accept(obj)
        }
    }

    protected open fun checkPermissions(): Boolean = true

    companion object {
        private var enablePermissionChecks = true

        @JvmStatic
        fun setPermissionChecksEnabled(enable: Boolean) {
            enablePermissionChecks = enable
        }

        @JvmStatic
        fun isPermissionChecksEnabled(): Boolean = enablePermissionChecks
    }
}
