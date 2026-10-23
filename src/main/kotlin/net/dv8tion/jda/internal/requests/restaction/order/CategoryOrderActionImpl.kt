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

import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.requests.restaction.order.CategoryOrderAction
import net.dv8tion.jda.internal.utils.Checks
import java.util.stream.Collectors
import javax.annotation.Nonnull

open class CategoryOrderActionImpl(
    category: Category,
    bucket: Int,
) : ChannelOrderActionImpl(category.guild, bucket, getChannelsOfType(category, bucket)),
    CategoryOrderAction {
    @JvmField
    protected val category: Category = category

    @Nonnull
    override fun getCategory(): Category = category

    override fun validateInput(entity: GuildChannel) {
        Checks.notNull(entity, "Provided channel")
        Checks.check(entity is ICategorizableChannel, "Provided channel is not an ICategorizableChannel")
        Checks.check(
            getCategory() == (entity as ICategorizableChannel).parentCategory,
            "Provided channel's Category is not this Category!",
        )
        Checks.check(orderList.contains(entity), "Provided channel is not in the list of orderable channels!")
    }

    companion object {
        @Nonnull
        private fun getChannelsOfType(
            category: Category,
            bucket: Int,
        ): Collection<GuildChannel> {
            Checks.notNull(category, "Category")
            return ChannelOrderActionImpl
                .getChannelsOfType(category.guild, bucket)
                .stream()
                .filter { ICategorizableChannel::class.java.isInstance(it) }
                .map { ICategorizableChannel::class.java.cast(it) }
                .filter { it.parentCategory == category }
                .sorted()
                .collect(Collectors.toList())
        }
    }
}
