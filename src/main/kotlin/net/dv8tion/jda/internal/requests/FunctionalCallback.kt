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

package net.dv8tion.jda.internal.requests

import net.dv8tion.jda.api.utils.IOBiConsumer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.function.BiConsumer
import javax.annotation.Nonnull

class FunctionalCallback(
    private val failure: BiConsumer<Call, IOException>?,
    private val success: IOBiConsumer<Call, Response>?,
) : Callback {
    override fun onFailure(
        @Nonnull call: Call,
        @Nonnull e: IOException,
    ) {
        failure?.accept(call, e)
    }

    @Throws(IOException::class)
    override fun onResponse(
        @Nonnull call: Call,
        @Nonnull response: Response,
    ) {
        success?.accept(call, response)
    }

    class Builder {
        private var failure: BiConsumer<Call, IOException>? = null
        private var success: IOBiConsumer<Call, Response>? = null

        fun onSuccess(callback: IOBiConsumer<Call, Response>): Builder {
            this.success = callback
            return this
        }

        fun onFailure(callback: BiConsumer<Call, IOException>): Builder {
            this.failure = callback
            return this
        }

        fun build(): FunctionalCallback = FunctionalCallback(failure, success)
    }

    companion object {
        @JvmStatic
        fun onSuccess(callback: IOBiConsumer<Call, Response>): Builder = Builder().onSuccess(callback)

        @JvmStatic
        fun onFailure(callback: BiConsumer<Call, IOException>): Builder = Builder().onFailure(callback)
    }
}
