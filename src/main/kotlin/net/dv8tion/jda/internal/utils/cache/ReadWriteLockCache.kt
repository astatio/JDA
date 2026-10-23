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

import net.dv8tion.jda.api.utils.MiscUtil
import net.dv8tion.jda.internal.utils.UnlockHook
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.NavigableSet
import java.util.concurrent.locks.ReentrantReadWriteLock

abstract class ReadWriteLockCache<T> {
    @JvmField
    protected val lock: ReentrantReadWriteLock = ReentrantReadWriteLock()

    @JvmField
    protected var cachedList: WeakReference<List<T>>? = null

    @JvmField
    protected var cachedSet: WeakReference<Set<T>>? = null

    fun writeLock(): UnlockHook {
        if (lock.readHoldCount > 0) {
            throw IllegalStateException("Unable to acquire write-lock while holding read-lock!")
        }
        val writeLock = lock.writeLock()
        MiscUtil.tryLock(writeLock)
        onAcquireWriteLock()
        clearCachedLists()
        return UnlockHook(writeLock)
    }

    fun readLock(): UnlockHook {
        val readLock = lock.readLock()
        MiscUtil.tryLock(readLock)
        onAcquireReadLock()
        return UnlockHook(readLock)
    }

    fun clearCachedLists() {
        cachedList = null
        cachedSet = null
    }

    protected open fun onAcquireWriteLock() {}

    protected open fun onAcquireReadLock() {}

    protected fun getCachedList(): List<T>? = cachedList?.get()

    protected fun getCachedSet(): Set<T>? = cachedSet?.get()

    protected fun cache(list: List<T>): List<T> {
        val result = Collections.unmodifiableList(list)
        cachedList = WeakReference(result)
        return result
    }

    protected fun cache(set: Set<T>): Set<T> {
        val result = Collections.unmodifiableSet(set)
        cachedSet = WeakReference(result)
        return result
    }

    protected fun cache(set: NavigableSet<T>): NavigableSet<T> {
        val result = Collections.unmodifiableNavigableSet(set)
        cachedSet = WeakReference(result)
        return result
    }
}
