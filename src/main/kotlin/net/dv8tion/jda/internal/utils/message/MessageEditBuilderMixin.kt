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

import net.dv8tion.jda.api.utils.AttachedFile
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditData
import net.dv8tion.jda.api.utils.messages.MessageEditRequest

@Suppress("UNCHECKED_CAST")
interface MessageEditBuilderMixin<R : MessageEditRequest<R>> :
    AbstractMessageBuilderMixin<R, MessageEditBuilder>,
    MessageEditRequest<R> {
    override fun setAttachments(attachments: Collection<AttachedFile>?): R {
        getBuilder().setAttachments(attachments)
        return this as R
    }

    override fun setReplace(isReplace: Boolean): R {
        getBuilder().setReplace(isReplace)
        return this as R
    }

    override fun setFiles(files: Collection<FileUpload>?): R {
        getBuilder().setFiles(files)
        return this as R
    }

    override fun applyData(data: MessageEditData): R {
        getBuilder().applyData(data)
        return this as R
    }

    override fun isReplace(): Boolean = getBuilder().isReplace
}
