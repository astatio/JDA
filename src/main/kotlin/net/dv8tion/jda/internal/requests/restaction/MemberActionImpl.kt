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

package net.dv8tion.jda.internal.requests.restaction

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.MemberAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.Helpers
import okhttp3.RequestBody
import java.util.HashSet
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

private const val MAX_NICKNAME_LENGTH = 32

class MemberActionImpl(
    api: JDA,
    private val guild: Guild,
    private val userId: String,
    private val accessToken: String,
) : RestActionImpl<Void>(api, Route.Guilds.ADD_MEMBER.compile(guild.id, userId)),
    MemberAction {
    private var nick: String? = null
    private var roles: MutableSet<Role>? = null
    private var mute = false
    private var deaf = false

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): MemberAction = super.setCheck(checks) as MemberAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        @Nonnull unit: TimeUnit,
    ): MemberAction = super.timeout(timeout, unit) as MemberAction

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): MemberAction = super.deadline(timestamp) as MemberAction

    @Nonnull
    override fun getAccessToken(): String = accessToken

    @Nonnull
    override fun getUserId(): String = userId

    override fun getUser(): User? = jda.getUserById(userId)

    @Nonnull
    override fun getGuild(): Guild = guild

    @Nonnull
    @CheckReturnValue
    override fun setNickname(nick: String?): MemberActionImpl {
        var nick = nick
        if (nick != null) {
            if (Helpers.isBlank(nick)) {
                this.nick = null
                return this
            }
            Checks.notLonger(nick, MAX_NICKNAME_LENGTH, "Nickname")
        }
        this.nick = nick
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setRoles(roles: Collection<Role>?): MemberActionImpl {
        if (roles == null) {
            this.roles = null
            return this
        }
        val newRoles = HashSet<Role>(roles.size)
        for (role in roles) {
            checkAndAdd(newRoles, role)
        }
        this.roles = newRoles
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setRoles(vararg roles: Role): MemberActionImpl {
        val newRoles = HashSet<Role>(roles.size)
        for (role in roles) {
            checkAndAdd(newRoles, role)
        }
        this.roles = newRoles
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setMute(mute: Boolean): MemberActionImpl {
        this.mute = mute
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setDeafen(deaf: Boolean): MemberActionImpl {
        this.deaf = deaf
        return this
    }

    override fun finalizeData(): RequestBody? {
        val json = DataObject.empty()
        json.put("access_token", accessToken)
        if (nick != null) {
            json.put("nick", nick)
        }
        if (roles != null && !roles!!.isEmpty()) {
            json.put(
                "roles",
                roles!!.stream().map { it.id }.collect(
                    java.util.stream.Collectors
                        .toList(),
                ),
            )
        }
        json.put("mute", mute)
        json.put("deaf", deaf)
        return getRequestBody(json)
    }

    private fun checkAndAdd(
        newRoles: MutableSet<Role>,
        role: Role,
    ) {
        Checks.notNull(role, "Role")
        Checks.check(role.guild == guild, "Roles must all be from the same guild")
        newRoles.add(role)
    }
}
