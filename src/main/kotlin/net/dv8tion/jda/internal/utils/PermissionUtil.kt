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

package net.dv8tion.jda.internal.utils

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.exceptions.DetachedEntityException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IInteractionPermissionMixin
import org.apache.commons.collections4.CollectionUtils
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Collectors
import java.util.stream.Stream

object PermissionUtil {
    private val ALL_PERMISSIONS: Long = Permission.getRaw(*Permission.entries.toTypedArray())

    private val ALL_CHANNEL_PERMISSIONS: Long =
        Permission.getRaw(
            Permission.entries
                .stream()
                .filter { it.isChannel }
                .collect(Collectors.toList()),
        )

    private const val MAX_CAUSE_DEPTH = 5

    /**
     * Checks if one given Member can interact with a 2nd given Member - in a permission sense (kick/ban/modify perms).
     * This only checks the Role-Position and does not check the actual permission (kick/ban/manage_role/...)
     *
     * @param issuer
     *         The member that tries to interact with 2nd member
     * @param target
     *         The member that is the target of the interaction
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     *
     * @return True, if issuer can interact with target in guild
     */
    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun canInteract(
        issuer: Member,
        target: Member,
    ): Boolean {
        Checks.notNull(issuer, "Issuer Member")
        Checks.notNull(target, "Target Member")

        val guild = issuer.guild
        if (guild != target.guild) {
            throw IllegalArgumentException("Provided members must both be Member objects of the same Guild!")
        }
        if (issuer.isOwner) {
            return true
        }
        if (target.isOwner) {
            return false
        }
        val issuerRoles = issuer.roles
        val targetRoles = target.roles
        return issuerRoles.isNotEmpty() && (targetRoles.isEmpty() || canInteract(issuerRoles[0], targetRoles[0]))
    }

    /**
     * Checks if a given Member can interact with a given Role - in a permission sense (kick/ban/modify perms).
     * This only checks the Role-Position and does not check the actual permission (kick/ban/manage_role/...)
     *
     * @param issuer
     *         The member that tries to interact with the role
     * @param target
     *         The role that is the target of the interaction
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     *
     * @return True, if issuer can interact with target
     */
    @JvmStatic
    fun canInteract(
        issuer: Member,
        target: Role,
    ): Boolean {
        Checks.notNull(issuer, "Issuer Member")
        Checks.notNull(target, "Target Role")

        val guild = issuer.guild
        if (guild != target.guild) {
            throw IllegalArgumentException("Provided Member issuer and Role target must be from the same Guild!")
        }
        if (issuer.isOwner) {
            return true
        }
        val issuerRoles = issuer.roles
        return issuerRoles.isNotEmpty() && canInteract(issuerRoles[0], target)
    }

    /**
     * Checks if one given Role can interact with a 2nd given Role - in a permission sense (kick/ban/modify perms).
     * This only checks the Role-Position and does not check the actual permission (kick/ban/manage_role/...)
     *
     * @param issuer
     *         The role that tries to interact with 2nd role
     * @param target
     *         The role that is the target of the interaction
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     *
     * @return True, if issuer can interact with target
     */
    @JvmStatic
    fun canInteract(
        issuer: Role,
        target: Role,
    ): Boolean {
        Checks.notNull(issuer, "Issuer Role")
        Checks.notNull(target, "Target Role")

        if (issuer.guild != target.guild) {
            throw IllegalArgumentException("The 2 Roles are not from same Guild!")
        }
        return target.compareTo(issuer) < 0
    }

