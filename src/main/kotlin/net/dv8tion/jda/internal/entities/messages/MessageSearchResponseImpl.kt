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

package net.dv8tion.jda.internal.entities.messages

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.messages.MessageSearchResponse
import org.jetbrains.annotations.Unmodifiable
import java.time.Duration
import javax.annotation.Nonnull

class MessageSearchResponseImpl(
    private val value: Any,
) : MessageSearchResponse {
    override fun isNotReady(): Boolean = value is MessageSearchResponse.NotReady

    @Nonnull
    override fun asNotReady(): MessageSearchResponse.NotReady {
        if (value is MessageSearchResponse.NotReady) {
            return value
        }
        throw IllegalStateException("The message search has succeeded")
    }

    @Nonnull
    override fun asResults(): MessageSearchResponse.Results {
        if (value is MessageSearchResponse.Results) {
            return value
        }
        throw IllegalStateException("The message search has succeeded")
    }

    class NotReadyImpl(
        private val documentsIndexed: Int,
        private val retryAfter: Int,
    ) : MessageSearchResponse.NotReady {
        override fun getDocumentsIndexed(): Int = documentsIndexed

        @Nonnull
        override fun getRetryAfter(): Duration = Duration.ofSeconds(retryAfter.toLong())
    }

    class ResultsImpl(
        private val messages: List<Message>,
        private val doingDeepHistoricalIndex: Boolean,
        private val totalResults: Int,
    ) : MessageSearchResponse.Results {
        @Nonnull
        override fun getMessages(): @Unmodifiable List<Message> = messages

        override fun isDoingDeepHistoricalIndex(): Boolean = doingDeepHistoricalIndex

        override fun getTotalResults(): Int = totalResults
    }
}
