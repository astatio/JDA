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

package net.dv8tion.jda.internal.utils.cache

import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView
import java.util.function.Function

open class SnowflakeCacheViewImpl<T : ISnowflake>(
    type: Class<T>,
    nameMapper: Function<T, String>?,
) : AbstractCacheView<T>(type, nameMapper),
    SnowflakeCacheView<T> {
    override fun getElementById(id: Long): T? {
        if (elements.isEmpty) {
            return null
        }
        return get(id)
    }
}