    /**
     * Check whether the provided [net.dv8tion.jda.api.entities.Member] can use the specified [RichCustomEmoji].
     *
     * If the specified Member is not in the emoji's guild or the emoji provided is from a message this will return false.
     * Otherwise, it will check if the emoji is restricted to any roles and if that is the case if the Member has one of these.
     *
     * **Note**: This is not checking if the issuer owns the Guild or not.
     *
     * @param issuer
     *         The member that tries to interact with the Emoji
     * @param emoji
     *         The emoji that is the target interaction
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     *
     * @return True, if the issuer can interact with the emoji
     */
    @JvmStatic
    fun canInteract(
        issuer: Member,
        emoji: RichCustomEmoji,
    ): Boolean {
        Checks.notNull(issuer, "Issuer Member")
        Checks.notNull(emoji, "Target Emoji")

        if (issuer.guild != emoji.guild) {
            throw IllegalArgumentException("The issuer and target are not in the same Guild")
        }

        return emoji.roles.isEmpty() ||
            // emoji restricted to roles -> check if the issuer has them
            CollectionUtils.containsAny(issuer.roles, emoji.roles)
    }

    /**
     * Checks whether the specified [RichCustomEmoji] can be used by the provided
     * [net.dv8tion.jda.api.entities.User] in the [MessageChannel].
     *
     * @param issuer
     *         The user that tries to interact with the emoji
     * @param emoji
     *         The emoji that is the target interaction
     * @param channel
     *         The MessageChannel this emoji should be interacted within
     * @param botOverride
     *         Whether bots can use non-managed emojis in other guilds
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     *
     * @return True, if the issuer can interact with the emoji within the specified MessageChannel
     */
    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun canInteract(
        issuer: User,
        emoji: RichCustomEmoji,
        channel: MessageChannel,
        botOverride: Boolean,
    ): Boolean {
        Checks.notNull(issuer, "Issuer Member")
        Checks.notNull(emoji, "Target Emoji")
        Checks.notNull(channel, "Target Channel")

        if (!emoji.guild.isMember(issuer)) {
            return false // cannot use an emoji if you're not in its guild
        }
        val member = emoji.guild.getMemberById(issuer.idLong) ?: return false
        if (!canInteract(member, emoji)) {
            return false
        }
        // external means it is available outside of its own guild - works for bots or if its
        // managed
        // currently we cannot check whether other users have nitro, we assume no here
        val external = emoji.isManaged || (issuer.isBot && botOverride)
        return when (channel.type) {
            ChannelType.TEXT -> {
                val text = channel as TextChannel
                val textMember = text.guild.getMemberById(issuer.idLong)
                emoji.guild == text.guild ||
                    // within the same guild
                    (
                        external &&
                            textMember != null &&
                            textMember.hasPermission(text, Permission.MESSAGE_EXT_EMOJI)
                    ) // in different guild
            }
            else -> external // In Group or Private it only needs to be external
        }
    }

    /**
     * Checks whether the specified [RichCustomEmoji] can be used by the provided
     * [net.dv8tion.jda.api.entities.User] in the [MessageChannel].
     *
     * @param issuer
     *         The user that tries to interact with the emoji
     * @param emoji
     *         The emoji that is the target interaction
     * @param channel
     *         The MessageChannel this emoji should be interacted within
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     *
     * @return True, if the issuer can interact with the emoji within the specified MessageChannel
     */
    @JvmStatic
    fun canInteract(
        issuer: User,
        emoji: RichCustomEmoji,
        channel: MessageChannel,
    ): Boolean = canInteract(issuer, emoji, channel, true)

