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

package net.dv8tion.jda.internal.audio

import net.dv8tion.jda.internal.utils.JDALogger
import net.dv8tion.jda.internal.utils.ResizingByteBuffer
import org.slf4j.Logger
import java.net.DatagramPacket
import java.nio.ByteBuffer
import javax.annotation.Nullable

/**
 * Represents the contents of a audio packet that was either received from Discord or
 * will be sent to discord.
 *
 * @see [RFC 3350 - RTP: A Transport Protocol for Real-Time Applications](https://tools.ietf.org/html/rfc3550)
 */
open class AudioPacket {
    private val type: Byte
    private val seq: Char
    private val timestamp: Int
    private val ssrc: Int
    private val extensionLength: Short
    private val hasExtension: Boolean
    private val csrc: IntArray
    private val encodedAudio: ByteBuffer

    constructor(packet: DatagramPacket) : this(
        ByteBuffer.wrap(packet.data, packet.offset, packet.length),
    )

    constructor(rawPacket: ByteArray) : this(ByteBuffer.wrap(rawPacket))

    constructor(buffer: ByteBuffer) {
        // Parsing header as described by https://datatracker.ietf.org/doc/html/rfc3550#section-5.1

        val first = buffer.get()
        // extension, 1 if extension is present
        hasExtension = (first.toInt() and EXTENSION_FLAG_MASK) != 0
        // CSRC count, 0 to 15
        val cc = first.toInt() and CSRC_COUNT_MASK

        type = buffer.get()
        seq = buffer.getChar()
        timestamp = buffer.getInt()
        ssrc = buffer.getInt()

        csrc = IntArray(cc)
        for (i in 0 until cc) {
            csrc[i] = buffer.getInt()
        }

        // Extract extension length as described by
        // https://datatracker.ietf.org/doc/html/rfc3550#section-5.3.1
        extensionLength = if (hasExtension) buffer.getInt().toShort() else 0

        encodedAudio = buffer
    }

    constructor(seq: Char, timestamp: Int, ssrc: Int, encodedAudio: ByteBuffer) {
        this.seq = seq
        this.ssrc = ssrc
        this.timestamp = timestamp
        csrc = IntArray(0)
        extensionLength = 0
        hasExtension = false
        type = RTP_PAYLOAD_TYPE
        this.encodedAudio = encodedAudio
    }

    fun getEncodedAudio(): ByteBuffer = encodedAudio

    fun getSequence(): Char = seq

    fun getSSRC(): Int = ssrc

    fun getTimestamp(): Int = timestamp

    fun asEncryptedPacket(
        crypto: CryptoAdapter,
        buffer: ResizingByteBuffer,
    ) {
        buffer.prepareWrite(RTP_HEADER_SIZE)
        writeHeader(seq, timestamp, ssrc, buffer.buffer())
        crypto.encrypt(buffer, encodedAudio)
    }

    @Suppress("ReturnCount")
    @Nullable
    fun asDecryptAudioPacket(
        crypto: CryptoAdapter,
        userId: Long,
        decryptBuffer: ResizingByteBuffer,
    ): AudioPacket? {
        if (type != RTP_PAYLOAD_TYPE) {
            return null
        }

        val success = crypto.decrypt(extensionLength, userId, encodedAudio, decryptBuffer)
        if (!success) {
            log.warn("Failed to decrypt audio packet for user {}", userId)
            return null
        }

        return AudioPacket(seq, timestamp, ssrc, decryptBuffer.buffer())
    }

    companion object {
        private const val CSRC_COUNT_MASK: Int = 0x0F
        private const val EXTENSION_FLAG_MASK: Int = 0b0001_0000

        /**
         * Bit index 0 and 1 represent the RTP Protocol version used. Discord uses the latest RTP protocol version, 2.
         * Bit index 2 represents whether or not we pad. Opus uses an internal padding system, so RTP padding is not used.
         * Bit index 3 represents if we use extensions.
         * Bit index 4 to 7 represent the CC or CSRC count. CSRC is Combined SSRC.
         */
        const val RTP_VERSION_PAD_EXTEND: Byte = 0x80.toByte() // Binary: 1000 0000

        /**
         * This is Discord's RTP Profile Payload type.
         * I've yet to find actual documentation on what the bits inside this value represent.
         */
        const val RTP_PAYLOAD_TYPE: Byte = 0x78.toByte() // Binary: 0100 1000

        private const val RTP_HEADER_SIZE = 12

        private val log: Logger = JDALogger.getLog(AudioPacket::class.java)

        private fun writeHeader(
            seq: Char,
            timestamp: Int,
            ssrc: Int,
            buffer: ByteBuffer,
        ) {
            buffer.put(RTP_VERSION_PAD_EXTEND)
            buffer.put(RTP_PAYLOAD_TYPE)
            buffer.putChar(seq)
            buffer.putInt(timestamp)
            buffer.putInt(ssrc)
        }
    }
}
