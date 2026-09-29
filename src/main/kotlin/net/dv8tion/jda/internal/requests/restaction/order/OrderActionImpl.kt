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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.order.OrderAction
import net.dv8tion.jda.internal.requests.RestActionImpl
import net.dv8tion.jda.internal.utils.Checks
import java.util.Collections
import java.util.Comparator
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import javax.annotation.Nonnull

abstract class OrderActionImpl<T, M : OrderAction<T, M>> :
    RestActionImpl<Void>,
    OrderAction<T, M> {
    @JvmField
    protected val orderList: MutableList<T> = ArrayList()

    @JvmField
    protected val ascendingOrder: Boolean

    @JvmField
    protected var selectedPosition: Int = -1

    constructor(api: JDA, route: Route.CompiledRoute) : this(api, true, route)

    constructor(api: JDA, ascendingOrder: Boolean, route: Route.CompiledRoute) : super(api, route) {
        this.ascendingOrder = ascendingOrder
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun setCheck(checks: BooleanSupplier?): M = super.setCheck(checks) as M

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun timeout(
        timeout: Long,
        unit: TimeUnit,
    ): M = super.timeout(timeout, unit) as M

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun deadline(timestamp: Long): M = super.deadline(timestamp) as M

    override fun isAscendingOrder(): Boolean = ascendingOrder

    @Nonnull
    override fun getCurrentOrder(): List<T> = Collections.unmodifiableList(orderList)

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun selectPosition(selectedPosition: Int): M {
        Checks.notNegative(selectedPosition, "Provided selectedPosition")
        Checks.check(
            selectedPosition < orderList.size,
            "Provided selectedPosition is too big and is out of bounds. selectedPosition: $selectedPosition",
        )

        this.selectedPosition = selectedPosition

        return this as M
    }

    @Nonnull
    override fun selectPosition(selectedEntity: T & Any): M {
        Checks.notNull(selectedEntity, "Channel")
        validateInput(selectedEntity)

        return selectPosition(orderList.indexOf(selectedEntity))
    }

    override fun getSelectedPosition(): Int = selectedPosition

    @Nonnull
    override fun getSelectedEntity(): T & Any {
        if (selectedPosition == -1) {
            throw IllegalStateException("No position has been selected yet")
        }

        return orderList[selectedPosition]!!
    }

    @Nonnull
    override fun moveUp(amount: Int): M {
        Checks.notNegative(amount, "Provided amount")
        if (selectedPosition == -1) {
            throw IllegalStateException("Cannot move until an item has been selected. Use #selectPosition first.")
        }
        if (ascendingOrder) {
            Checks.check(
                selectedPosition - amount >= 0,
                "Amount provided to move up is too large and would be out of bounds." +
                    "Selected position: " + selectedPosition + " Amount: " + amount +
                    " Largest Position: " + orderList.size,
            )
        } else {
            Checks.check(
                selectedPosition + amount < orderList.size,
                "Amount provided to move up is too large and would be out of bounds." +
                    "Selected position: " + selectedPosition + " Amount: " + amount +
                    " Largest Position: " + orderList.size,
            )
        }

        return if (ascendingOrder) {
            moveTo(selectedPosition - amount)
        } else {
            moveTo(selectedPosition + amount)
        }
    }

    @Nonnull
    override fun moveDown(amount: Int): M {
        Checks.notNegative(amount, "Provided amount")
        if (selectedPosition == -1) {
            throw IllegalStateException("Cannot move until an item has been selected. Use #selectPosition first.")
        }

        if (ascendingOrder) {
            Checks.check(
                selectedPosition + amount < orderList.size,
                "Amount provided to move down is too large and would be out of bounds. " +
                    "Selected position: " + selectedPosition + " Amount: " + amount +
                    " Largest Position: " + orderList.size,
            )
        } else {
            Checks.check(
                selectedPosition - amount >= 0,
                "Amount provided to move down is too large and would be out of bounds. " +
                    "Selected position: " + selectedPosition + " Amount: " + amount +
                    " Largest Position: " + orderList.size,
            )
        }

        return if (ascendingOrder) {
            moveTo(selectedPosition + amount)
        } else {
            moveTo(selectedPosition - amount)
        }
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun moveTo(position: Int): M {
        Checks.notNegative(position, "Provided position")
        Checks.check(position < orderList.size, "Provided position is too big and is out of bounds.")
        if (selectedPosition == -1) {
            throw IllegalStateException("Cannot move until an item has been selected. Use #selectPosition first.")
        }

        val selectedItem = orderList.removeAt(selectedPosition)
        orderList.add(position, selectedItem)
        selectedPosition = position

        return this as M
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun moveBelow(other: T & Any): M {
        validateInput(other)
        val index = getCurrentOrder().indexOf(other)
        moveTo(index)
        if (isAscendingOrder()) {
            return moveDown(1)
        }
        return this as M
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun moveAbove(other: T & Any): M {
        validateInput(other)
        val index = getCurrentOrder().indexOf(other)
        moveTo(index)
        if (!isAscendingOrder()) {
            return moveUp(1)
        }
        return this as M
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun swapPosition(swapPosition: Int): M {
        Checks.notNegative(swapPosition, "Provided swapPosition")
        Checks.check(
            swapPosition < orderList.size,
            "Provided swapPosition is too big and is out of bounds. swapPosition: $swapPosition",
        )
        if (selectedPosition == -1) {
            throw IllegalStateException("Cannot move until an item has been selected. Use #selectPosition first.")
        }

        val selectedItem = orderList[selectedPosition]
        val swapItem = orderList[swapPosition]
        orderList[swapPosition] = selectedItem
        orderList[selectedPosition] = swapItem
        selectedPosition = swapPosition

        return this as M
    }

    @Nonnull
    override fun swapPosition(swapEntity: T & Any): M {
        Checks.notNull(swapEntity, "Provided swapEntity")
        validateInput(swapEntity)

        return swapPosition(orderList.indexOf(swapEntity))
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun reverseOrder(): M {
        Collections.reverse(this.orderList)
        return this as M
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun shuffleOrder(): M {
        Collections.shuffle(this.orderList)
        return this as M
    }

    @Nonnull
    @Suppress("UNCHECKED_CAST")
    override fun sortOrder(comparator: Comparator<T>): M {
        Checks.notNull(comparator, "Provided comparator")

        this.orderList.sortWith(comparator)
        return this as M
    }

    protected abstract fun validateInput(entity: T)
}