    /**
     * Checks to see if the [net.dv8tion.jda.api.entities.Member] has the specified [net.dv8tion.jda.api.Permission]s
     * in the specified [net.dv8tion.jda.api.entities.Guild]. This method properly deals with Owner status.
     *
     * **Note:** this is based on effective permissions, not literal permissions. If a member has permissions that would
     * enable them to do something without the literal permission to do it, this will still return true.
     * <br>Example: If a member has the [net.dv8tion.jda.api.Permission.ADMINISTRATOR] permission, they will be able to
     * [net.dv8tion.jda.api.Permission.MANAGE_SERVER] as well, even without the literal permissions.
     *
     * @param member
     *         The [net.dv8tion.jda.api.entities.Member] whose permissions are being checked.
     * @param permissions
     *         The [net.dv8tion.jda.api.Permission] being checked for.
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is null
     *
     * @return True -
     *         if the [net.dv8tion.jda.api.entities.Member] effectively has the specified [net.dv8tion.jda.api.Permission].
     */
    @JvmStatic
    fun checkPermission(
        member: Member,
        vararg permissions: Permission,
    ): Boolean {
        Checks.notNull(member, "Member")
        Checks.notNull(permissions, "Permissions")

        val effectivePerms = getEffectivePermission(member)
        return isApplied(effectivePerms, Permission.ADMINISTRATOR.rawValue) ||
            isApplied(effectivePerms, Permission.getRaw(*permissions))
    }

    /**
     * Checks to see if the [net.dv8tion.jda.api.entities.Member] has the specified [net.dv8tion.jda.api.Permission]s
     * in the specified [GuildChannel]. This method properly deals with
     * [net.dv8tion.jda.api.entities.PermissionOverride] and Owner status.
     *
     * **Note:** this is based on effective permissions, not literal permissions. If a member has permissions that would
     * enable them to do something without the literal permission to do it, this will still return true.
     * <br>Example: If a member has the [net.dv8tion.jda.api.Permission.ADMINISTRATOR] permission, they will be able to
     * [net.dv8tion.jda.api.Permission.MESSAGE_SEND] in every channel.
     *
     * @param member
     *         The [net.dv8tion.jda.api.entities.Member] whose permissions are being checked.
     * @param channel
     *         The [GuildChannel] being checked.
     * @param permissions
     *         The [net.dv8tion.jda.api.Permission] being checked for.
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     *
     * @return True -
     *         if the [net.dv8tion.jda.api.entities.Member] effectively has the specified [net.dv8tion.jda.api.Permission].
     */
    @JvmStatic
    fun checkPermission(
        channel: GuildChannel,
        member: Member,
        vararg permissions: Permission,
    ): Boolean {
        if (isInteractionPermissionOverride(channel)) {
            val mixin = channel as IInteractionPermissionMixin<*>
            val interactionPermissions = getInteractionPermissions(mixin, member)
            val rawPermissions = Permission.getRaw(*permissions)
            return (interactionPermissions and rawPermissions) == rawPermissions
        }

        return checkPermissionInContainer(channel.permissionContainer, member, *permissions)
    }

    private fun checkPermissionInContainer(
        channel: IPermissionContainer?,
        member: Member,
        vararg permissions: Permission,
    ): Boolean {
        Checks.notNull(channel, "Channel")
        Checks.notNull(member, "Member")
        Checks.notNull(permissions, "Permissions")

        checkGuild(channel!!.guild, member.guild, "Member")

        val effectivePerms = getEffectivePermission(channel, member)
        return isApplied(effectivePerms, Permission.getRaw(*permissions))
    }

    /**
     * Checks if the member has any of the specified permissions. Also checks for owners and administrators.
     *
     * @param member
     *         The member whose permissions are being checked
     * @param permissions
     *         The permissions being checked for
     *
     * @throws IllegalArgumentException
     *         If any of the provided parameters are null, or no permissions were given
     * @throws InsufficientPermissionException
     *         If the member has none of the specified permissions
     */
    @JvmStatic
    fun requireAnyPermission(
        member: Member,
        vararg permissions: Permission,
    ) {
        Checks.notNull(member, "Member")
        Checks.notEmpty(permissions, "Permissions")

        for (permission in permissions) {
            if (member.hasPermission(permission)) {
                return
            }
        }

        val reason = Stream.of(*permissions).map(Permission::name).collect(Collectors.joining(" or "))
        throw InsufficientPermissionException(
            member.guild,
            permissions[0],
            "You need the $reason permission to perform this action!",
        )
    }

