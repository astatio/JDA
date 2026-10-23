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

package net.dv8tion.jda.internal.utils

import com.google.errorprone.annotations.FormatMethod
import com.google.errorprone.annotations.FormatString
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import net.dv8tion.jda.api.utils.Result
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jetbrains.annotations.Unmodifiable
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.ArrayList
import java.util.Collections
import java.util.EnumSet
import java.util.HashSet
import java.util.Locale
import java.util.Objects
import java.util.StringJoiner
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.ToLongFunction
import java.util.stream.Collector
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * This class has major inspiration from [Lang 3](https://commons.apache.org/proper/commons-lang/)
 *
 * Specifically StringUtils.java and ExceptionUtils.java
 */
object Helpers {
    private const val MAX_CAUSE_DEPTH = 5
    private val OFFSET: ZoneOffset = ZoneOffset.of("+00:00")

    private val EMPTY_CONSUMER: Consumer<Any?> = Consumer { }

    @JvmStatic
    fun <T> emptyConsumer(): Consumer<T> {
        @Suppress("UNCHECKED_CAST")
        return EMPTY_CONSUMER as Consumer<T>
    }

    @JvmStatic
    @Nonnull
    fun toOffset(instant: Long): OffsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(instant), OFFSET)

    @JvmStatic
    fun toTimestamp(iso8601String: String): Long {
        val joinedAt: TemporalAccessor = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(iso8601String)
        return Instant.from(joinedAt).toEpochMilli()
    }

    @JvmStatic
    @Nullable
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun toOffsetDateTime(
        @Nullable temporal: TemporalAccessor?,
    ): OffsetDateTime? {
        if (temporal == null) {
            return null
        } else if (temporal is OffsetDateTime) {
            return temporal
        } else {
            val offset =
                try {
                    ZoneOffset.from(temporal)
                } catch (ignore: DateTimeException) {
                    ZoneOffset.UTC
                }
            try {
                val ldt = LocalDateTime.from(temporal)
                return OffsetDateTime.of(ldt, offset)
            } catch (ignore: DateTimeException) {
                try {
                    val instant = Instant.from(temporal)
                    return OffsetDateTime.ofInstant(instant, offset)
                } catch (ex: DateTimeException) {
                    throw DateTimeException(
                        "Unable to obtain OffsetDateTime from TemporalAccessor: $temporal of type ${temporal.javaClass.name}",
                        ex,
                    )
                }
            }
        }
    }

    // locale-safe String#format

    @JvmStatic
    @FormatMethod
    fun format(
        @FormatString format: String,
        vararg args: Any?,
    ): String = java.lang.String.format(Locale.ROOT, format, *args)

    // ## StringUtils ##

    @JvmStatic
    fun isEmpty(seq: CharSequence?): Boolean = seq == null || seq.length == 0

    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun containsWhitespace(seq: CharSequence?): Boolean {
        if (isEmpty(seq)) {
            return false
        }
        for (i in 0 until seq!!.length) {
            if (Character.isWhitespace(seq[i])) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun isBlank(seq: CharSequence?): Boolean {
        if (isEmpty(seq)) {
            return true
        }
        for (i in 0 until seq!!.length) {
            if (!Character.isWhitespace(seq[i])) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun countMatches(
        seq: CharSequence?,
        c: Char,
    ): Int {
        if (isEmpty(seq)) {
            return 0
        }
        var count = 0
        for (i in 0 until seq!!.length) {
            if (seq[i] == c) {
                count++
            }
        }
        return count
    }

    @JvmStatic
    @Nullable
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun truncate(
        input: String?,
        maxWidth: Int,
    ): String? {
        if (input == null) {
            return null
        }
        Checks.notNegative(maxWidth, "maxWidth")
        if (input.length <= maxWidth) {
            return input
        }
        if (maxWidth == 0) {
            return ""
        }
        return input.substring(0, maxWidth)
    }

    @JvmStatic
    @Nonnull
    fun rightPad(
        input: String,
        size: Int,
    ): String {
        val pads = size - input.length
        if (pads <= 0) {
            return input
        }
        val out = StringBuilder(input)
        for (i in pads downTo 1) {
            out.append(' ')
        }
        return out.toString()
    }

    @JvmStatic
    @Nonnull
    fun leftPad(
        input: String,
        size: Int,
    ): String {
        val pads = size - input.length
        if (pads <= 0) {
            return input
        }
        val out = StringBuilder()
        for (i in pads downTo 1) {
            out.append(' ')
        }
        return out.append(input).toString()
    }

    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun isNumeric(input: String?): Boolean {
        if (isEmpty(input)) {
            return false
        }
        for (i in 0 until input!!.length) {
            if (!Character.isDigit(input[i])) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun codePointLength(string: CharSequence): Int = string.codePoints().count().toInt()

    @JvmStatic
    @Nonnull
    fun split(
        input: String,
        match: String,
    ): Array<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < input.length) {
            val j = input.indexOf(match, i)
            if (j == -1) {
                out.add(input.substring(i))
                break
            }

            out.add(input.substring(i, j))
            i = j + match.length
        }

        return out.toTypedArray()
    }

    @JvmStatic
    @Suppress("ReferenceEquality", "StringEquality")
    fun equals(
        a: String?,
        b: String?,
        ignoreCase: Boolean,
    ): Boolean = if (ignoreCase) a === b || (a != null && a.equals(b, ignoreCase = true)) else Objects.equals(a, b)

    // ## CollectionUtils ##

    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReferenceEquality", "ReturnCount")
    fun deepEquals(
        first: Collection<*>?,
        second: Collection<*>?,
    ): Boolean {
        if (first === second) {
            return true
        }
        if (first == null || second == null || first.size != second.size) {
            return false
        }
        val itFirst = first.iterator()
        val itSecond = second.iterator()
        while (itFirst.hasNext()) {
            val elementFirst = itFirst.next()
            val elementSecond = itSecond.next()
            if (!Objects.equals(elementFirst, elementSecond)) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReferenceEquality", "SuspiciousMethodCalls", "ReturnCount")
    fun deepEqualsUnordered(
        first: Collection<*>?,
        second: Collection<*>?,
    ): Boolean {
        if (first === second) {
            return true
        }
        if (first == null || second == null) {
            return false
        }
        return first.size == second.size && second.containsAll(first)
    }

    @JvmStatic
    @Nonnull
    fun <E : Enum<E>> copyEnumSet(
        clazz: Class<E>,
        col: Collection<E>?,
    ): EnumSet<E> = if (col == null || col.isEmpty()) EnumSet.noneOf(clazz) else EnumSet.copyOf(col)

    @JvmStatic
    @SafeVarargs
    fun <T> setOf(vararg elements: T): Set<T> {
        val set: MutableSet<T> = HashSet(elements.size)
        Collections.addAll(set, *elements)
        return set
    }

    @JvmStatic
    @SafeVarargs
    fun <T> listOf(vararg elements: T): List<T> = Collections.unmodifiableList(elements.asList())

    @JvmStatic
    @Nonnull
    fun convertToMap(
        getId: ToLongFunction<DataObject>,
        array: DataArray,
    ): TLongObjectMap<DataObject> {
        val map = TLongObjectHashMap<DataObject>()
        for (i in 0 until array.length()) {
            val obj = array.getObject(i)
            val objId = getId.applyAsLong(obj)
            map.put(objId, obj)
        }
        return map
    }

    @JvmStatic
    @Nonnull
    fun <I, O> tryMap(mapper: Function<I, O>): Function<I, Result<O>> = Function { element -> Result.defer { mapper.apply(element) } }

    @JvmStatic
    @Nonnull
    fun <I, O> mapGracefully(
        stream: Stream<I>,
        mapper: Function<I, O>,
        errorDescription: String,
    ): Stream<O> =
        stream
            .map(tryMap(mapper))
            .peek { result ->
                if (result.isFailure) {
                    JDAImpl.LOG.error(errorDescription, result.failure)
                }
            }.filter { it.isSuccess }
            .map { it.get() }

    // ## ExceptionUtils ##

    @JvmStatic
    @Nonnull
    fun <T : Throwable> appendCause(
        throwable: T,
        cause: Throwable,
    ): T {
        var t: Throwable = throwable

        for (i in 0 until MAX_CAUSE_DEPTH) {
            if (t.cause == null) {
                t.initCause(cause)
                return throwable
            } else {
                t = t.cause!!
            }
        }

        // Exception is too deep, add it on the initial exception as suppressed
        throwable.addSuppressed(cause)
        return throwable
    }

    @JvmStatic
    fun hasCause(
        throwable: Throwable,
        cause: Class<out Throwable>,
    ): Boolean {
        var cursor: Throwable? = throwable
        while (cursor != null) {
            if (cause.isInstance(cursor)) {
                return true
            }
            cursor = cursor.cause
        }
        return false
    }

    @JvmStatic
    @Nonnull
    fun <T> toUnmodifiableList(): Collector<T, *, List<T>> =
        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)

    @JvmStatic
    @Nonnull
    fun <E : Enum<E>> toUnmodifiableEnumSet(enumType: Class<E>): Collector<E, *, Set<E>> =
        Collectors.collectingAndThen(
            Collectors.toCollection { EnumSet.noneOf(enumType) },
            Collections::unmodifiableSet,
        )

    @JvmStatic
    @SafeVarargs
    fun <E : Enum<E>> unmodifiableEnumSet(
        first: E,
        vararg rest: E,
    ): Set<E> = Collections.unmodifiableSet(EnumSet.of(first, *rest))

    @JvmStatic
    @Nonnull
    fun <E> copyAsUnmodifiableList(items: Collection<E>?): @Unmodifiable List<E> =
        if (items == null || items.isEmpty()) Collections.emptyList() else Collections.unmodifiableList(ArrayList(items))

    @JvmStatic
    @Nonnull
    @SafeVarargs
    fun <E> mergeVararg(
        @Nonnull first: E,
        @Nonnull vararg other: E,
    ): List<E> {
        val list = ArrayList<E>(other.size + 1)
        list.add(first)
        Collections.addAll(list, *other)
        return list
    }

    @JvmStatic
    @Nonnull
    fun <T> toDataArray(): Collector<T, *, DataArray> =
        Collector.of({ DataArray.empty() }, { arr, t -> arr.add(t) }, { a, b -> a.addAll(b) })

    @JvmStatic
    @Nonnull
    fun durationToString(
        duration: Duration,
        resolutionUnit: TimeUnit,
    ): String {
        val actual = resolutionUnit.convert(duration.seconds, TimeUnit.SECONDS)
        val raw = "$actual ${resolutionUnit.toString().lowercase(Locale.ROOT)}"

        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds =
            duration.seconds -
                TimeUnit.DAYS.toSeconds(days) -
                TimeUnit.HOURS.toSeconds(hours) -
                TimeUnit.MINUTES.toSeconds(minutes)

        val joiner = StringJoiner(" ")
        if (days > 0) {
            joiner.add("$days days")
        }
        if (hours > 0) {
            joiner.add("$hours hours")
        }
        if (minutes > 0) {
            joiner.add("$minutes minutes")
        }
        if (seconds > 0) {
            joiner.add("$seconds seconds")
        }

        return "$raw ($joiner)"
    }

    @JvmStatic
    @Nonnull
    fun getLastPathSegment(
        @Nonnull url: String,
    ): String {
        val parsedUrl = url.toHttpUrlOrNull()
        Checks.check(parsedUrl != null, "URL '%s' is invalid", url)

        val segments = parsedUrl!!.pathSegments
        return segments[segments.size - 1]
    }
}
