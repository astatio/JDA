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

package net.dv8tion.jda.internal.interactions.component;

import net.dv8tion.jda.api.entities.SkuSnowflake;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import net.dv8tion.jda.internal.utils.Checks;
import net.dv8tion.jda.internal.utils.EntityString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class ButtonImpl implements Button
{
    private final String id;
    private final String label;
    private final ButtonStyle style;
    private final String url;
    private final SkuSnowflake sku;
    private final boolean disabled;
    private final EmojiUnion emoji;

    public ButtonImpl(DataObject data)
    {
        this(
            data.getString("custom_id", null),
            data.getString("label", ""),
            ButtonStyle.fromKey(data.getInt("style")),
            data.getString("url", null),
            data.hasKey("sku_id") ? SkuSnowflake.fromId(data.getLong("sku_id")) : null,
            data.getBoolean("disabled"),
            data.optObject("emoji").map(EntityBuilder::createEmoji).orElse(null));
    }

    public ButtonImpl(String id, String label, ButtonStyle style, boolean disabled, Emoji emoji)
    {
        this(id, label, style, null, null, disabled, emoji);
    }

    public ButtonImpl(String id, String label, ButtonStyle style, String url, SkuSnowflake sku, boolean disabled, Emoji emoji)
    {
        this.id = id;
        this.label = label == null ? "" : label;
        this.style = style;
        this.url = url;
        this.sku = sku;
        this.disabled = disabled;
        this.emoji = (EmojiUnion) emoji;
    }

    public ButtonImpl checkValid()
    {
        Checks.notNull(style, "Style");
        Checks.notLonger(label, LABEL_MAX_LENGTH, "Label");
        Checks.check(style != ButtonStyle.UNKNOWN, "Cannot make button with unknown style!");

        switch (style)
        {
        case PRIMARY:
        case SECONDARY:
        case SUCCESS:
        case DANGER:
            Checks.check(url == null, "Cannot set an URL on action buttons");
            Checks.check(sku == null, "Cannot set an SKU on action buttons");
            Checks.check(emoji != null || !label.isEmpty(), "Action buttons must have either an emoji or label");
            Checks.notEmpty(id, "Id");
            Checks.notLonger(id, ID_MAX_LENGTH, "Id");
            break;
        case LINK:
            Checks.check(id == null, "Cannot set an ID on link buttons");
            Checks.check(url != null, "You must set an URL on link buttons");
            Checks.check(sku == null, "Cannot set an SKU on link buttons");
            Checks.check(emoji != null || !label.isEmpty(), "Link buttons must have either an emoji or label");
            Checks.notEmpty(url, "URL");
            Checks.notLonger(url, URL_MAX_LENGTH, "URL");
            break;
        case PREMIUM:
            Checks.check(id == null, "Cannot set an ID on premium buttons");
            Checks.check(url == null, "Cannot set an URL on premium buttons");
            Checks.check(emoji == null, "Cannot set an emoji on premium buttons");
            Checks.check(label.isEmpty(), "Cannot set a label on premium buttons");
            Checks.notNull(sku, "SKU");
            break;
        }

        return this;
    }

    @Nonnull
    @Override
    public Type getType()
    {
        return Type.BUTTON;
    }

    @Nullable
    @Override
    public String getId()
    {
        return id;
    }

    @Nonnull
    @Override
    public String getLabel()
    {
        return label;
    }

    @Nonnull
    @Override
    public ButtonStyle getStyle()
    {
        return style;
    }

    @Nullable
    @Override
    public String getUrl()
    {
        return url;
    }

    @Nullable
    @Override
    public SkuSnowflake getSku()
    {
        return sku;
    }

    @Nullable
    @Override
    public EmojiUnion getEmoji()
    {
        return emoji;
    }

    @Override
    public boolean isDisabled()
    {
        return disabled;
    }

    @Nonnull
    @Override
    public DataObject toData()
    {
        DataObject json = DataObject.empty();
        json.put("type", 2);
        if (!label.isEmpty())
            json.put("label", label);
        json.put("style", style.getKey());
        json.put("disabled", disabled);
        if (emoji != null)
            json.put("emoji", emoji);
        if (url != null)
            json.put("url", url);
        else if (id != null)
            json.put("custom_id", id);
        else
            json.put("sku_id", sku.getId());
        return json;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, label, style, url, disabled, emoji);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this) return true;
        if (!(obj instanceof ButtonImpl)) return false;
        ButtonImpl other = (ButtonImpl) obj;
        return Objects.equals(other.id, id)
            && Objects.equals(other.label, label)
            && Objects.equals(other.url, url)
            && Objects.equals(other.emoji, emoji)
            && other.disabled == disabled
            && other.style == style;
    }

    @Override
    public String toString()
    {
        return new EntityString(this)
                .setName(label)
                .addMetadata("id", id)
                .toString();
    }
}