    /**
     * Gets the `long` representation of the effective permissions allowed for this [net.dv8tion.jda.api.entities.Member]
     * in this [net.dv8tion.jda.api.entities.Guild]. This can be used in conjunction with
     * [net.dv8tion.jda.api.Permission.getPermissions] to easily get a list of all
     * [net.dv8tion.jda.api.Permission] that this member has in this [net.dv8tion.jda.api.entities.Guild].
     *
     * **This only returns the Guild-level permissions!**
     *
     * @param member
     *         The [net.dv8tion.jda.api.entities.Member] whose permissions are being checked.
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     * @throws DetachedEntityException
     *         If the provided member is in a guild the bot is not a member of
     *
     * @return The `long` representation of the literal permissions that
     *         this [net.dv8tion.jda.api.entities.Member] has in this [net.dv8tion.jda.api.entities.Guild].
     */
    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun getEffectivePermission(member: Member): Long {
        Checks.notNull(member, "Member")

        if (member.isDetached) {
            throw DetachedEntityException(
                "Cannot get the effective permissions of a detached member without a channel. " +
                    "Instead, please use the Member methods while supplying a GuildChannel",
            )
        }

        if (member.isOwner) {
            return ALL_PERMISSIONS
        }
        // Default to binary OR of all global permissions in this guild
        var permission = member.guild.publicRole.permissionsRaw
        for (role in member.unsortedRoles) {
            permission = permission or role.permissionsRaw
            if (isApplied(permission, Permission.ADMINISTRATOR.rawValue)) {
                return ALL_PERMISSIONS
            }
        }
        // See
        // https://discord.com/developers/docs/topics/permissions#permissions-for-timed-out-members
        if (member.isTimedOut) {
            permission = permission and (Permission.VIEW_CHANNEL.rawValue or Permission.MESSAGE_HISTORY.rawValue)
        }
        return permission
    }

    /**
     * Gets the `long` representation of the effective permissions allowed for this [net.dv8tion.jda.api.entities.Member]
     * in this [IPermissionContainer]. This can be used in conjunction with
     * [net.dv8tion.jda.api.Permission.getPermissions] to easily get a list of all
     * [net.dv8tion.jda.api.Permission] that this member can use in this [IPermissionContainer].
     * <br>This functions very similarly to how [net.dv8tion.jda.api.entities.Role.getPermissionsRaw].
     *
     * @param channel
     *         The [IPermissionContainer] being checked.
     * @param member
     *         The [net.dv8tion.jda.api.entities.Member] whose permissions are being checked.
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     * @throws DetachedEntityException
     *         If the provided member is in a guild the bot is not a member of
     *
     * @return The `long` representation of the effective permissions that this [net.dv8tion.jda.api.entities.Member]
     *         has in this [IPermissionContainer].
     */
    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun getEffectivePermission(
        channel: GuildChannel,
        member: Member,
    ): Long {
        Checks.notNull(channel, "Channel")
        Checks.notNull(member, "Member")

        if (isInteractionPermissionOverride(channel)) {
            return getInteractionPermissions(channel as IInteractionPermissionMixin<*>, member)
        }

        if (isInheritingPermissionsFromContainer(channel)) {
            return getEffectivePermission(channel.permissionContainer, member)
        }

        Checks.check(
            channel.guild == member.guild,
            "Provided channel and provided member are not of the same guild!",
        )

        if (member.isDetached) {
            throw DetachedEntityException(
                "Cannot get the effective permissions of a detached member. " +
                    "Instead, please use the Member methods while supplying a GuildChannel",
            )
        }

        if (member.isOwner) {
            // Owner effectively has all permissions
            return ALL_PERMISSIONS
        }

        var permission = getEffectivePermission(member)
        val admin = Permission.ADMINISTRATOR.rawValue
        if (isApplied(permission, admin)) {
            return ALL_PERMISSIONS
        }

        // MANAGE_CHANNEL allows to delete channels within a category
        // (this is undocumented behavior)
        if (channel is ICategorizableChannel) {
            val parentCategory = channel.parentCategory
            if (parentCategory != null && checkPermission(parentCategory, member, Permission.MANAGE_CHANNEL)) {
                permission = permission or Permission.MANAGE_CHANNEL.rawValue
            }
        }

        val allow = AtomicLong(0)
        val deny = AtomicLong(0)
        getExplicitOverrides(channel, member, allow, deny)
        permission = apply(permission, allow.get(), deny.get())
        val viewChannel = Permission.VIEW_CHANNEL.rawValue
        val connectChannel = Permission.VOICE_CONNECT.rawValue

        // When the permission to view the channel or to connect to the channel is not applied it is
        // not granted
        // This means that we have no access to this channel at all
        // See https://github.com/discord/discord-api-docs/issues/1522
        val hasConnect = !channel.type.isAudio || isApplied(permission, connectChannel)
        val hasAccess = isApplied(permission, viewChannel) && hasConnect

        // See
        // https://discord.com/developers/docs/topics/permissions#permissions-for-timed-out-members
        if (member.isTimedOut) {
            permission = permission and (viewChannel or Permission.MESSAGE_HISTORY.rawValue)
        }

        return if (hasAccess) permission else 0
    }

