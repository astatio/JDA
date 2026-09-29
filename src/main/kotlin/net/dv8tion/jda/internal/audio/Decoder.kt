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

import com.sun.jna.ptr.PointerByReference
import net.dv8tion.jda.api.audio.OpusPacket
import tomp2p.opuswrapper.Opus
import java.nio.IntBuffer
import java.nio.ShortBuffer
import javax.annotation.Nullable

/**
 * Class that wraps functionality around the Opus decoder.
 */
open class Decoder
    internal constructor(
        ssrc: Int,
    ) {
        @JvmField
        protected var ssrc: Int = ssrc

        @JvmField
        protected var lastSeq: Char = Char.MAX_VALUE

        @JvmField
        protected var lastTimestamp: Int = -1

        @JvmField
        protected var opusDecoder: PointerByReference?

        init {
            val error = IntBuffer.allocate(1)
            opusDecoder =
                Opus.INSTANCE.opus_decoder_create(OpusPacket.OPUS_SAMPLE_RATE, OpusPacket.OPUS_CHANNEL_COUNT, error)
            if (error.get() != Opus.OPUS_OK && opusDecoder == null) {
                throw IllegalStateException("Received error code from opus_decoder_create(...): " + error.get())
            }
        }

        fun isInOrder(newSeq: Char): Boolean = lastSeq == Char.MAX_VALUE || newSeq > lastSeq || lastSeq - newSeq > OUT_OF_ORDER_TOLERANCE

        fun wasPacketLost(newSeq: Char): Boolean = newSeq > lastSeq + 1

        @Nullable
        fun decodeFromOpus(decryptedPacket: AudioPacket?): ShortArray? {
            var result: Int
            val decoded = ShortBuffer.allocate(4096)
            if (decryptedPacket == null) { // Flag for packet-loss
                result = Opus.INSTANCE.opus_decode(opusDecoder, null, 0, decoded, OpusPacket.OPUS_FRAME_SIZE, 0)
                lastSeq = Char.MAX_VALUE
                lastTimestamp = -1
            } else {
                lastSeq = decryptedPacket.getSequence()
                lastTimestamp = decryptedPacket.getTimestamp()

                val encodedAudio = decryptedPacket.getEncodedAudio()
                val length = encodedAudio.remaining()
                val buf = ByteArray(length)
                encodedAudio.slice().get(buf)
                result = Opus.INSTANCE.opus_decode(opusDecoder, buf, buf.size, decoded, OpusPacket.OPUS_FRAME_SIZE, 0)
            }

            // If we get a result that is less than 0, then there was an error. Return null as a
            // signifier.
            if (result < 0) {
                handleDecodeError(result)
                return null
            }

            val audio = ShortArray(result * 2)
            decoded.get(audio)
            return audio
        }

        private fun handleDecodeError(result: Int) {
            val b = StringBuilder("Decoder failed to decode audio from user with code ")
            when (result) {
                Opus.OPUS_BAD_ARG -> b.append("OPUS_BAD_ARG") // -1
                Opus.OPUS_BUFFER_TOO_SMALL -> b.append("OPUS_BUFFER_TOO_SMALL") // -2
                Opus.OPUS_INTERNAL_ERROR -> b.append("OPUS_INTERNAL_ERROR") // -3
                Opus.OPUS_INVALID_PACKET -> b.append("OPUS_INVALID_PACKET") // -4
                Opus.OPUS_UNIMPLEMENTED -> b.append("OPUS_UNIMPLEMENTED") // -5
                Opus.OPUS_INVALID_STATE -> b.append("OPUS_INVALID_STATE") // -6
                Opus.OPUS_ALLOC_FAIL -> b.append("OPUS_ALLOC_FAIL") // -7
                else -> b.append(result)
            }
            AudioConnection.LOG.debug("{}", b)
        }

        @JvmName("close")
        @Synchronized
        internal fun close() {
            if (opusDecoder != null) {
                Opus.INSTANCE.opus_decoder_destroy(opusDecoder)
                opusDecoder = null
            }
        }

        @Deprecated("Deprecated in Java 9 because the finalization system is being changed/removed")
        @Throws(Throwable::class)
        @Suppress("deprecation")
        protected fun finalize() {
            close()
        }

        private companion object {
            private const val OUT_OF_ORDER_TOLERANCE: Int = 10
        }
    }
