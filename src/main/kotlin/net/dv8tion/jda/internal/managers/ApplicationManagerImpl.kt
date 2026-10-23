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
import net.dv8tion.jda.api.entities.ApplicationInfo
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.managers.ApplicationManager
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.LinkedHashSet
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * When implement new fields, update also [reset], [reset] and [finalizeData].
 */
class ApplicationManagerImpl(
    jda: JDA,
) : ManagerBase<ApplicationManager>(jda, Route.Applications.EDIT_BOT_APPLICATION.compile()),
    ApplicationManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var description: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var icon: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var coverImage: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var tags: MutableSet<String>? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var interactionsEndpointUrl: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var customInstallUrl: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var installParams: ApplicationManager.IntegrationTypeConfig? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var integrationTypeConfig: MutableMap<IntegrationType, ApplicationManager.IntegrationTypeConfig>? = null

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): ApplicationManagerImpl {
        super.reset(fields)

        if (fields and ApplicationManager.DESCRIPTION == ApplicationManager.DESCRIPTION) {
            description = null
        }
        if (fields and ApplicationManager.ICON == ApplicationManager.ICON) {
            icon = null
        }
        if (fields and ApplicationManager.COVER_IMAGE == ApplicationManager.COVER_IMAGE) {
            coverImage = null
        }

        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): ApplicationManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): ApplicationManagerImpl {
        super.reset()
        return this
    }

    @Nonnull
    override fun setDescription(
        @Nonnull description: String,
    ): ApplicationManager {
        Checks.notNull(description, "Description")
        Checks.notLonger(description.trim(), ApplicationInfo.MAX_DESCRIPTION_LENGTH, "Description")
        this.description = description.trim()
        set = set or ApplicationManager.DESCRIPTION
        return this
    }

    @Nonnull
    override fun setIcon(
        @Nullable icon: Icon?,
    ): ApplicationManager {
        this.icon = icon
        set = set or ApplicationManager.ICON
        return this
    }

    @Nonnull
    override fun setCoverImage(
        @Nullable coverImage: Icon?,
    ): ApplicationManager {
        this.coverImage = coverImage
        set = set or ApplicationManager.COVER_IMAGE
        return this
    }

    @Nonnull
    override fun setTags(
        @Nonnull tags: Collection<String>,
    ): ApplicationManager {
        Checks.noneNull(tags, "Tags")
        val tagSet = LinkedHashSet<String>()
        for (tag in tags) {
            Checks.notLonger(tag.trim(), ApplicationInfo.MAX_TAG_LENGTH, "Tag")
            Checks.notBlank(tag, "Tag")
            tagSet.add(tag.trim())
        }

        this.tags = tagSet
        set = set or ApplicationManager.TAGS
        return this
    }

    @Nonnull
    override fun setInteractionsEndpointUrl(
        @Nullable interactionsEndpointUrl: String?,
    ): ApplicationManager {
        if (interactionsEndpointUrl != null) {
            checkUrl(interactionsEndpointUrl)
        }
        this.interactionsEndpointUrl = interactionsEndpointUrl
        set = set or ApplicationManager.INTERACTIONS_ENDPOINT_URL
        return this
    }

    @Nonnull
    override fun setCustomInstallUrl(
        @Nullable customInstallUrl: String?,
    ): ApplicationManager {
        if (customInstallUrl != null) {
            checkUrl(customInstallUrl)
        }
        this.customInstallUrl = customInstallUrl
        set = set or ApplicationManager.CUSTOM_INSTALL_URL
        return this
    }

    @Nonnull
    override fun setInstallParams(
        @Nullable installParams: ApplicationManager.IntegrationTypeConfig?,
    ): ApplicationManager {
        this.installParams = installParams
        set = set or ApplicationManager.INSTALL_PARAMS
        return this
    }

    @Nonnull
    override fun setIntegrationTypeConfig(
        @Nullable config: MutableMap<IntegrationType, ApplicationManager.IntegrationTypeConfig>?,
    ): ApplicationManager {
        if (config != null) {
            Checks.noneNull(config.keys, "IntegrationTypeConfig")
            Checks.noneNull(config.values, "IntegrationTypeConfig")
            Checks.check(
                !config.keys.contains(IntegrationType.UNKNOWN),
                "IntegrationTypeConfig must not be set for UNKNOWN",
            )
        }

        integrationTypeConfig = config
        set = set or ApplicationManager.INTEGRATION_TYPES_CONFIG
        return this
    }

    override fun finalizeData(): RequestBody {
        val body = DataObject.empty()

        if (shouldUpdate(ApplicationManager.DESCRIPTION)) {
            body.put("description", description)
        }
        if (shouldUpdate(ApplicationManager.ICON)) {
            body.put("icon", icon?.getEncoding())
        }
        if (shouldUpdate(ApplicationManager.COVER_IMAGE)) {
            body.put("cover_image", coverImage?.getEncoding())
        }
        if (shouldUpdate(ApplicationManager.TAGS)) {
            body.put("tags", DataArray.fromCollection(tags!!))
        }
        if (shouldUpdate(ApplicationManager.INTERACTIONS_ENDPOINT_URL)) {
            body.put("interactions_endpoint_url", interactionsEndpointUrl)
        }
        if (shouldUpdate(ApplicationManager.CUSTOM_INSTALL_URL)) {
            body.put("custom_install_url", customInstallUrl)
        }
        if (shouldUpdate(ApplicationManager.INSTALL_PARAMS)) {
            body.put("install_params", installParams)
        }
        if (shouldUpdate(ApplicationManager.INTEGRATION_TYPES_CONFIG)) {
            val config = DataObject.empty()
            integrationTypeConfig!!.forEach { (key, value) ->
                config.put(key.name, DataObject.empty().put("oauth2_install_params", value))
            }
            body.put("integration_type_config", config)
        }

        reset()
        return getRequestBody(body)
    }

    override fun handleSuccess(
        response: Response,
        request: Request<Void>,
    ) {
        request.onSuccess(null)
    }

    // Kept protected to match the Java member shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    protected fun checkUrl(url: String) {
        Checks.notLonger(url, ApplicationInfo.MAX_URL_LENGTH, "URL")
        Checks.notBlank(url, "URL")
        Checks.noWhitespace(url, "URL")
    }
}
