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
import net.dv8tion.jda.api.entities.SelfUser
import net.dv8tion.jda.api.managers.AccountManager
import net.dv8tion.jda.api.requests.Request
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull

private const val USER_NAME_MAX_LENGTH = 32

class AccountManagerImpl(
    @JvmField protected val selfUser: SelfUser,
) : ManagerBase<AccountManager>(selfUser.getJDA(), Route.Self.MODIFY_SELF.compile()),
    AccountManager {
    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var name: String? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var avatar: Icon? = null

    // Kept protected to match the Java field shape, even though this class is final
    @Suppress("ProtectedMemberInFinalClass")
    @JvmField
    protected var banner: Icon? = null

    @Nonnull
    override fun getSelfUser(): SelfUser = selfUser

    @Nonnull
    @CheckReturnValue
    override fun reset(fields: Long): AccountManagerImpl {
        super.reset(fields)
        if (fields and AccountManager.AVATAR == AccountManager.AVATAR) {
            avatar = null
        }
        if (fields and AccountManager.BANNER == AccountManager.BANNER) {
            banner = null
        }
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(
        @Nonnull vararg fields: Long,
    ): AccountManagerImpl {
        super.reset(*fields)
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun reset(): AccountManagerImpl {
        super.reset()
        avatar = null
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setName(
        @Nonnull name: String,
    ): AccountManagerImpl {
        Checks.notBlank(name, "Name")
        val trimmed = name.trim()
        Checks.notEmpty(trimmed, "Name")
        Checks.notLonger(trimmed, USER_NAME_MAX_LENGTH, "Name")
        this.name = trimmed
        set = set or AccountManager.NAME
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setAvatar(avatar: Icon?): AccountManagerImpl {
        this.avatar = avatar
        set = set or AccountManager.AVATAR
        return this
    }

    @Nonnull
    @CheckReturnValue
    override fun setBanner(banner: Icon?): AccountManager {
        this.banner = banner
        set = set or AccountManager.BANNER
        return this
    }

    override fun finalizeData(): RequestBody {
        val body = DataObject.empty()

        if (shouldUpdate(AccountManager.NAME)) {
            body.put("username", name)
        }
        if (shouldUpdate(AccountManager.AVATAR)) {
            body.put("avatar", avatar?.getEncoding())
        }
        if (shouldUpdate(AccountManager.BANNER)) {
            body.put("banner", banner?.getEncoding())
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
