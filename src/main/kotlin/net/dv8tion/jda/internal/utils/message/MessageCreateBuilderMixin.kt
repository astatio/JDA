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

package net.dv8tion.jda.internal.utils.message

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest
import net.dv8tion.jda.api.utils.messages.MessagePollData

@Suppress("UNCHECKED_CAST")
interface MessageCreateBuilderMixin<R : MessageCreateRequest<R>> :
    AbstractMessageBuilderMixin<R, MessageCreateBuilder>,
    MessageCreateRequest<R> {
    override fun addContent(content: String): R {
        getBuilder().addContent(content)
        return this as R
    }

    override fun addEmbeds(embeds: Collection<MessageEmbed>): R {
        getBuilder().addEmbeds(embeds)
        return this as R
    }

    override fun addComponents(components: Collection<MessageTopLevelComponent>): R {
        getBuilder().addComponents(components)
        return this as R
    }

    override fun addFiles(files: Collection<FileUpload>): R {
        getBuilder().addFiles(files)
        return this as R
    }

    override fun getPoll(): MessagePollData? = getBuilder().poll

    override fun setPoll(poll: MessagePollData?): R {
        getBuilder().setPoll(poll)
        return this as R
    }

    override fun setTTS(tts: Boolean): R {
        getBuilder().setTTS(tts)
        return this as R
    }

    override fun setFiles(files: Collection<FileUpload>?): R {
        getBuilder().setFiles(files)
        return this as R
    }

    override fun getAttachments(): List<FileUpload> = getBuilder().attachments

    override fun setSuppressedNotifications(suppressed: Boolean): R {
        getBuilder().setSuppressedNotifications(suppressed)
        return this as R
    }

    override fun setVoiceMessage(voiceMessage: Boolean): R {
        getBuilder().setVoiceMessage(voiceMessage)
        return this as R
    }
}
