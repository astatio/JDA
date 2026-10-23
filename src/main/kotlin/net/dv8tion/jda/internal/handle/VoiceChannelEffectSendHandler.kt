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

import net.dv8tion.jda.api.entities.channel.VoiceChannelEffect
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.events.channel.VoiceChannelEffectSendEvent
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl

class VoiceChannelEffectSendHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }

        val guild = getJDA().getGuildById(guildId) as GuildImpl?
        if (guild == null) {
            getJDA()
                .getEventCache()
                .cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            return null
        }

        val channelId = content.getUnsignedLong("channel_id")
        val channel: VoiceChannel? = guild.getVoiceChannelById(channelId)
        if (channel == null) {
            getJDA()
                .getEventCache()
                .cache(EventCache.Type.CHANNEL, channelId, responseNumber, allContent, this::handle)
            return null
        }

        val userId = content.getUnsignedLong("user_id")
        val emoji: EmojiUnion? = content.optObject("emoji").map(EntityBuilder::createEmoji).orElse(null)
        val animation: VoiceChannelEffect.Animation? =
            content
                .opt("animation_type")
                .map { raw: Any -> (raw as Number).toInt() }
                .map { rawAnimationType: Int ->
                    val animationId = content.getUnsignedLong("animation_id")
                    val type = VoiceChannelEffect.Animation.Type.fromValue(rawAnimationType)
                    VoiceChannelEffect.Animation(animationId, type)
                }.orElse(null)
        val soundboardSoundId = content.getUnsignedLong("sound_id", 0)
        val soundVolume = content.getDouble("sound_volume", 0.0)

        val effect = VoiceChannelEffect(channel, userId, emoji, animation, soundboardSoundId, soundVolume)

        api.handleEvent(VoiceChannelEffectSendEvent(api, responseNumber, effect))

        return null
    }
}
