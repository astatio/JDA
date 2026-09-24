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

package net.dv8tion.jda.internal.utils.requestbody

import okhttp3.MediaType
import okio.BufferedSink
import okio.Source
import java.io.IOException
import java.util.function.Supplier

class DataSupplierBody(
    type: MediaType?,
    private val streamSupply: Supplier<out Source>,
) : TypedBody<DataSupplierBody>(type) {
    override fun withType(newType: MediaType): DataSupplierBody {
        if (type == newType) {
            return this
        }
        return DataSupplierBody(newType, streamSupply)
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        synchronized(streamSupply) {
            streamSupply.get().use { stream: Source ->
                sink.writeAll(stream)
            }
        }
    }
}