    /**
     * Gets the `long` representation of the effective permissions allowed for this [net.dv8tion.jda.api.entities.Role]
     * in this [IPermissionContainer]. This can be used in conjunction with
     * [net.dv8tion.jda.api.Permission.getPermissions] to easily get a list of all
     * [net.dv8tion.jda.api.Permission] that this role can use in this [IPermissionContainer].
     *
     * @param channel
     *         The [IPermissionContainer] in which permissions are being checked.
     * @param role
     *         The [net.dv8tion.jda.api.entities.Role] whose permissions are being checked.
     *
     * @throws IllegalArgumentException
     *         if any of the provided parameters is `null`
     *         or the provided entities are not from the same guild
     *
     * @return The `long` representation of the effective permissions that this [net.dv8tion.jda.api.entities.Role]
     *         has in this [IPermissionContainer]
     */
    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun getEffectivePermission(
        channel: GuildChannel,
        role: Role,
    ): Long {
        Checks.notNull(channel, "Channel")
        Checks.notNull(role, "Role")
        checkGuild(channel.guild, role.guild, "Role")

        if (channel.isDetached) {
            return 0L
        }

        if (isInheritingPermissionsFromContainer(channel)) {
            return getEffectivePermission(channel.permissionContainer, role)
        }

        val permissions = getExplicitPermission(channel, role)
        return if (isApplied(permissions, Permission.ADMINISTRATOR.rawValue)) {
            ALL_CHANNEL_PERMISSIONS
        } else if (!isApplied(permissions, Permission.VIEW_CHANNEL.rawValue)) {
            0
        } else {
            permissions
        }
    }

    /**
     * Retrieves the explicit permissions of the specified [net.dv8tion.jda.api.entities.Member]
     * in its hosting [net.dv8tion.jda.api.entities.Guild].
     * <br>This method does not calculate the owner in.
     *
     * All permissions returned are explicitly granted to this Member via its [net.dv8tion.jda.api.entities.Role].
     * <br>Permissions like [net.dv8tion.jda.api.Permission.ADMINISTRATOR] do not
     * grant other permissions in this value.
     *
     * @param member
     *         The non-null [net.dv8tion.jda.api.entities.Member] for which to get implicit permissions
     *
     * @throws IllegalArgumentException
     *         If the specified member is `null`
     * @throws DetachedEntityException
     *         If the provided member is in a guild the bot is not a member of
     *
     * @return Primitive (unsigned) long value with the implicit permissions of the specified member
     */
    @JvmStatic
    fun getExplicitPermission(member: Member): Long {
        Checks.notNull(member, "Member")

        if (member.isDetached) {
            throw DetachedEntityException(
                "Cannot get the explicit permissions of a detached member without a channel. " +
                    "Instead, please use the Member methods while supplying a GuildChannel",
            )
        }

        val guild = member.guild
        var permission = guild.publicRole.permissionsRaw

        for (role in member.unsortedRoles) {
            permission = permission or role.permissionsRaw
        }

        return permission
    }

