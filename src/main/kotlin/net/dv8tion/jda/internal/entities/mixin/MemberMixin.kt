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

package net.dv8tion.jda.internal.entities.mixin

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.RoleColors
import net.dv8tion.jda.internal.entities.detached.mixin.IDetachableEntityMixin
import javax.annotation.Nonnull

interface MemberMixin<T : MemberMixin<T>> :
    Member,
    IDetachableEntityMixin {
    fun setNickname(nickname: String): T

    fun setAvatarId(avatarId: String): T

    fun setBannerId(bannerId: String): T

    fun setJoinDate(joinDate: Long): T

    fun setBoostDate(boostDate: Long): T

    fun setTimeOutEnd(time: Long): T

    fun setPending(pending: Boolean): T

    fun setFlags(flags: Int): T

    @Nonnull
    override fun getColors(): RoleColors {
        for (role in roles) {
            val roleColors = role.colors
            if (!roleColors.isDefault) {
                return roleColors
            }
        }
        return RoleColors.DEFAULT
    }
}
