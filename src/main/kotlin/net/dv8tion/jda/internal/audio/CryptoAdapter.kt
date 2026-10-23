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

import com.google.crypto.tink.aead.internal.InsecureNonceAesGcmJce
import com.google.crypto.tink.aead.internal.InsecureNonceXChaCha20Poly1305
import net.dv8tion.jda.internal.utils.IOUtil
import net.dv8tion.jda.internal.utils.ResizingByteBuffer
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.Security
import java.util.EnumSet
import kotlin.math.abs

interface CryptoAdapter {
    fun getMode(): AudioEncryption

    fun encrypt(
        output: ResizingByteBuffer,
        audio: ByteBuffer,
    )

    fun decrypt(
        extensionLength: Short,
        userId: Long,
        packet: ByteBuffer,
        decrypted: ResizingByteBuffer,
    ): Boolean

    abstract class AbstractAaedAdapter
        protected constructor(
            secretKey: ByteArray,
            tagBytes: Int,
            paddedNonceBytes: Int,
        ) : CryptoAdapter {
            @JvmField
            protected val secretKey: ByteArray = secretKey

            @JvmField
            protected val tagBytes: Int = tagBytes

            @JvmField
            protected val paddedNonceBytes: Int = paddedNonceBytes

            @JvmField
            protected val nonceBuffer: ByteArray = ByteArray(paddedNonceBytes)

            @JvmField
            protected var encryptCounter: Int = abs(RANDOM.nextInt()) % 513 + 1

            @Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
            override fun encrypt(
                output: ResizingByteBuffer,
                audio: ByteBuffer,
            ) {
                val minimumOutputSize = audio.remaining() + tagBytes + NONCE_BYTES

                output.ensureRemaining(minimumOutputSize)
                IOUtil.setIntBigEndian(nonceBuffer, 0, encryptCounter)

                try {
                    encryptInternally(output.buffer(), audio, nonceBuffer)
                    output.buffer().putInt(encryptCounter++)
                    output.buffer().flip()
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }

            @Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
            override fun decrypt(
                extensionLength: Short,
                userId: Long,
                packet: ByteBuffer,
                decrypted: ResizingByteBuffer,
            ): Boolean {
                try {
                    val headerLength = packet.position()
                    packet.position(0)
                    val associatedData = ByteArray(headerLength)
                    packet.get(associatedData)
                    val cipherText = ByteArray(packet.remaining() - NONCE_BYTES)
                    packet.get(cipherText)
                    val nonce = ByteArray(paddedNonceBytes)
                    packet.get(nonce, 0, NONCE_BYTES)
                    val output = decryptInternally(cipherText, associatedData, nonce)

                    decrypted.replace(ByteBuffer.wrap(output))

                    return true
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }

            @Throws(Exception::class)
            protected abstract fun encryptInternally(
                output: ByteBuffer,
                audio: ByteBuffer,
                nonce: ByteArray,
            )

            @Throws(Exception::class)
            protected abstract fun decryptInternally(
                cipherText: ByteArray,
                associatedData: ByteArray,
                nonce: ByteArray,
            ): ByteArray

            protected fun getAssociatedData(output: ByteBuffer): ByteArray {
                val ad = ByteArray(output.position())
                output.position(0)
                output.get(ad)
                return ad
            }

            protected fun getPlaintextCopy(audio: ByteBuffer): ByteArray {
                val plaintext = ByteArray(audio.remaining())
                audio.get(plaintext)
                return plaintext
            }

            private companion object {
                private const val NONCE_BYTES: Int = 4
                private val RANDOM: SecureRandom = SecureRandom()
            }
        }

    // Name preserved for bytecode compatibility with the Java class; ktlint would prefer camel case.
    @Suppress("ktlint:standard:class-naming")
    open class AES_GCM_Adapter(
        secretKey: ByteArray,
    ) : AbstractAaedAdapter(secretKey, TAG_BYTES, AES_GCM_NONCE_BYTES) {
        override fun getMode(): AudioEncryption = AudioEncryption.AEAD_AES256_GCM_RTPSIZE

        @Throws(Exception::class)
        override fun encryptInternally(
            output: ByteBuffer,
            audio: ByteBuffer,
            nonce: ByteArray,
        ) {
            val cipher = getCipher()
            val input = getPlaintextCopy(audio)
            val associatedData = getAssociatedData(output)
            output.put(cipher.encrypt(nonce, input, associatedData))
        }

        @Throws(Exception::class)
        public override fun decryptInternally(
            cipherText: ByteArray,
            associatedData: ByteArray,
            nonce: ByteArray,
        ): ByteArray {
            val cipher = getCipher()
            return cipher.decrypt(nonce, cipherText, associatedData)
        }

        @Throws(GeneralSecurityException::class)
        private fun getCipher(): InsecureNonceAesGcmJce = InsecureNonceAesGcmJce(secretKey)
    }

    open class XChaCha20Poly1305Adapter(
        secretKey: ByteArray,
    ) : AbstractAaedAdapter(secretKey, TAG_BYTES, XCHACHA_NONCE_BYTES) {
        override fun getMode(): AudioEncryption = AudioEncryption.AEAD_XCHACHA20_POLY1305_RTPSIZE

        @Throws(Exception::class)
        public override fun encryptInternally(
            output: ByteBuffer,
            audio: ByteBuffer,
            nonce: ByteArray,
        ) {
            val cipher = getCipher()
            val input = getPlaintextCopy(audio)
            val associatedData = getAssociatedData(output)
            output.put(cipher.encrypt(nonce, input, associatedData))
        }

        @Throws(Exception::class)
        public override fun decryptInternally(
            cipherText: ByteArray,
            associatedData: ByteArray,
            nonce: ByteArray,
        ): ByteArray {
            val cipher = getCipher()
            return cipher.decrypt(nonce, cipherText, associatedData)
        }

        @Throws(GeneralSecurityException::class)
        private fun getCipher(): InsecureNonceXChaCha20Poly1305 = InsecureNonceXChaCha20Poly1305(secretKey)
    }

    companion object {
        const val AES_GCM_NO_PADDING: String = "AES_256/GCM/NOPADDING"

        private const val TAG_BYTES: Int = 16
        private const val AES_GCM_NONCE_BYTES: Int = 12
        private const val XCHACHA_NONCE_BYTES: Int = 24

        @JvmStatic
        fun negotiate(supportedModes: EnumSet<AudioEncryption>): AudioEncryption? {
            for (mode in AudioEncryption.values()) {
                if (supportedModes.contains(mode) && isModeSupported(mode)) {
                    return mode
                }
            }

            return null
        }

        @JvmStatic
        fun isModeSupported(mode: AudioEncryption): Boolean =
            when (mode) {
                AudioEncryption.AEAD_AES256_GCM_RTPSIZE ->
                    Security.getAlgorithms("Cipher").contains(AES_GCM_NO_PADDING)
                AudioEncryption.AEAD_XCHACHA20_POLY1305_RTPSIZE -> true
            }

        @JvmStatic
        fun getAdapter(
            mode: AudioEncryption,
            secretKey: ByteArray,
        ): CryptoAdapter =
            when (mode) {
                AudioEncryption.AEAD_AES256_GCM_RTPSIZE -> AES_GCM_Adapter(secretKey)
                AudioEncryption.AEAD_XCHACHA20_POLY1305_RTPSIZE -> XChaCha20Poly1305Adapter(secretKey)
            }
    }
}
