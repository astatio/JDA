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

package net.dv8tion.jda.internal.requests.restaction.operator

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.exceptions.ContextException
import net.dv8tion.jda.api.requests.RestAction
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.annotation.Nonnull
import javax.annotation.Nullable

abstract class RestActionOperator<I, O>(
    @JvmField protected val action: RestAction<I>,
) : RestAction<O> {
    @JvmField protected var check: BooleanSupplier? = null

    @JvmField protected var deadline: Long = -1

    companion object {
        @JvmStatic
        internal fun <E> doSuccess(
            callback: Consumer<in E>?,
            value: E,
        ) {
            if (callback == null) {
                RestAction.getDefaultSuccess().accept(value)
            } else {
                callback.accept(value)
            }
        }

        @JvmStatic
        internal fun doFailure(
            callback: Consumer<in Throwable>?,
            throwable: Throwable,
        ) {
            if (callback == null) {
                RestAction.getDefaultFailure().accept(throwable)
            } else {
                callback.accept(throwable)
            }
            if (throwable is Error) {
                throw throwable
            }
        }
    }

    // Faithful port of the Java original: the success callback is wrapped so any throwable
    // it raises is routed through the failure handler.
    @Suppress("TooGenericExceptionCaught")
    protected fun handle(
        action: RestAction<I>,
        failure: Consumer<in Throwable>?,
        success: Consumer<in I>?,
    ) {
        val catcher = contextWrap(failure)
        action.queue(
            { result ->
                try {
                    success?.accept(result)
                } catch (ex: Throwable) {
                    doFailure(catcher, ex)
                }
            },
            catcher,
        )
    }

    @Nonnull
    override fun getJDA(): JDA = action.jda

    @Nonnull
    override fun setCheck(
        @Nullable checks: BooleanSupplier?,
    ): RestAction<O> {
        this.check = checks
        action.setCheck(checks)
        return this
    }

    @Nullable
    override fun getCheck(): BooleanSupplier? = action.check

    @Nonnull
    override fun deadline(timestamp: Long): RestAction<O> {
        this.deadline = timestamp
        action.deadline(timestamp)
        return this
    }

    @Nullable
    protected fun <T> applyContext(action: RestAction<T>?): RestAction<T>? {
        if (action == null) {
            return null
        }
        check?.let { action.setCheck(it) }
        if (deadline >= 0) {
            action.deadline(deadline)
        }
        return action
    }

    @Nullable
    protected fun contextWrap(
        @Nullable callback: Consumer<in Throwable>?,
    ): Consumer<in Throwable>? =
        if (callback is ContextException.ContextConsumer) {
            callback
        } else if (RestAction.isPassContext()) {
            val acceptor: Consumer<in Throwable> = callback ?: RestAction.getDefaultFailure()
            ContextException.here(acceptor)
        } else {
            callback
        }
}