    /**
     * Retrieves the explicit permissions of the specified [net.dv8tion.jda.api.entities.Member]
     * in its hosting [net.dv8tion.jda.api.entities.Guild] and specific [IPermissionContainer].
     * <br>This method does not calculate the owner in.
     * **Allowed permissions override denied permissions of [net.dv8tion.jda.api.entities.PermissionOverride]!**
     *
     * All permissions returned are explicitly granted to this Member via its [net.dv8tion.jda.api.entities.Role].
     * <br>Permissions like [net.dv8tion.jda.api.Permission.ADMINISTRATOR] do not
     * grant other permissions in this value.
     * This factor in all [net.dv8tion.jda.api.entities.PermissionOverride] that affect this member
     * and only grants the ones that are explicitly given.
     *
     * @param channel
     *         The target channel of which to check [net.dv8tion.jda.api.entities.PermissionOverride]
     * @param member
     *         The non-null [net.dv8tion.jda.api.entities.Member] for which to get implicit permissions
     *
     * @throws IllegalArgumentException
     *         If any of the arguments is `null`
     *         or the specified entities are not from the same [net.dv8tion.jda.api.entities.Guild]
     *
     * @return Primitive (unsigned) long value with the implicit permissions of the specified member in the specified channel
     */
    @JvmStatic
    fun getExplicitPermission(
        channel: GuildChannel,
        member: Member,
    ): Long = getExplicitPermission(channel, member, true)

    /**
     * Retrieves the explicit permissions of the specified [net.dv8tion.jda.api.entities.Member]
     * in its hosting [net.dv8tion.jda.api.entities.Guild] and specific [IPermissionContainer].
     * <br>This method does not calculate the owner in.
     * **Allowed permissions override denied permissions of [net.dv8tion.jda.api.entities.PermissionOverride]!**
     *
     * All permissions returned are explicitly granted to this Member via its [net.dv8tion.jda.api.entities.Role].
     * <br>Permissions like [net.dv8tion.jda.api.Permission.ADMINISTRATOR] do not
     * grant other permissions in this value.
     * This factor in all [net.dv8tion.jda.api.entities.PermissionOverride] that affect this member
     * and only grants the ones that are explicitly given.
     *
     * @param channel
     *         The target channel of which to check [net.dv8tion.jda.api.entities.PermissionOverride]
     * @param member
     *         The non-null [net.dv8tion.jda.api.entities.Member] for which to get implicit permissions
     * @param includeRoles
     *         Whether the base role permissions should be included
     *
     * @throws IllegalArgumentException
     *         If any of the arguments is `null`
     *         or the specified entities are not from the same [net.dv8tion.jda.api.entities.Guild]
     * @throws DetachedEntityException
     *         If the provided member is in a guild the bot is not a member of
     *
     * @return Primitive (unsigned) long value with the implicit permissions of the specified member in the specified channel
     */
    @JvmStatic
    fun getExplicitPermission(
        channel: GuildChannel,
        member: Member,
        includeRoles: Boolean,
    ): Long {
        Checks.notNull(channel, "Channel")
        Checks.notNull(member, "Member")

        checkGuild(channel.guild, member.guild, "Member")

        if (member.isDetached) {
            throw DetachedEntityException(
                "Cannot get the explicit permissions of a detached member. " +
                    "Instead, please use the Member methods while supplying a GuildChannel",
            )
        }

        if (isInteractionPermissionOverride(channel)) {
            return getInteractionPermissions(channel as IInteractionPermissionMixin<*>, member)
        }

        var permission = if (includeRoles) getExplicitPermission(member) else 0L

        val allow = AtomicLong(0)
        val deny = AtomicLong(0)

        // populates allow/deny
        getExplicitOverrides(channel, member, allow, deny)

        return apply(permission, allow.get(), deny.get())
    }

