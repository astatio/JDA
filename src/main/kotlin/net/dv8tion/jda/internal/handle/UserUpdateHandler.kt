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

package net.dv8tion.jda.internal.handle

import net.dv8tion.jda.api.events.self.SelfUpdateAvatarEvent
import net.dv8tion.jda.api.events.self.SelfUpdateDiscriminatorEvent
import net.dv8tion.jda.api.events.self.SelfUpdateGlobalNameEvent
import net.dv8tion.jda.api.events.self.SelfUpdateMFAEvent
import net.dv8tion.jda.api.events.self.SelfUpdateNameEvent
import net.dv8tion.jda.api.events.self.SelfUpdateVerifiedEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.SelfUserImpl
import java.util.Objects

class UserUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    protected override fun handleInternally(content: DataObject): Long? {
        val self = getJDA().getSelfUser() as SelfUserImpl

        val name = content.getString("username")
        val discriminator = content.getString("discriminator")
        val globalName = content.getString("global_name", null)
        val avatarId = content.getString("avatar", null)
        val verified: Boolean? = if (content.hasKey("verified")) content.getBoolean("verified") else null
        val mfaEnabled: Boolean? = if (content.hasKey("mfa_enabled")) content.getBoolean("mfa_enabled") else null

        if (!Objects.equals(name, self.getName())) {
            val oldName = self.getName()
            self.setName(name)
            getJDA().handleEvent(SelfUpdateNameEvent(getJDA(), responseNumber, oldName))
        }

        if (!Objects.equals(discriminator, self.getDiscriminator())) {
            val oldDiscriminator = self.getDiscriminator()
            self.setDiscriminator(discriminator.toShort())
            getJDA().handleEvent(SelfUpdateDiscriminatorEvent(getJDA(), responseNumber, oldDiscriminator))
        }

        if (!Objects.equals(globalName, self.getGlobalName())) {
            val oldGlobalName = self.getGlobalName()
            self.setGlobalName(globalName)
            getJDA().handleEvent(SelfUpdateGlobalNameEvent(getJDA(), responseNumber, oldGlobalName))
        }

        if (!Objects.equals(avatarId, self.getAvatarId())) {
            val oldAvatarId = self.getAvatarId()
            self.setAvatarId(avatarId)
            getJDA().handleEvent(SelfUpdateAvatarEvent(getJDA(), responseNumber, oldAvatarId))
        }

        if (verified != null && verified != self.isVerified()) {
            val wasVerified = self.isVerified()
            self.setVerified(verified)
            getJDA().handleEvent(SelfUpdateVerifiedEvent(getJDA(), responseNumber, wasVerified))
        }

        if (mfaEnabled != null && mfaEnabled != self.isMfaEnabled()) {
            val wasMfaEnabled = self.isMfaEnabled()
            self.setMfaEnabled(mfaEnabled)
            getJDA().handleEvent(SelfUpdateMFAEvent(getJDA(), responseNumber, wasMfaEnabled))
        }
        return null
    }
}
