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

package net.dv8tion.jda.internal.entities.detached

import net.dv8tion.jda.api.Region
import net.dv8tion.jda.api.entities.BulkBanResponse
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Guild.MetaData
import net.dv8tion.jda.api.entities.Guild.NSFWLevel
import net.dv8tion.jda.api.entities.Guild.NotificationLevel
import net.dv8tion.jda.api.entities.Guild.Timeout
import net.dv8tion.jda.api.entities.Guild.VerificationLevel
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.GuildWelcomeScreen
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.RoleMemberCounts
import net.dv8tion.jda.api.entities.ScheduledEvent
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.SoundboardSound
import net.dv8tion.jda.api.entities.SoundboardSoundSnowflake
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.VanityInvite
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.automod.AutoModRule
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions
import net.dv8tion.jda.api.entities.guild.SecurityIncidentDetections
import net.dv8tion.jda.api.entities.guild.SystemChannelFlag
import net.dv8tion.jda.api.entities.sticker.GuildSticker
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import net.dv8tion.jda.api.entities.templates.Template
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.PrivilegeConfig
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.privileges.IntegrationPrivilege
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.managers.AutoModRuleManager
import net.dv8tion.jda.api.managers.GuildManager
import net.dv8tion.jda.api.managers.GuildStickerManager
import net.dv8tion.jda.api.managers.GuildWelcomeScreenManager
import net.dv8tion.jda.api.managers.SoundboardSoundManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction
import net.dv8tion.jda.api.requests.restaction.CommandEditAction
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import net.dv8tion.jda.api.requests.restaction.MemberAction
import net.dv8tion.jda.api.requests.restaction.MessageSearchAction
import net.dv8tion.jda.api.requests.restaction.RoleAction
import net.dv8tion.jda.api.requests.restaction.ScheduledEventAction
import net.dv8tion.jda.api.requests.restaction.SoundboardSoundCreateAction
import net.dv8tion.jda.api.requests.restaction.order.CategoryOrderAction
import net.dv8tion.jda.api.requests.restaction.order.ChannelOrderAction
import net.dv8tion.jda.api.requests.restaction.order.RoleOrderAction
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.cache.MemberCacheView
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView
import net.dv8tion.jda.api.utils.cache.SortedSnowflakeCacheView
import net.dv8tion.jda.api.utils.concurrent.Task
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.detached.mixin.IDetachableEntityMixin
import net.dv8tion.jda.internal.requests.restaction.pagination.BanPaginationActionImpl
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.cache.SortedChannelCacheViewImpl
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.TemporalAccessor
import java.util.Collections
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Predicate
import javax.annotation.Nonnull
import javax.annotation.Nullable

