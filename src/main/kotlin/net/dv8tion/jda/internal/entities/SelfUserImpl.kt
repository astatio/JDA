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

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.SelfUser
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.managers.AccountManager
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.managers.AccountManagerImpl
import javax.annotation.Nonnull

class SelfUserImpl(
    id: Long,
    api: JDAImpl,
) : UserImpl(id, api),
    SelfUser {
    private var verified: Boolean = false
    private var mfaEnabled: Boolean = false
    private var applicationId: Long = id

    override fun hasPrivateChannel(): Boolean = false

    override fun getPrivateChannel(): PrivateChannel =
        throw UnsupportedOperationException("You cannot get a PrivateChannel with yourself (SelfUser)")

    @Nonnull
    override fun openPrivateChannel(): CacheRestAction<PrivateChannel> =
        throw UnsupportedOperationException("You cannot open a PrivateChannel with yourself (SelfUser)")

    override fun getApplicationIdLong(): Long = applicationId

    override fun isVerified(): Boolean = verified

    override fun isMfaEnabled(): Boolean = mfaEnabled

    override fun getAllowedFileSize(): Long = Message.MAX_FILE_SIZE.toLong()

    @Nonnull
    override fun getManager(): AccountManager = AccountManagerImpl(this)

    fun setVerified(verified: Boolean): SelfUserImpl {
        this.verified = verified
        return this
    }

    fun setMfaEnabled(enabled: Boolean): SelfUserImpl {
        this.mfaEnabled = enabled
        return this
    }

    fun setApplicationId(id: Long): SelfUserImpl {
        this.applicationId = id
        return this
    }

    companion object {
        @JvmStatic
        fun copyOf(
            other: SelfUserImpl,
            jda: JDAImpl,
        ): SelfUserImpl {
            val selfUser = SelfUserImpl(other.idLong, jda)
            selfUser
                .setName(other.getName())
                .setGlobalName(other.getGlobalName())
                .setAvatarId(other.getAvatarId())
                .setDiscriminator(other.getDiscriminatorInt())
                .setBot(other.isBot())
            return selfUser
                .setVerified(other.verified)
                .setMfaEnabled(other.mfaEnabled)
                .setApplicationId(other.applicationId)
        }
    }
}
