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

package net.dv8tion.jda.internal.utils.compress

import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.internal.utils.IOUtil
import net.dv8tion.jda.internal.utils.JDALogger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.ref.SoftReference
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import java.util.zip.InflaterOutputStream
import javax.annotation.Nonnull
import javax.annotation.Nullable

class ZlibDecompressor(
    private val maxBufferSize: Int,
) : Decompressor {
    companion object {
        private const val Z_SYNC_FLUSH = 0x0000FFFF

        private const val INITIAL_BUFFER_SIZE = 1024
        private const val FLUSH_SUFFIX_LENGTH = 4
        private const val BUFFER_CAPACITY_FACTOR = 2
    }

    private val inflater = Inflater()
    private var flushBuffer: ByteBuffer? = null
    private var decompressBuffer: SoftReference<ByteArrayOutputStream>? = null

    private fun newDecompressBuffer(): SoftReference<ByteArrayOutputStream> =
        SoftReference(ByteArrayOutputStream(minOf(INITIAL_BUFFER_SIZE, maxBufferSize)))

    private fun getDecompressBuffer(): ByteArrayOutputStream {
        // If no buffer has been allocated yet we do that here (lazy init)
        if (decompressBuffer == null) {
            decompressBuffer = newDecompressBuffer()
        }
        // Check if the buffer has been collected by the GC or not
        val buffer = decompressBuffer!!.get()
        return buffer
            // create a new buffer because the GC got it
            ?: ByteArrayOutputStream(minOf(INITIAL_BUFFER_SIZE, maxBufferSize)).also {
                decompressBuffer = SoftReference(it)
            }
    }

    private fun isFlush(data: ByteArray): Boolean {
        if (data.size < FLUSH_SUFFIX_LENGTH) {
            return false
        }
        val suffix = IOUtil.getIntBigEndian(data, data.size - FLUSH_SUFFIX_LENGTH)
        return suffix == Z_SYNC_FLUSH
    }

    private fun buffer(data: ByteArray) {
        var current = flushBuffer
        if (current == null) {
            current = ByteBuffer.allocate(data.size * BUFFER_CAPACITY_FACTOR)
        }

        // Ensure the capacity can hold the new data, ByteBuffer doesn't grow automatically
        if (current.capacity() < data.size + current.position()) {
            // Flip to make it a read buffer
            current.flip()
            // Reallocate for the new capacity
            current = IOUtil.reallocate(current, (current.capacity() + data.size) * BUFFER_CAPACITY_FACTOR)
        }

        current.put(data)
        flushBuffer = current
    }

    private fun lazy(data: ByteArray): Any = JDALogger.getLazyString { Arrays.toString(data) }

    @Nonnull
    override fun getType(): Compression = Compression.ZLIB

    override fun reset() {
        inflater.reset()
    }

    override fun shutdown() {
        reset()
    }

    @Nullable
    @Throws(DataFormatException::class)
    override fun decompress(
        @Nonnull data: ByteArray,
    ): ByteArray? {
        var message = data
        // Handle split messages
        if (!isFlush(message)) {
            // There is no flush suffix so this is not the end of the message
            Decompressor.LOG.debug("Received incomplete data, writing to buffer. Length: {}", message.size)
            buffer(message)
            return null // signal failure to decompress
        } else if (flushBuffer != null) {
            // This has a flush suffix and we have an incomplete package buffered
            // concatenate the package with the new data and decompress it below
            Decompressor.LOG.debug("Received final part of incomplete data")
            buffer(message)
            val pending = flushBuffer!!
            val arr = pending.array()
            message = ByteArray(pending.position())
            System.arraycopy(arr, 0, message, 0, message.size)
            flushBuffer = null
        }
        Decompressor.LOG.trace("Decompressing data {}", lazy(message))
        // Get the compressed message and inflate it
        // We use the same buffer here to optimize gc use
        val output = getDecompressBuffer()
        try {
            InflaterOutputStream(output, inflater).use { decompressor ->
                // This decompressor writes the received data and inflates it
                decompressor.write(message)
                // Once decompressed we re-interpret the data as a String which can be used for JSON
                // parsing
                return output.toByteArray()
            }
        } catch (e: IOException) {
            // Some issue appeared during decompression that caused a failure
            throw DataFormatException("Malformed").initCause(e) as DataFormatException
        } finally {
            // When done with decompression we want to reset the buffer so it can be used again later
            if (output.size() > maxBufferSize) {
                decompressBuffer = newDecompressBuffer()
            } else {
                output.reset()
            }
        }
    }
}
