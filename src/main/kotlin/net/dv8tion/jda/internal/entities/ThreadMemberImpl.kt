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
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.ThreadMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl
import net.dv8tion.jda.internal.utils.EntityString
import net.dv8tion.jda.internal.utils.Helpers
import java.time.OffsetDateTime
import javax.annotation.Nonnull

class ThreadMemberImpl(
    member: Member,
    private val thread: ThreadChannelImpl,
) : ThreadMember {
    private val api: JDA = member.jda

    private var member: Member = member
    private var joinedTimestamp: Long = 0

    @Nonnull
    override fun getJDA(): JDA = api

    @Nonnull
    override fun getGuild(): Guild = thread.guild

    @Nonnull
    override fun getThread(): ThreadChannel = this.thread

    @Nonnull
    override fun getUser(): User = member.user

    @Nonnull
    override fun getMember(): Member = member

    @Nonnull
    override fun getTimeJoined(): OffsetDateTime = Helpers.toOffset(joinedTimestamp)

    @Nonnull
    override fun getAsMention(): String = member.asMention

    override fun getIdLong(): Long = member.idLong

    // ===== Setters =======

    fun setJoinedTimestamp(joinedTimestamp: Long): ThreadMemberImpl {
        this.joinedTimestamp = joinedTimestamp
        return this
    }

    override fun toString(): String = EntityString(this).addMetadata("member", getMember()).toString()
}
