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

package net.dv8tion.jda.internal.interactions

import net.dv8tion.jda.api.exceptions.InteractionFailureException
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import javax.annotation.Nonnull

open class DeferrableInteractionImpl(
    jda: JDAImpl,
    data: DataObject,
) : InteractionImpl(jda, data),
    IDeferrableCallback {
    @JvmField
    protected val hook: InteractionHookImpl = InteractionHookImpl(this, jda)

    @Synchronized
    override fun releaseHook(success: Boolean) {
        if (success) {
            hook.ready()
        } else {
            hook.fail(InteractionFailureException())
        }
    }

    @Nonnull
    override fun getHook(): InteractionHook = hook
}
