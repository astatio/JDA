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

package net.dv8tion.jda.internal.entities.sticker

import net.dv8tion.jda.api.entities.sticker.StandardSticker
import net.dv8tion.jda.api.entities.sticker.StickerPack
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Collections
import javax.annotation.Nonnull

class StickerPackImpl(
    private val id: Long,
    stickers: List<StandardSticker>,
    private val name: String,
    private val description: String,
    private val coverId: Long,
    private val bannerId: Long,
    private val skuId: Long,
) : StickerPack {
    private val stickers: List<StandardSticker> = Collections.unmodifiableList(stickers)

    override fun getIdLong(): Long = id

    @Nonnull
    override fun getStickers(): List<StandardSticker> = stickers

    @Nonnull
    override fun getName(): String = name

    @Nonnull
    override fun getDescription(): String = description

    override fun getCoverIdLong(): Long = coverId

    override fun getBannerIdLong(): Long = bannerId

    override fun getSkuIdLong(): Long = skuId

    override fun toString(): String = EntityString(this).setName(name).toString()

    override fun hashCode(): Int = java.lang.Long.hashCode(id)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is StickerPackImpl) {
            return false
        }
        return id == other.id
    }
}
