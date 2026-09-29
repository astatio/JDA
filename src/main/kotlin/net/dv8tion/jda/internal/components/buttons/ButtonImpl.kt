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

package net.dv8tion.jda.internal.components.buttons

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.Button.ID_MAX_LENGTH
import net.dv8tion.jda.api.components.buttons.Button.LABEL_MAX_LENGTH
import net.dv8tion.jda.api.components.buttons.Button.URL_MAX_LENGTH
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.section.SectionAccessoryComponentUnion
import net.dv8tion.jda.api.entities.SkuSnowflake
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.components.AbstractComponentImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.utils.Checks
import net.dv8tion.jda.internal.utils.EntityString
import java.util.Objects
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ButtonImpl :
    AbstractComponentImpl,
    Button,
    ActionRowChildComponentUnion,
    SectionAccessoryComponentUnion {
    private val customId: String?
    private val uniqueId: Int
    private val label: String
    private val style: ButtonStyle
    private val url: String?
    private val sku: SkuSnowflake?
    private val disabled: Boolean
    private val emoji: EmojiUnion?

    constructor(data: DataObject) : this(
        data.getString("custom_id", null),
        data.getInt("id", -1),
        data.getString("label", ""),
        ButtonStyle.fromKey(data.getInt("style")),
        data.getString("url", null),
        if (data.hasKey("sku_id")) SkuSnowflake.fromId(data.getLong("sku_id")) else null,
        data.getBoolean("disabled"),
        data.optObject("emoji").map { EntityBuilder.createEmoji(it) }.orElse(null),
    )

    constructor(customId: String?, label: String?, style: ButtonStyle, disabled: Boolean, emoji: Emoji?) : this(
        customId,
        label,
        style,
        null,
        null,
        disabled,
        emoji,
    )

    constructor(
        customId: String?,
        label: String?,
        style: ButtonStyle,
        url: String?,
        sku: SkuSnowflake?,
        disabled: Boolean,
        emoji: Emoji?,
    ) : this(customId, -1, label, style, url, sku, disabled, emoji)

    constructor(
        customId: String?,
        uniqueId: Int,
        label: String?,
        style: ButtonStyle,
        url: String?,
        sku: SkuSnowflake?,
        disabled: Boolean,
        emoji: Emoji?,
    ) {
        this.customId = customId
        this.uniqueId = uniqueId
        this.label = label ?: ""
        this.style = style
        this.url = url
        this.sku = sku
        this.disabled = disabled
        this.emoji = emoji as EmojiUnion?
    }

    fun checkValid(): ButtonImpl {
        Checks.notNull(style, "Style")
        Checks.notLonger(label, LABEL_MAX_LENGTH, "Label")

        when (style) {
            ButtonStyle.PRIMARY,
            ButtonStyle.SECONDARY,
            ButtonStyle.SUCCESS,
            ButtonStyle.DANGER,
            -> {
                Checks.check(url == null, "Cannot set an URL on action buttons")
                Checks.check(sku == null, "Cannot set an SKU on action buttons")
                Checks.check(emoji != null || label.isNotEmpty(), "Action buttons must have either an emoji or label")
                Checks.notEmpty(customId, "Id")
                Checks.notLonger(customId, ID_MAX_LENGTH, "Id")
            }

            ButtonStyle.LINK -> {
                Checks.check(customId == null, "Cannot set an ID on link buttons")
                Checks.check(url != null, "You must set an URL on link buttons")
                Checks.check(sku == null, "Cannot set an SKU on link buttons")
                Checks.check(emoji != null || label.isNotEmpty(), "Link buttons must have either an emoji or label")
                Checks.notEmpty(url, "URL")
                Checks.notLonger(url, URL_MAX_LENGTH, "URL")
            }

            ButtonStyle.PREMIUM -> {
                Checks.check(customId == null, "Cannot set an ID on premium buttons")
                Checks.check(url == null, "Cannot set an URL on premium buttons")
                Checks.check(emoji == null, "Cannot set an emoji on premium buttons")
                Checks.check(label.isEmpty(), "Cannot set a label on premium buttons")
                Checks.notNull(sku, "SKU")
            }

            ButtonStyle.UNKNOWN -> throw IllegalArgumentException("Cannot make button with unknown style!")
        }

        return this
    }

    @Nonnull
    override fun getType(): Component.Type = Component.Type.BUTTON

    @Nonnull
    override fun withUniqueId(uniqueId: Int): ButtonImpl = super<Button>.withUniqueId(uniqueId) as ButtonImpl

    @Nullable
    override fun getCustomId(): String? = customId

    override fun getUniqueId(): Int = uniqueId

    @Nonnull
    override fun getLabel(): String = label

    @Nonnull
    override fun getStyle(): ButtonStyle = style

    @Nullable
    override fun getUrl(): String? = url

    @Nullable
    override fun getSku(): SkuSnowflake? = sku

    @Nullable
    override fun getEmoji(): EmojiUnion? = emoji

    override fun isDisabled(): Boolean = disabled

    @Nonnull
    override fun toData(): DataObject {
        val json = DataObject.empty()
        json.put("type", 2)
        if (label.isNotEmpty()) {
            json.put("label", label)
        }
        json.put("style", style.key)
        json.put("disabled", disabled)
        if (emoji != null) {
            json.put("emoji", emoji)
        }
        if (url != null) {
            json.put("url", url)
        } else if (customId != null) {
            json.put("custom_id", customId)
        } else {
            json.put("sku_id", sku!!.id)
        }
        if (uniqueId >= 0) {
            json.put("id", uniqueId)
        }
        return json
    }

    override fun hashCode(): Int = Objects.hash(customId, label, style, url, sku, disabled, emoji)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ButtonImpl) {
            return false
        }
        return Objects.equals(other.customId, customId) &&
            Objects.equals(other.label, label) &&
            Objects.equals(other.url, url) &&
            Objects.equals(other.sku, sku) &&
            Objects.equals(other.emoji, emoji) &&
            other.disabled == disabled &&
            other.style == style
    }

    override fun toString(): String {
        val entityString = EntityString(this).setName(label).addMetadata("id", uniqueId)
        if (customId != null) {
            entityString.addMetadata("custom id", customId)
        }
        if (url != null) {
            entityString.addMetadata("url", url)
        }
        if (sku != null) {
            entityString.addMetadata("sku", sku.id)
        }

        return entityString.toString()
    }
}
