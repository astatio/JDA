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

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.type.CollectionType
import com.fasterxml.jackson.databind.type.MapType
import net.dv8tion.jda.api.exceptions.ParsingException
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.io.UncheckedIOException
import java.util.ArrayList
import java.util.HashMap
import javax.annotation.Nonnull

object SerializationUtil {
    private const val TRUNCATED_ARRAY = "[…truncated array…]"
    private const val TRUNCATED_OBJECT = "{…truncated object…}"

    private val mapper: ObjectMapper = ObjectMapper()
    private val module: SimpleModule = SimpleModule()
    private val mapType: MapType
    private val listType: CollectionType

    init {
        module.addAbstractTypeMapping(Map::class.java, HashMap::class.java)
        module.addAbstractTypeMapping(List::class.java, ArrayList::class.java)
        mapper.registerModule(module)
        mapType = mapper.typeFactory.constructMapType(HashMap::class.java, String::class.java, Any::class.java)
        listType = mapper.typeFactory.constructRawCollectionType(ArrayList::class.java)
    }

    @Nonnull
    @JvmStatic
    fun getMapType(): MapType = mapType

    @Nonnull
    @JvmStatic
    fun getListType(): CollectionType = listType

    @Nonnull
    @JvmStatic
    fun toJson(
        @Nonnull data: Any,
    ): ByteArray {
        Checks.notNull(data, "Data")
        try {
            return mapper.writeValueAsBytes(data)
        } catch (ex: IOException) {
            throw ParsingException(ex)
        }
    }

    @Nonnull
    @JvmStatic
    fun toJsonString(
        @Nonnull data: Any,
        pretty: Boolean,
    ): String {
        Checks.notNull(data, "Data")

        try {
            val writer = getObjectWriter(pretty)
            return writer.writeValueAsString(data)
        } catch (ex: IOException) {
            throw UncheckedIOException(ex)
        }
    }

    @Nonnull
    @JvmStatic
    fun getObjectWriter(pretty: Boolean): ObjectWriter =
        if (!pretty) {
            mapper.writer()
        } else {
            mapper
                .writerWithDefaultPrettyPrinter()
                .with(SerializationFeature.INDENT_OUTPUT)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        }

    @Nonnull
    @JvmStatic
    fun <T> fromJson(
        @Nonnull clazz: Class<T>,
        @Nonnull data: ByteArray,
    ): T {
        Checks.notNull(clazz, "Class")
        return fromJson(mapper.constructType(clazz), data)
    }

    @Nonnull
    @JvmStatic
    fun <T> fromJson(
        @Nonnull type: JavaType,
        @Nonnull data: ByteArray,
    ): T {
        Checks.notNull(type, "Type")
        Checks.notNull(data, "Data")

        try {
            return mapper.readValue(data, type)
        } catch (ex: IOException) {
            throw ParsingException(ex)
        }
    }

    @Nonnull
    @JvmStatic
    fun <T> fromJson(
        @Nonnull type: JavaType,
        @Nonnull data: InputStream,
    ): T {
        Checks.notNull(type, "Type")
        Checks.notNull(data, "Data")

        try {
            return mapper.readValue(data, type)
        } catch (ex: IOException) {
            throw ParsingException(ex)
        }
    }

    @Nonnull
    @JvmStatic
    fun <T> fromJson(
        @Nonnull type: JavaType,
        @Nonnull data: Reader,
    ): T {
        Checks.notNull(type, "Type")
        Checks.notNull(data, "Data")

        try {
            return mapper.readValue(data, type)
        } catch (ex: IOException) {
            throw ParsingException(ex)
        }
    }

    @Nonnull
    @JvmStatic
    fun <T> fromJson(
        @Nonnull type: JavaType,
        @Nonnull data: String,
    ): T {
        Checks.notNull(type, "Type")
        Checks.notNull(data, "Data")

        try {
            return mapper.readValue(data, type)
        } catch (ex: IOException) {
            throw ParsingException(ex)
        }
    }

    @Nonnull
    @JvmStatic
    @Throws(JsonProcessingException::class)
    fun toShallowJsonString(
        @Nonnull `object`: Any,
    ): String {
        val root = mapper.valueToTree<JsonNode>(`object`)
        val shallowRoot = pruneOneLevel(root)
        return mapper
            .writer()
            .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writeValueAsString(shallowRoot)
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    private fun pruneOneLevel(n: JsonNode): JsonNode {
        if (n.isObject) {
            val out = mapper.createObjectNode()
            for (e in n.properties()) {
                val v = e.value
                if (v.isValueNode) {
                    out.set<JsonNode>(e.key, v)
                } else if (v.isArray) {
                    out.put(e.key, TRUNCATED_ARRAY)
                } else {
                    out.put(e.key, TRUNCATED_OBJECT)
                }
            }
            return out
        } else if (n.isArray) {
            val out: ArrayNode = mapper.createArrayNode()
            n.values().forEachRemaining { v: JsonNode ->
                if (v.isValueNode) {
                    out.add(v)
                } else if (v.isArray) {
                    out.add(TRUNCATED_ARRAY)
                } else {
                    out.add(TRUNCATED_OBJECT)
                }
            }
            return out
        }
        return n
    }
}
