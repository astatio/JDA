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

package net.dv8tion.jda.internal.requests.restaction.order

import gnu.trove.map.TLongLongMap
import gnu.trove.map.hash.TLongLongHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.order.ChannelOrderAction
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.Checks
import okhttp3.RequestBody
import java.util.stream.Collectors
import javax.annotation.Nonnull

open class ChannelOrderActionImpl :
    OrderActionImpl<GuildChannel, ChannelOrderAction>,
    ChannelOrderAction {
    @JvmField
    protected val guild: Guild

    @JvmField
    protected val bucket: Int

    @JvmField
    protected val lockPermissions: TLongSet = TLongHashSet()

    @JvmField
    protected val parent: TLongLongMap = TLongLongHashMap()

    constructor(guild: Guild, bucket: Int) : this(guild, bucket, getChannelsOfType(guild, bucket))

    constructor(guild: Guild, bucket: Int, channels: Collection<GuildChannel>) :
        super(guild.jda, Route.Guilds.MODIFY_CHANNELS.compile(guild.id)) {
        Checks.notNull(channels, "Channels to order")
        Checks.notEmpty(channels, "Channels to order")
        Checks.check(
            channels.stream().allMatch { c -> guild == c.guild },
            "One or more channels are not from the correct guild",
        )
        Checks.check(
            channels.stream().allMatch { c -> c.type.sortBucket == bucket },
            "One or more channels did not match the expected bucket $bucket",
        )

        this.guild = guild
        this.bucket = bucket
        this.orderList.addAll(channels)
    }

    @Nonnull
    override fun getGuild(): Guild = guild

    override fun getSortBucket(): Int = bucket

    @Nonnull
    override fun setCategory(
        category: Category?,
        syncPermissions: Boolean,
    ): ChannelOrderAction {
        val channel = getSelectedEntity()
        if (channel !is ICategorizableChannel && category != null) {
            throw IllegalStateException("Cannot move channel of type " + channel.type + " to category!")
        }
        if (category != null) {
            Checks.check(category.guild == guild, "Category is not from the same guild!")
        }

        val id = channel.idLong
        parent.put(id, if (category == null) 0 else category.idLong)
        if (syncPermissions) {
            lockPermissions.add(id)
        } else {
            lockPermissions.remove(id)
        }
        return this
    }

    override fun finalizeData(): RequestBody {
        val self: Member = guild.selfMember
        if (!self.hasPermission(Permission.MANAGE_CHANNEL)) {
            throw InsufficientPermissionException(guild, Permission.MANAGE_CHANNEL)
        }
        val array = DataArray.empty()
        for (i in orderList.indices) {
            val chan = orderList[i]
            val json = DataObject.empty().put("id", chan.id).put("position", i)
            if (parent.containsKey(chan.idLong)) {
                val parentId = parent.get(chan.idLong)
                json.put("parent_id", if (parentId == 0L) null else parentId)
                json.put("lock_permissions", lockPermissions.contains(chan.idLong))
            }
            array.add(json)
        }

        return getRequestBody(array)
    }

    override fun validateInput(entity: GuildChannel) {
        Checks.check(entity.guild == guild, "Provided channel is not from this Guild!")
        Checks.check(orderList.contains(entity), "Provided channel is not in the list of orderable channels!")
    }

    companion object {
        @JvmStatic
        protected fun getChannelsOfType(
            guild: Guild,
            bucket: Int,
        ): Collection<GuildChannel> =
            guild.channels
                .stream()
                .filter { it.type.sortBucket == bucket }
                .sorted()
                .collect(Collectors.toList())
    }
}