    /**
     * Retrieves the explicit permissions of the specified [net.dv8tion.jda.api.entities.Role]
     * in its hosting [net.dv8tion.jda.api.entities.Guild] and specific [IPermissionContainer].
     * <br>**Allowed permissions override denied permissions of [net.dv8tion.jda.api.entities.PermissionOverride]!**
     *
     * All permissions returned are explicitly granted to this Role.
     * <br>Permissions like [net.dv8tion.jda.api.Permission.ADMINISTRATOR] do not
     * grant other permissions in this value.
     * This factor in existing [net.dv8tion.jda.api.entities.PermissionOverride] if possible.
     *
     * @param channel
     *         The target channel of which to check [net.dv8tion.jda.api.entities.PermissionOverride]
     * @param role
     *         The non-null [net.dv8tion.jda.api.entities.Role] for which to get implicit permissions
     *
     * @throws IllegalArgumentException
     *         If any of the arguments is `null`
     *         or the specified entities are not from the same [net.dv8tion.jda.api.entities.Guild]
     *
     * @return Primitive (unsigned) long value with the implicit permissions of the specified role in the specified channel
     */
    @JvmStatic
    fun getExplicitPermission(
        channel: GuildChannel,
        role: Role,
    ): Long = getExplicitPermission(channel, role, true)

    /**
     * Retrieves the explicit permissions of the specified [net.dv8tion.jda.api.entities.Role]
     * in its hosting [net.dv8tion.jda.api.entities.Guild] and specific [IPermissionContainer].
     * <br>**Allowed permissions override denied permissions of [net.dv8tion.jda.api.entities.PermissionOverride]!**
     *
     * All permissions returned are explicitly granted to this Role.
     * <br>Permissions like [net.dv8tion.jda.api.Permission.ADMINISTRATOR] do not
     * grant other permissions in this value.
     * This factor in existing [net.dv8tion.jda.api.entities.PermissionOverride] if possible.
     *
     * @param channel
     *         The target channel of which to check [net.dv8tion.jda.api.entities.PermissionOverride]
     * @param role
     *         The non-null [net.dv8tion.jda.api.entities.Role] for which to get implicit permissions
     * @param includeRoles
     *         Whether the base role permissions should be included
     *
     * @throws IllegalArgumentException
     *         If any of the arguments is `null`
     *         or the specified entities are not from the same [net.dv8tion.jda.api.entities.Guild]
     * @throws DetachedEntityException
     *         If the provided role is in a guild the bot is not a member of
     *
     * @return Primitive (unsigned) long value with the implicit permissions of the specified role in the specified channel
     */
    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun getExplicitPermission(
        channel: GuildChannel,
        role: Role,
        includeRoles: Boolean,
    ): Long {
        Checks.notNull(channel, "Channel")
        Checks.notNull(role, "Role")

        // Can't know exactly what the role's permissions in that channel are, since we don't have
        // the overrides.
        if (role.isDetached) {
            throw DetachedEntityException("Cannot get the explicit permissions of a detached role")
        }

        checkGuild(channel.guild, role.guild, "Role")

        if (channel.isDetached) {
            return 0L
        }

        val permsChannel = channel.permissionContainer

        val guild = role.guild

        var permission = if (includeRoles) role.permissionsRaw or guild.publicRole.permissionsRaw else 0
        var override = permsChannel.getPermissionOverride(guild.publicRole)
        if (override != null) {
            permission = apply(permission, override.allowedRaw, override.deniedRaw)
        }
        if (role.isPublicRole) {
            return permission
        }

        override = permsChannel.getPermissionOverride(role)

        return if (override == null) permission else apply(permission, override.allowedRaw, override.deniedRaw)
    }

