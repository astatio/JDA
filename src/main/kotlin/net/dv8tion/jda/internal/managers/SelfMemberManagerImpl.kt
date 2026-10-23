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

import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.managers.SelfMemberManager
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

class SelfMemberManagerImpl(
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected val selfMember: SelfMember,
) : ManagerBase<SelfMemberManager>(
        selfMember.getJDA(),
        Route.Guilds.MODIFY_SELF.compile(selfMember.getGuild().getId()),
    ),
    SelfMemberManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var nickname: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var avatar: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var banner: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var bio: String? = null

    @Nonnull
    override fun getMember(): SelfMember = selfMember

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): SelfMemberManagerImpl {
        super.reset(fields)
        if (fields and SelfMemberManager.NICKNAME == SelfMemberManager.NICKNAME) {
            nickname = null
        }
        if (fields and SelfMemberManager.AVATAR == SelfMemberManager.AVATAR) {
            avatar = null
        }
        if (fields and SelfMemberManager.BANNER == SelfMemberManager.BANNER) {
            banner = null
        }
        if (fields and SelfMemberManager.BIO == SelfMemberManager.BIO) {
            bio = null
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): SelfMemberManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): SelfMemberManagerImpl {
        super.reset()
        nickname = null
        avatar = null
        banner = null
        bio = null
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setNickname(
        @Nullable nickname: String?,
    ): SelfMemberManagerImpl {
        if (nickname != null) {
            Checks.notLonger(nickname, Member.MAX_NICKNAME_LENGTH, "Nickname")
        }
        this.nickname = nickname
        set = set or SelfMemberManager.NICKNAME
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setAvatar(
        @Nullable avatar: Icon?,
    ): SelfMemberManagerImpl {
        this.avatar = avatar
        set = set or SelfMemberManager.AVATAR
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setBanner(
        @Nullable banner: Icon?,
    ): SelfMemberManagerImpl {
        this.banner = banner
        set = set or SelfMemberManager.BANNER
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setBio(
        @Nullable bio: String?,
    ): SelfMemberManagerImpl {
        if (bio != null) {
            Checks.notLonger(bio, SelfMember.MAX_BIO_LENGTH, "Bio")
        }
        this.bio = bio
        set = set or SelfMemberManager.BIO
        return this
    }

    override fun finalizeData(): RequestBody {
        val body = DataObject.empty()

        if (shouldUpdate(SelfMemberManager.NICKNAME)) {
            body.put("nick", nickname)
        }
        if (shouldUpdate(SelfMemberManager.AVATAR)) {
            body.put("avatar", avatar?.getEncoding())
        }
        if (shouldUpdate(SelfMemberManager.BANNER)) {
            body.put("banner", banner?.getEncoding())
        }
        if (shouldUpdate(SelfMemberManager.BIO)) {
            body.put("bio", bio)
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
}
