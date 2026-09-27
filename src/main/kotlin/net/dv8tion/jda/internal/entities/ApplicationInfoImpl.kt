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

package net.dv8tion.jda.internal.entities

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ApplicationInfo
import net.dv8tion.jda.api.entities.ApplicationInfo.InstallParameters
import net.dv8tion.jda.api.entities.ApplicationInfo.IntegrationTypeConfiguration
import net.dv8tion.jda.api.entities.ApplicationTeam
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.utils.ImageFormat
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Collections
import java.util.EnumSet
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ApplicationInfoImpl(
    private val api: JDA,
    private val description: String,
    private val doesBotRequireCodeGrant: Boolean,
    private val iconId: String,
    private val id: Long,
    private val flags: Long,
    private val isBotPublic: Boolean,
    private val name: String,
    private val termsOfServiceUrl: String,
    private val privacyPolicyUrl: String,
    private val owner: User,
    private val team: ApplicationTeam,
    tags: List<String>,
    redirectUris: List<String>,
    private val interactionsEndpointUrl: String?,
    private val roleConnectionsVerificationUrl: String?,
    private val customAuthUrl: String?,
    private val defaultAuthUrlPerms: Long,
    defaultAuthUrlScopes: List<String>,
    private val approxUserInstallCount: Long,
    private val integrationTypesConfig: Map<IntegrationType, IntegrationTypeConfiguration>,
) : ApplicationInfo {
    private val tags: List<String> = Collections.unmodifiableList(tags)
    private val redirectUris: List<String> = Collections.unmodifiableList(redirectUris)
    private val defaultAuthUrlScopes: List<String> = Collections.unmodifiableList(defaultAuthUrlScopes)

    private var scopes: String = "bot"

    override fun doesBotRequireCodeGrant(): Boolean = doesBotRequireCodeGrant

    override fun equals(other: Any?): Boolean = other is ApplicationInfoImpl && id == other.id

    @Nonnull
    override fun getDescription(): String = description

    @Nullable
    override fun getTermsOfServiceUrl(): String? = termsOfServiceUrl

    @Nullable
    override fun getPrivacyPolicyUrl(): String? = privacyPolicyUrl

    @Nullable
    override fun getIconId(): String? = iconId

    @Nullable
    override fun getIconUrl(): String? = getIconUrl(ImageFormat.PNG)

    @Nonnull
    override fun getTeam(): ApplicationTeam = team

    @Nonnull
    override fun setRequiredScopes(
        @Nonnull scopes: Collection<String>,
    ): ApplicationInfo {
        Checks.noneNull(scopes, "Scopes")
        this.scopes = scopes.joinToString("+")
        if (!this.scopes.contains("bot")) {
            if (this.scopes.isEmpty()) {
                this.scopes = "bot"
            } else {
                this.scopes += "+bot"
            }
        }
        return this
    }

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getInviteUrl(
        guildId: String?,
        permissions: Collection<Permission>?,
    ): String {
        val builder = StringBuilder("https://discord.com/oauth2/authorize?client_id=")
        builder.append(getId())
        builder.append("&scope=").append(scopes)
        if (permissions != null && permissions.isNotEmpty()) {
            builder.append("&permissions=")
            builder.append(Permission.getRaw(permissions))
        }
        if (guildId != null) {
            builder.append("&guild_id=")
            builder.append(guildId)
        }
        return builder.toString()
    }

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun getName(): String = name

    @Nonnull
    override fun getOwner(): User = owner

    override fun hashCode(): Int = id.hashCode()

    override fun isBotPublic(): Boolean = isBotPublic

    @Nonnull
    override fun getTags(): List<String> = tags

    @Nonnull
    override fun getRedirectUris(): List<String> = redirectUris

    @Nullable
    override fun getInteractionsEndpointUrl(): String? = interactionsEndpointUrl

    @Nullable
    override fun getRoleConnectionsVerificationUrl(): String? = roleConnectionsVerificationUrl

    @Nullable
    override fun getCustomAuthorizationUrl(): String? = customAuthUrl

    @Nonnull
    override fun getPermissions(): EnumSet<Permission> = Permission.getPermissions(defaultAuthUrlPerms)

    override fun getPermissionsRaw(): Long = defaultAuthUrlPerms

    override fun getFlagsRaw(): Long = flags

    @Nonnull
    override fun getScopes(): List<String> = defaultAuthUrlScopes

    override fun getUserInstallCount(): Long = approxUserInstallCount

    @Nonnull
    override fun getIntegrationTypesConfig(): Map<IntegrationType, IntegrationTypeConfiguration> = integrationTypesConfig

    override fun toString(): String = EntityString(this).toString()

    class IntegrationTypeConfigurationImpl(
        private val installParameters: InstallParameters?,
    ) : IntegrationTypeConfiguration {
        @Nullable
        override fun getInstallParameters(): InstallParameters? = installParameters
    }

    class InstallParametersImpl(
        scopes: List<String>,
        permissions: Set<Permission>,
    ) : InstallParameters {
        private val scopes: List<String> = Collections.unmodifiableList(scopes)
        private val permissions: Set<Permission> = Collections.unmodifiableSet(permissions)

        @Nonnull
        override fun getScopes(): List<String> = scopes

        @Nonnull
        override fun getPermissions(): Set<Permission> = permissions
    }
}