    @Suppress("ReferenceEquality")
    private fun isInheritingPermissionsFromContainer(channel: GuildChannel): Boolean =
        // Intentionally checking reference equality to handle "return this;" implementation
        channel.permissionContainer !== channel

    private fun isInteractionPermissionOverride(channel: GuildChannel): Boolean = channel is IInteractionPermissionMixin<*>

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    private fun getInteractionPermissions(
        channel: IInteractionPermissionMixin<*>,
        member: Member,
    ): Long {
        Checks.notNull(member, "Member")
        checkGuild(channel.guild, member.guild, "Member")

        if (member.isOwner) {
            return ALL_PERMISSIONS
        }

        val interactionPermissions = channel.interactionPermissions
        if (interactionPermissions.memberId == member.idLong) {
            return interactionPermissions.permissions
        }

        if (channel.isObfuscated) {
            if (member.idLong == channel.jda.selfUser.idLong) {
                // We know if the channel is obfuscated, the self-user has no permissions to see it
                return 0L
            }

            throw DetachedEntityException(
                Helpers.format(
                    "The requested member %s is not the interacting user (%s). " +
                        "Since the used channel is obfuscated the permissions of other members cannot be determined.",
                    member.id,
                    java.lang.Long.toUnsignedString(interactionPermissions.memberId),
                ),
            )
        }

        return 0L
    }

    private fun getExplicitOverrides(
        channel: GuildChannel,
        member: Member,
        allow: AtomicLong,
        deny: AtomicLong,
    ) {
        val permsChannel = channel.permissionContainer
        var override = permsChannel.getPermissionOverride(member.guild.publicRole)
        var allowRaw = 0L
        var denyRaw = 0L
        if (override != null) {
            denyRaw = override.deniedRaw
            allowRaw = override.allowedRaw
        }

        var allowRole = 0L
        var denyRole = 0L
        // create temporary bit containers for role cascade
        for (role in member.unsortedRoles) {
            override = permsChannel.getPermissionOverride(role)
            if (override != null) {
                // important to update role cascade not others
                denyRole = denyRole or override.deniedRaw
                allowRole = allowRole or override.allowedRaw
            }
        }
        // Override the raw values of public role then apply role cascade
        allowRaw = (allowRaw and denyRole.inv()) or allowRole
        denyRaw = (denyRaw and allowRole.inv()) or denyRole

        override = permsChannel.getPermissionOverride(member)
        if (override != null) {
            // finally override the role cascade with member overrides
            val oDeny = override.deniedRaw
            val oAllow = override.allowedRaw
            allowRaw = (allowRaw and oDeny.inv()) or oAllow
            denyRaw = (denyRaw and oAllow.inv()) or oDeny
            // this time we need to exclude new allowed bits from old denied ones and OR the new
            // denied bits as final overrides
        }
        // set as resulting values
        allow.set(allowRaw)
        deny.set(denyRaw)
    }

    /*
     * Check whether the specified permission is applied in the bits
     */
    private fun isApplied(
        permissions: Long,
        perms: Long,
    ): Boolean = (permissions and perms) == perms

    private fun apply(
        permission: Long,
        allow: Long,
        deny: Long,
    ): Long {
        var result = permission
        result = result and deny.inv() // Deny everything that the cascade of roles denied.
        result = result or allow // Allow all the things that the cascade of roles allowed
        // The allowed bits override the denied ones!
        return result
    }

    private fun checkGuild(
        o1: Guild,
        o2: Guild,
        name: String,
    ) {
        Checks.check(o1.idLong == o2.idLong, "Specified %s is not in the same guild! (%s / %s)", name, o1, o2)
    }
}
