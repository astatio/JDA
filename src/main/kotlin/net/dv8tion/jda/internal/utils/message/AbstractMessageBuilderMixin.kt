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
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.AttachedFile
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.AbstractMessageBuilder
import net.dv8tion.jda.api.utils.messages.MessageRequest
import java.util.EnumSet

@Suppress("UNCHECKED_CAST")
interface AbstractMessageBuilderMixin<R : MessageRequest<R>, B : AbstractMessageBuilder<*, B>> : MessageRequest<R> {
    fun getBuilder(): B

    override fun setContent(content: String?): R {
        getBuilder().setContent(content)
        return this as R
    }

    override fun getContent(): String = getBuilder().content

    override fun setEmbeds(embeds: Collection<MessageEmbed>): R {
        getBuilder().setEmbeds(embeds)
        return this as R
    }

    override fun getEmbeds(): List<MessageEmbed> = getBuilder().embeds

    override fun setComponents(components: Collection<MessageTopLevelComponent>): R {
        getBuilder().setComponents(components)
        return this as R
    }

    override fun useComponentsV2(use: Boolean): R {
        getBuilder().useComponentsV2(use)
        return this as R
    }

    override fun getComponents(): List<MessageTopLevelComponentUnion> = getBuilder().components

    override fun isUsingComponentsV2(): Boolean = getBuilder().isUsingComponentsV2

    override fun setSuppressEmbeds(suppress: Boolean): R {
        getBuilder().setSuppressEmbeds(suppress)
        return this as R
    }

    override fun isSuppressEmbeds(): Boolean = getBuilder().isSuppressEmbeds

    override fun setFiles(files: Collection<FileUpload>?): R {
        getBuilder().setFiles(files)
        return this as R
    }

    override fun getAttachments(): List<@JvmWildcard AttachedFile> = getBuilder().attachments

    override fun mentionRepliedUser(mention: Boolean): R {
        getBuilder().mentionRepliedUser(mention)
        return this as R
    }

    override fun setAllowedMentions(allowedMentions: @JvmSuppressWildcards Collection<Message.MentionType>?): R {
        getBuilder().setAllowedMentions(allowedMentions)
        return this as R
    }

    override fun mention(mentions: Collection<IMentionable>): R {
        getBuilder().mention(mentions)
        return this as R
    }

    override fun mentionUsers(userIds: Collection<String>): R {
        getBuilder().mentionUsers(userIds)
        return this as R
    }

    override fun mentionRoles(roleIds: Collection<String>): R {
        getBuilder().mentionRoles(roleIds)
        return this as R
    }

    override fun getMentionedUsers(): Set<String> = getBuilder().mentionedUsers

    override fun getMentionedRoles(): Set<String> = getBuilder().mentionedRoles

    override fun getAllowedMentions(): EnumSet<Message.MentionType> = getBuilder().allowedMentions

    override fun isMentionRepliedUser(): Boolean = getBuilder().isMentionRepliedUser
}
