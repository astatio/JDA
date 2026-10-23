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

import net.dv8tion.jda.api.entities.messages.MessagePoll
import java.time.OffsetDateTime
import javax.annotation.Nonnull

class MessagePollImpl(
    private val layout: MessagePoll.LayoutType,
    private val question: MessagePoll.Question,
    private val answers: List<MessagePoll.Answer>,
    private val expiresAt: OffsetDateTime,
    private val isMultiAnswer: Boolean,
    private val isFinalizedVotes: Boolean,
) : MessagePoll {
    @Nonnull
    override fun getLayout(): MessagePoll.LayoutType = layout

    @Nonnull
    override fun getQuestion(): MessagePoll.Question = question

    @Nonnull
    override fun getAnswers(): List<MessagePoll.Answer> = answers

    @Nonnull
    override fun getTimeExpiresAt(): OffsetDateTime = expiresAt

    override fun isMultiAnswer(): Boolean = isMultiAnswer

    override fun isFinalizedVotes(): Boolean = isFinalizedVotes
}