class DetachedGuildImpl(
    private val api: JDAImpl,
    private val id: Long,
) : Guild,
    IDetachableEntityMixin {
    private var features: Set<String>? = null
    private var preferredLocale: DiscordLocale = DiscordLocale.ENGLISH_US

    override fun isDetached(): Boolean = true

    @Nonnull
    override fun getName(): String = throw detachedException()

    @Nonnull
    override fun getFeatures(): Set<String> = features as Set<String>

    @Nonnull
    override fun getLocale(): DiscordLocale = preferredLocale

    @Nonnull
    override fun getJDA(): JDAImpl = api

    override fun getIdLong(): Long = id

    // ---- Unsupported (detached) -----

    @Nonnull
    override fun retrieveCommands(withLocalizations: Boolean): RestAction<List<Command>> = throw detachedException()

    @Nonnull
    override fun retrieveCommandById(id: String): RestAction<Command> = throw detachedException()

    @Nonnull
    override fun upsertCommand(command: CommandData): CommandCreateAction = throw detachedException()

    @Nonnull
    override fun updateCommands(): CommandListUpdateAction = throw detachedException()

    @Nonnull
    override fun editCommandById(
        type: Command.Type,
        id: String,
    ): CommandEditAction = throw detachedException()

    @Nonnull
    override fun deleteCommandById(commandId: String): RestAction<Void> = throw detachedException()

    @Nonnull
    override fun retrieveIntegrationPrivilegesById(targetId: String): RestAction<List<IntegrationPrivilege>> = throw detachedException()

    @Nonnull
    override fun retrieveCommandPrivileges(): RestAction<PrivilegeConfig> = throw detachedException()

    @Nonnull
    override fun retrieveRegions(includeDeprecated: Boolean): RestAction<EnumSet<Region>> = throw detachedException()

    @Nonnull
    override fun retrieveAutoModRules(): RestAction<List<AutoModRule>> = throw detachedException()

    @Nonnull
    override fun retrieveAutoModRuleById(id: String): RestAction<AutoModRule> = throw detachedException()

    @Nonnull
    override fun createAutoModRule(rule: AutoModRuleData): AuditableRestAction<AutoModRule> = throw detachedException()

    @Nonnull
    override fun modifyAutoModRuleById(id: String): AutoModRuleManager = throw detachedException()

    @Nonnull
    override fun deleteAutoModRuleById(id: String): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun addMember(
        accessToken: String,
        user: UserSnowflake,
    ): MemberAction = throw detachedException()

    override fun isLoaded(): Boolean = throw detachedException()

    override fun pruneMemberCache(): Unit = throw detachedException()

    override fun unloadMember(userId: Long): Boolean = throw detachedException()

    override fun getMemberCount(): Int = throw detachedException()

    override fun getIconId(): String = throw detachedException()

    override fun getSplashId(): String = throw detachedException()

    @Nullable
    override fun getVanityCode(): String = throw detachedException()

    @Nonnull
    override fun retrieveVanityInvite(): RestAction<VanityInvite> = throw detachedException()

    @Nullable
    override fun getDescription(): String = throw detachedException()

    @Nullable
    override fun getBannerId(): String = throw detachedException()

    @Nonnull
    override fun getBoostTier(): Guild.BoostTier = throw detachedException()

    override fun getBoostCount(): Int = throw detachedException()

    override fun getBoosters(): List<Member> = throw detachedException()

    override fun getMaxMembers(): Int = throw detachedException()

    override fun getMaxPresences(): Int = throw detachedException()

    @Nonnull
    override fun retrieveMetaData(): RestAction<Guild.MetaData> = throw detachedException()

    override fun getAfkChannel(): VoiceChannel = throw detachedException()

    override fun getSystemChannel(): TextChannel = throw detachedException()

    override fun getRulesChannel(): TextChannel = throw detachedException()

    @Nonnull
    override fun retrieveScheduledEventById(id: String): CacheRestAction<ScheduledEvent> = throw detachedException()

    @Nonnull
    override fun retrieveScheduledEventById(id: Long): CacheRestAction<ScheduledEvent> = throw detachedException()

    @Nonnull
    override fun createScheduledEvent(
        name: String,
        location: String,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime,
    ): ScheduledEventAction = throw detachedException()

    @Nonnull
    override fun createScheduledEvent(
        name: String,
        channel: GuildChannel,
        startTime: OffsetDateTime,
    ): ScheduledEventAction = throw detachedException()

    @Nonnull
    override fun searchMessages(): MessageSearchAction = throw detachedException()

    override fun getCommunityUpdatesChannel(): TextChannel = throw detachedException()

    @Nullable
    override fun getSafetyAlertsChannel(): TextChannel = throw detachedException()

    @Nonnull
    override fun retrieveWebhooks(): RestAction<List<Webhook>> = throw detachedException()

    override fun getOwner(): Member = throw detachedException()

    override fun getOwnerIdLong(): Long = throw detachedException()

    @Nonnull
    override fun getAfkTimeout(): Guild.Timeout = throw detachedException()

    @Nonnull
    override fun getSecurityIncidentActions(): SecurityIncidentActions = throw detachedException()

    @Nonnull
    override fun getSecurityIncidentDetections(): SecurityIncidentDetections = throw detachedException()

    override fun isMember(user: UserSnowflake): Boolean = throw detachedException()

    @Nonnull
    override fun getSelfMember(): SelfMember = throw detachedException()

    override fun getMember(user: UserSnowflake): Member = throw detachedException()

    @Nonnull
    override fun findMembers(filter: Predicate<in Member>): Task<List<Member>> = throw detachedException()

    @Nonnull
    override fun getMemberCache(): MemberCacheView = throw detachedException()

    @Nonnull
    override fun getScheduledEventCache(): SortedSnowflakeCacheView<ScheduledEvent> = throw detachedException()

    @Nonnull
    override fun retrieveScheduledEvents(includeUserCount: Boolean): RestAction<List<ScheduledEvent>> = throw detachedException()

    @Nonnull
    override fun getCategoryCache(): SortedSnowflakeCacheView<Category> = throw detachedException()

    @Nonnull
    override fun getTextChannelCache(): SortedSnowflakeCacheView<TextChannel> = throw detachedException()

    @Nonnull
    override fun getNewsChannelCache(): SortedSnowflakeCacheView<NewsChannel> = throw detachedException()

    @Nonnull
    override fun getVoiceChannelCache(): SortedSnowflakeCacheView<VoiceChannel> = throw detachedException()

    @Nonnull
    override fun getForumChannelCache(): SortedSnowflakeCacheView<ForumChannel> = throw detachedException()

    @Nonnull
    override fun getMediaChannelCache(): SnowflakeCacheView<MediaChannel> = throw detachedException()

    @Nonnull
    override fun getStageChannelCache(): SortedSnowflakeCacheView<StageChannel> = throw detachedException()

    @Nonnull
    override fun getThreadChannelCache(): SortedSnowflakeCacheView<ThreadChannel> = throw detachedException()

    @Nonnull
    override fun getChannelCache(): SortedChannelCacheViewImpl<GuildChannel> = throw detachedException()

    @Nullable
    override fun getGuildChannelById(id: Long): GuildChannel = throw detachedException()

    override fun getGuildChannelById(
        type: ChannelType,
        id: Long,
    ): GuildChannel = throw detachedException()

    @Nonnull
    override fun getRoleCache(): SortedSnowflakeCacheView<Role> = throw detachedException()

    @Nonnull
    override fun getEmojiCache(): SnowflakeCacheView<RichCustomEmoji> = throw detachedException()

    @Nonnull
    override fun getStickerCache(): SnowflakeCacheView<GuildSticker> = throw detachedException()

    @Nonnull
    override fun getSoundboardSoundCache(): SnowflakeCacheView<SoundboardSound> = throw detachedException()

    @Nonnull
    override fun getChannels(includeHidden: Boolean): List<GuildChannel> = throw detachedException()

    @Nonnull
    override fun retrieveEmojis(): RestAction<List<RichCustomEmoji>> = throw detachedException()

    @Nonnull
    override fun retrieveEmojiById(id: String): RestAction<RichCustomEmoji> = throw detachedException()

    @Nonnull
    override fun retrieveEmoji(emoji: CustomEmoji): RestAction<RichCustomEmoji> = throw detachedException()

    @Nonnull
    override fun retrieveStickers(): RestAction<List<GuildSticker>> = throw detachedException()

    @Nonnull
    override fun retrieveSticker(sticker: StickerSnowflake): RestAction<GuildSticker> = throw detachedException()

    @Nonnull
    override fun editSticker(sticker: StickerSnowflake): GuildStickerManager = throw detachedException()

    @Nonnull
    override fun retrieveSoundboardSounds(): CacheRestAction<List<SoundboardSound>> = throw detachedException()

    @Nonnull
    override fun retrieveSoundboardSound(sound: SoundboardSoundSnowflake): CacheRestAction<SoundboardSound> = throw detachedException()

    @Nonnull
    override fun editSoundboardSound(sound: SoundboardSoundSnowflake): SoundboardSoundManager = throw detachedException()

    @Nonnull
    override fun retrieveBanList(): BanPaginationActionImpl = throw detachedException()

    @Nonnull
    override fun retrieveBan(user: UserSnowflake): RestAction<Guild.Ban> = throw detachedException()

    @Nonnull
    override fun retrievePrunableMemberCount(days: Int): RestAction<Int> = throw detachedException()

    @Nonnull
    override fun getPublicRole(): Role = throw detachedException()

    @Nullable
    override fun getDefaultChannel(): DefaultGuildChannelUnion = throw detachedException()

    @Nonnull
    override fun getManager(): GuildManager = throw detachedException()

    override fun isBoostProgressBarEnabled(): Boolean = throw detachedException()

    @Nonnull
    override fun retrieveAuditLogs(): AuditLogPaginationAction = throw detachedException()

    @Nonnull
    override fun leave(): RestAction<Void> = throw detachedException()

    @Nonnull
    override fun getAudioManager(): AudioManager = throw detachedException()

    @Nonnull
    override fun requestToSpeak(): Task<Void> = throw detachedException()

    @Nonnull
    override fun cancelRequestToSpeak(): Task<Void> = throw detachedException()

    @Nonnull
    override fun getVoiceStates(): List<GuildVoiceState> = throw detachedException()

    @Nonnull
    override fun retrieveMemberVoiceStateById(id: Long): CacheRestAction<GuildVoiceState> = throw detachedException()

    @Nonnull
    override fun getVerificationLevel(): Guild.VerificationLevel = throw detachedException()

    @Nonnull
    override fun getNSFWLevel(): Guild.NSFWLevel = throw detachedException()

    @Nonnull
    override fun getSystemChannelFlags(): Set<SystemChannelFlag> = throw detachedException()

    override fun getSystemChannelFlagsRaw(): Int = throw detachedException()

    @Nonnull
    override fun getDefaultNotificationLevel(): Guild.NotificationLevel = throw detachedException()

    @Nonnull
    override fun getRequiredMFALevel(): Guild.MFALevel = throw detachedException()

    @Nonnull
    override fun getExplicitContentLevel(): Guild.ExplicitContentLevel = throw detachedException()

    @Nonnull
    override fun loadMembers(callback: Consumer<Member>): Task<Void> = throw detachedException()

    @Nonnull
    override fun retrieveMemberById(id: Long): CacheRestAction<Member> = throw detachedException()

    @Nonnull
    override fun retrieveMembersByIds(
        includePresence: Boolean,
        vararg ids: Long,
    ): Task<List<Member>> = throw detachedException()

    @Nonnull
    override fun retrieveMembersByPrefix(
        prefix: String,
        limit: Int,
    ): Task<List<Member>> = throw detachedException()

    @Nonnull
    override fun retrieveActiveThreads(): RestAction<List<ThreadChannel>> = throw detachedException()

    @Nonnull
    override fun retrieveInvites(): RestAction<List<Invite>> = throw detachedException()

    @Nonnull
    override fun retrieveTemplates(): RestAction<List<Template>> = throw detachedException()

    @Nonnull
    override fun createTemplate(
        name: String,
        description: String?,
    ): RestAction<Template> = throw detachedException()

    @Nonnull
    override fun retrieveWelcomeScreen(): RestAction<GuildWelcomeScreen> = throw detachedException()

    @Nonnull
    override fun moveVoiceMember(
        user: UserSnowflake,
        audioChannel: AudioChannel?,
    ): RestAction<Void> = throw detachedException()

    @Nonnull
    override fun modifyNickname(
        member: Member,
        nickname: String?,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun prune(
        days: Int,
        wait: Boolean,
        vararg roles: Role,
    ): AuditableRestAction<Int> = throw detachedException()

    @Nonnull
    override fun modifySecurityIncidents(incidents: SecurityIncidentActions): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun kick(user: UserSnowflake): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun ban(
        user: UserSnowflake,
        duration: Int,
        unit: TimeUnit,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun ban(
        users: Collection<UserSnowflake>,
        deletionTime: Duration?,
    ): AuditableRestAction<BulkBanResponse> = throw detachedException()

    @Nonnull
    override fun unban(user: UserSnowflake): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun timeoutUntil(
        user: UserSnowflake,
        temporal: TemporalAccessor,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun removeTimeout(user: UserSnowflake): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun deafen(
        user: UserSnowflake,
        deafen: Boolean,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun mute(
        user: UserSnowflake,
        mute: Boolean,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun addRoleToMember(
        user: UserSnowflake,
        role: Role,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun removeRoleFromMember(
        user: UserSnowflake,
        role: Role,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun modifyMemberRoles(
        member: Member,
        rolesToAdd: Collection<Role>?,
        rolesToRemove: Collection<Role>?,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun modifyMemberRoles(
        member: Member,
        roles: Collection<Role>,
    ): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun retrieveRoleMemberCounts(): RestAction<RoleMemberCounts> = throw detachedException()

    @Nonnull
    override fun createTextChannel(
        name: String,
        parent: Category?,
    ): ChannelAction<TextChannel> = throw detachedException()

    @Nonnull
    override fun createNewsChannel(
        name: String,
        parent: Category?,
    ): ChannelAction<NewsChannel> = throw detachedException()

    @Nonnull
    override fun createVoiceChannel(
        name: String,
        parent: Category?,
    ): ChannelAction<VoiceChannel> = throw detachedException()

    @Nonnull
    override fun createStageChannel(
        name: String,
        parent: Category?,
    ): ChannelAction<StageChannel> = throw detachedException()

    @Nonnull
    override fun createForumChannel(
        name: String,
        parent: Category?,
    ): ChannelAction<ForumChannel> = throw detachedException()

    @Nonnull
    override fun createMediaChannel(
        name: String,
        parent: Category?,
    ): ChannelAction<MediaChannel> = throw detachedException()

    @Nonnull
    override fun createCategory(name: String): ChannelAction<Category> = throw detachedException()

    @Nonnull
    override fun createRole(): RoleAction = throw detachedException()

    @Nonnull
    override fun createEmoji(
        name: String,
        icon: Icon,
        vararg roles: Role,
    ): AuditableRestAction<RichCustomEmoji> = throw detachedException()

    @Nonnull
    override fun createSticker(
        name: String,
        description: String,
        file: FileUpload,
        tags: Collection<String>,
    ): AuditableRestAction<GuildSticker> = throw detachedException()

    @Nonnull
    override fun deleteSticker(id: StickerSnowflake): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun createSoundboardSound(
        name: String,
        file: FileUpload,
    ): SoundboardSoundCreateAction = throw detachedException()

    @Nonnull
    override fun deleteSoundboardSound(sound: SoundboardSoundSnowflake): AuditableRestAction<Void> = throw detachedException()

    @Nonnull
    override fun modifyCategoryPositions(): ChannelOrderAction = throw detachedException()

    @Nonnull
    override fun modifyTextChannelPositions(): ChannelOrderAction = throw detachedException()

    @Nonnull
    override fun modifyVoiceChannelPositions(): ChannelOrderAction = throw detachedException()

    @Nonnull
    override fun modifyTextChannelPositions(category: Category): CategoryOrderAction = throw detachedException()

    @Nonnull
    override fun modifyVoiceChannelPositions(category: Category): CategoryOrderAction = throw detachedException()

    @Nonnull
    override fun modifyRolePositions(useAscendingOrder: Boolean): RoleOrderAction = throw detachedException()

    @Nonnull
    override fun modifyWelcomeScreen(): GuildWelcomeScreenManager = throw detachedException()

    // ---- Setters -----

    fun setFeatures(features: Set<String>): DetachedGuildImpl {
        this.features = Collections.unmodifiableSet(features)
        return this
    }

    fun setLocale(locale: DiscordLocale): DetachedGuildImpl {
        this.preferredLocale = locale
        return this
    }

    // -- Object overrides --

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is DetachedGuildImpl) {
            return false
        }
        return this.id == other.id
    }

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun toString(): String = EntityString(this).toString()
}
