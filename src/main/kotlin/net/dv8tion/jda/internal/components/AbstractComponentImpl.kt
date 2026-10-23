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

package net.dv8tion.jda.internal.components

import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.checkbox.Checkbox
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroup
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.filedisplay.FileDisplay
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.mediagallery.MediaGallery
import net.dv8tion.jda.api.components.radiogroup.RadioGroup
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.thumbnail.Thumbnail
import net.dv8tion.jda.api.utils.data.SerializableData
import net.dv8tion.jda.internal.utils.UnionUtil
import javax.annotation.Nonnull

abstract class AbstractComponentImpl : SerializableData {
    // -- Union hooks --

    @Nonnull
    fun asActionRow(): ActionRow = toComponentType(ActionRow::class.java)

    @Nonnull
    fun asButton(): Button = toComponentType(Button::class.java)

    @Nonnull
    fun asStringSelectMenu(): StringSelectMenu = toComponentType(StringSelectMenu::class.java)

    @Nonnull
    fun asEntitySelectMenu(): EntitySelectMenu = toComponentType(EntitySelectMenu::class.java)

    @Nonnull
    fun asTextInput(): TextInput = toComponentType(TextInput::class.java)

    @Nonnull
    fun asSection(): Section = toComponentType(Section::class.java)

    @Nonnull
    fun asTextDisplay(): TextDisplay = toComponentType(TextDisplay::class.java)

    @Nonnull
    fun asMediaGallery(): MediaGallery = toComponentType(MediaGallery::class.java)

    @Nonnull
    fun asThumbnail(): Thumbnail = toComponentType(Thumbnail::class.java)

    @Nonnull
    fun asSeparator(): Separator = toComponentType(Separator::class.java)

    @Nonnull
    fun asFileDisplay(): FileDisplay = toComponentType(FileDisplay::class.java)

    @Nonnull
    fun asContainer(): Container = toComponentType(Container::class.java)

    @Nonnull
    fun asLabel(): Label = toComponentType(Label::class.java)

    @Nonnull
    fun asAttachmentUpload(): AttachmentUpload = toComponentType(AttachmentUpload::class.java)

    @Nonnull
    fun asRadioGroup(): RadioGroup = toComponentType(RadioGroup::class.java)

    @Nonnull
    fun asCheckboxGroup(): CheckboxGroup = toComponentType(CheckboxGroup::class.java)

    @Nonnull
    fun asCheckbox(): Checkbox = toComponentType(Checkbox::class.java)

    protected fun <T : Component> toComponentType(type: Class<T>): T = UnionUtil.safeUnionCast("component", this, type)
}
