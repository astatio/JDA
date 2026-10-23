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

package net.dv8tion.jda.internal.requests.restaction

import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.data.SerializableData
import javax.annotation.Nonnull

class PermOverrideData : SerializableData {
    @JvmField
    val type: Int

    @JvmField
    val id: Long

    @JvmField
    val allow: Long

    @JvmField
    val deny: Long

    constructor(type: Int, id: Long, allow: Long, deny: Long) {
        this.type = type
        this.id = id
        this.allow = allow
        this.deny = deny and allow.inv()
    }

    constructor(override: PermissionOverride) {
        this.id = override.idLong
        this.type = if (override.isMemberOverride) MEMBER_TYPE else ROLE_TYPE
        this.allow = override.allowedRaw
        this.deny = override.deniedRaw
    }

    @Nonnull
    override fun toData(): DataObject {
        val o = DataObject.empty()
        o.put("type", type)
        o.put("id", id)
        o.put("allow", allow)
        o.put("deny", deny)
        return o
    }

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is PermOverrideData) {
            return false
        }

        return other.id == this.id
    }

    companion object {
        const val ROLE_TYPE: Int = 0
        const val MEMBER_TYPE: Int = 1
    }
}
