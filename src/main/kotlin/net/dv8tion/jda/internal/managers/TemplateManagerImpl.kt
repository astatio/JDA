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

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.templates.Template
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.TemplateManager
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

private const val NAME_MAX_LENGTH = 100
private const val DESCRIPTION_MAX_LENGTH = 120

class TemplateManagerImpl(
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField protected val template: Template,
) : ManagerBase<TemplateManager>(
        template.getJDA(),
        Route.Templates.MODIFY_TEMPLATE.compile(template.getGuild().getId(), template.getCode()),
    ),
    TemplateManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var name: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var description: String? = null

    init {
        if (isPermissionChecksEnabled()) {
            checkPermissions()
        }
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): TemplateManagerImpl {
        super.reset(fields)
        if (fields and TemplateManager.NAME == TemplateManager.NAME) {
            this.name = null
        }
        if (fields and TemplateManager.DESCRIPTION == TemplateManager.DESCRIPTION) {
            this.description = null
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): TemplateManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): TemplateManagerImpl {
        super.reset()
        this.name = null
        this.description = null
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): TemplateManagerImpl {
        Checks.notEmpty(name, "Name")
        Checks.notLonger(name, NAME_MAX_LENGTH, "Name")
        this.name = name
        set = set or TemplateManager.NAME
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setDescription(
        @Nullable description: String?,
    ): TemplateManagerImpl {
        if (description != null) {
            Checks.notLonger(name, DESCRIPTION_MAX_LENGTH, "Description")
        }
        this.description = description
        set = set or TemplateManager.DESCRIPTION
        return this
    }

    override fun finalizeData(): RequestBody {
        val body = DataObject.empty()
        if (shouldUpdate(TemplateManager.NAME)) {
            body.put("name", name)
        }
        if (shouldUpdate(TemplateManager.DESCRIPTION)) {
            body.put("description", name)
        }

        reset()
        return getRequestBody(body)
    }

    override fun checkPermissions(): Boolean {
        val guild: Guild? = api.getGuildById(template.getGuild().getIdLong())

        if (guild == null) {
            return true
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            throw InsufficientPermissionException(guild, Permission.MANAGE_SERVER)
        }
        return super.checkPermissions()
    }
}
