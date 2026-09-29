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

import com.neovisionaries.ws.client.WebSocketFactory
import net.dv8tion.jda.internal.utils.requestbody.BufferedRequestBody
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okio.source
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException
import javax.annotation.CheckReturnValue
import javax.annotation.Nonnull
import javax.annotation.Nullable

object IOUtil {
    private val log = JDALogger.getLog(IOUtil::class.java)

    private const val PARAMS_PER_QUERY_ENTRY = 2
    private const val BUFFER_GROWTH_FACTOR = 1.25
    private const val MAX_REQUESTS_PER_HOST = 25
    private const val IDLE_CONNECTIONS = 5
    private const val KEEP_ALIVE_SECONDS = 10L
    private const val READ_AHEAD_LIMIT = 256
    private const val BYTE_MASK = 0xFF
    private const val SHIFT_1_BYTE = 8
    private const val SHIFT_2_BYTES = 16
    private const val SHIFT_3_BYTES = 24
    private const val LOWEST_BYTE_INDEX = 3

    @JvmStatic
    fun silentClose(closeable: AutoCloseable?) {
        try {
            closeable?.close()
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun silentClose(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (ignored: IOException) {
        }
    }

    @JvmStatic
    @Suppress("JdkObsolete")
    fun addQuery(
        base: String,
        vararg params: Any,
    ): String =
        try {
            val builder = StringBuilder(base)
            // Start a new query or append to existing one
            if (URI(base).query == null) {
                builder.append('?')
            } else {
                builder.append('&')
            }

            var i = 0
            while (i < params.size) {
                builder
                    .append(params[i])
                    .append('=')
                    .append(URLEncoder.encode(params[i + 1].toString(), "UTF-8"))
                    .append('&')
                i += PARAMS_PER_QUERY_ENTRY
            }

            // Remove trailing &
            builder.setLength(builder.length - 1)

            builder.toString()
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException(e)
        } catch (e: UnsupportedEncodingException) {
            throw IllegalArgumentException(e)
        }

    @JvmStatic
    @Nullable
    fun getHost(uri: String): String? = URI.create(uri).host

    @JvmStatic
    fun setServerName(
        factory: WebSocketFactory,
        url: String,
    ) {
        val host = getHost(url)
        // null if the host is undefined, unlikely but we should handle it
        if (host != null) {
            factory.setServerName(host)
        }
    }

    @JvmStatic
    @Nonnull
    fun newHttpClientBuilder(): OkHttpClient.Builder {
        val dispatcher = Dispatcher()
        // Allow 25 parallel requests to the same host (usually discord.com)
        dispatcher.maxRequestsPerHost = MAX_REQUESTS_PER_HOST
        // Allow 5 idle threads with 10 seconds timeout for each
        val connectionPool = ConnectionPool(IDLE_CONNECTIONS, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS)
        return OkHttpClient.Builder().connectionPool(connectionPool).dispatcher(dispatcher)
    }

    /**
     * Used as an alternate to Java's nio Files.readAllBytes.
     *
     * This customized version for File is provide (instead of just using [.readFully] with a FileInputStream)
     * because with a File we can determine the total size of the array and do not need to have a buffer.
     * This results in a memory footprint that is half the size of [.readFully]
     *
     * Code provided from [Stackoverflow](http://stackoverflow.com/a/6276139)
     *
     * @param file
     *         The file from which we should retrieve the bytes from
     *
     * @throws java.io.IOException
     *         Thrown if there is a problem while reading the file.
     *
     * @return A byte[] containing all of the file's data
     */
    @JvmStatic
    @Nonnull
    @Throws(IOException::class)
    fun readFully(file: File): ByteArray {
        Checks.notNull(file, "File")
        Checks.check(file.exists(), "Provided file does not exist!")

        FileInputStream(file).use { input ->
            // Get the size of the file
            val length = file.length()

            // You cannot create an array using a long type.
            // It needs to be an int type.
            // Before converting to an int type, check
            // to ensure that file is not larger than Integer.MAX_VALUE.
            if (length > Int.MAX_VALUE) {
                throw IOException("Cannot read the file into memory completely due to it being too large!")
                // File is too large
            }

            // Create the byte array to hold the data
            val bytes = ByteArray(length.toInt())

            // Read in the bytes
            var offset = 0
            while (offset < bytes.size) {
                val numRead = input.read(bytes, offset, bytes.size - offset)
                if (numRead < 0) {
                    break
                }
                offset += numRead
            }

            // Ensure all the bytes have been read in
            if (offset < bytes.size) {
                throw IOException("Could not completely read file " + file.name)
            }

            // Close the input stream and return bytes
            input.close()
            return bytes
        }
    }

    /**
     * Provided as a simple way to fully read an InputStream into a byte[].
     *
     * This method will block until the InputStream has been fully read, so if you provide an InputStream that is
     * non-finite, you're gonna have a bad time.
     *
     * @param stream
     *         The Stream to be read.
     *
     * @throws IOException
     *         If the first byte cannot be read for any reason other than the end of the file,
     *         if the input stream has been closed, or if some other I/O error occurs.
     *
     * @return A byte[] containing all of the data provided by the InputStream
     */
    @JvmStatic
    @Nonnull
    @Throws(IOException::class)
    fun readFully(stream: InputStream): ByteArray {
        Checks.notNull(stream, "InputStream")

        val buffer = ByteArray(1024)
        ByteArrayOutputStream().use { bos ->
            var readAmount: Int
            while ((stream.read(buffer).also { readAmount = it }) != -1) {
                bos.write(buffer, 0, readAmount)
            }
            return bos.toByteArray()
        }
    }

    /**
     * Creates a new request body that transmits the provided [java.io.InputStream].
     *
     * @param contentType
     *         The [okhttp3.MediaType] of the data
     * @param stream
     *         The [java.io.InputStream] to be transmitted
     *
     * @return RequestBody capable of transmitting the provided InputStream of data
     */
    @JvmStatic
    @Nonnull
    fun createRequestBody(
        contentType: MediaType,
        stream: InputStream,
    ): BufferedRequestBody = BufferedRequestBody(stream.source(), contentType)

    @JvmStatic
    fun getShortBigEndian(
        arr: ByteArray,
        offset: Int,
    ): Short = (((arr[offset].toInt() and BYTE_MASK) shl SHIFT_1_BYTE) or (arr[offset + 1].toInt() and BYTE_MASK)).toShort()

    @JvmStatic
    fun getShortLittleEndian(
        arr: ByteArray,
        offset: Int,
    ): Short =
        // Same as big endian but reversed order of bytes (java uses big endian)
        (((arr[offset].toInt() and BYTE_MASK) or ((arr[offset + 1].toInt() and BYTE_MASK) shl SHIFT_1_BYTE))).toShort()

    @JvmStatic
    fun getIntBigEndian(
        arr: ByteArray,
        offset: Int,
    ): Int =
        (arr[offset + LOWEST_BYTE_INDEX].toInt() and BYTE_MASK) or
            ((arr[offset + 2].toInt() and BYTE_MASK) shl SHIFT_1_BYTE) or
            ((arr[offset + 1].toInt() and BYTE_MASK) shl SHIFT_2_BYTES) or
            ((arr[offset].toInt() and BYTE_MASK) shl SHIFT_3_BYTES)

    @JvmStatic
    fun setIntBigEndian(
        arr: ByteArray,
        offset: Int,
        it: Int,
    ) {
        arr[offset] = ((it ushr SHIFT_3_BYTES) and BYTE_MASK).toByte()
        arr[offset + 1] = ((it ushr SHIFT_2_BYTES) and BYTE_MASK).toByte()
        arr[offset + 2] = ((it ushr SHIFT_1_BYTE) and BYTE_MASK).toByte()
        arr[offset + LOWEST_BYTE_INDEX] = (it and BYTE_MASK).toByte()
    }

    @JvmStatic
    @Nonnull
    @CheckReturnValue
    fun allocateLike(
        @Nonnull original: ByteBuffer,
        length: Int,
    ): ByteBuffer = if (original.isDirect) ByteBuffer.allocateDirect(length) else ByteBuffer.allocate(length)

    @JvmStatic
    @Nonnull
    @CheckReturnValue
    fun reallocate(
        @Nonnull original: ByteBuffer,
        length: Int,
    ): ByteBuffer {
        val buffer = allocateLike(original, length)
        buffer.put(original)
        return buffer
    }

    @JvmStatic
    @Nonnull
    @CheckReturnValue
    fun replace(
        @Nonnull destination: ByteBuffer,
        @Nonnull source: ByteBuffer,
    ): ByteBuffer {
        var result = destination
        if (result.capacity() < source.remaining()) {
            result = allocateLike(result, (BUFFER_GROWTH_FACTOR * source.remaining()).toInt())
        }

        result.clear()
        result.put(source)
        result.flip()
        return result
    }

    /**
     * Retrieves an [InputStream] for the provided [okhttp3.Response].
     * <br>When the header for `content-encoding` is set with `gzip` this will wrap the body
     * in a [java.util.zip.GZIPInputStream] which decodes the data.
     *
     * This is used to make usage of encoded responses more user-friendly in various parts of JDA.
     *
     * @param response
     *         The not-null Response object
     *
     * @return InputStream representing the body of this response
     */
    @JvmStatic
    @Nullable
    @Throws(IOException::class)
    // Mirrors the original Java control flow, where each branch returns directly.
    @Suppress("ReturnCount")
    fun getBody(response: okhttp3.Response): InputStream? {
        val encoding = response.header("content-encoding", "")
        val data = BufferedInputStream(response.body.byteStream())
        data.mark(READ_AHEAD_LIMIT)
        try {
            if (encoding.equals("gzip", ignoreCase = true)) {
                return GZIPInputStream(data)
            } else if (encoding.equals("deflate", ignoreCase = true)) {
                return InflaterInputStream(data, Inflater(true))
            }
        } catch (ex: ZipException) {
            data.reset() // reset to get full content
            log.error(
                "Failed to read gzip content for response. Headers: {}\nContent: '{}'",
                response.headers,
                JDALogger.getLazyString { String(readFully(data), StandardCharsets.UTF_8) },
                ex,
            )
            return null
        } catch (ex: EOFException) {
            data.reset() // reset to get full content
            log.error(
                "Failed to read gzip content for response. Headers: {}\nContent: '{}'",
                response.headers,
                JDALogger.getLazyString { String(readFully(data), StandardCharsets.UTF_8) },
                ex,
            )
            return null
        }
        return data
    }
}
