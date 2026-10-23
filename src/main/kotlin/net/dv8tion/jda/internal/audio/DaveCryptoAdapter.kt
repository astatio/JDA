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

import net.dv8tion.jda.api.audio.dave.DaveSession
import net.dv8tion.jda.internal.utils.ResizingByteBuffer
import java.nio.ByteBuffer
import javax.annotation.concurrent.NotThreadSafe

@NotThreadSafe
open class DaveCryptoAdapter(
    @JvmField protected val transportCryptoAdapter: CryptoAdapter,
    @JvmField protected val daveSession: DaveSession,
    @JvmField protected val ssrc: Int,
) : CryptoAdapter {
    @JvmField
    protected var encryptBuffer: ResizingByteBuffer = ResizingByteBuffer(ByteBuffer.allocateDirect(512))

    @JvmField
    protected var decryptBuffer: ResizingByteBuffer? = null

    private companion object {
        private const val DECRYPT_BUFFER_SIZE: Int = 1024
    }

    override fun getMode(): AudioEncryption = transportCryptoAdapter.getMode()

    override fun encrypt(
        output: ResizingByteBuffer,
        audio: ByteBuffer,
    ) {
        val maxSize = daveSession.getMaxEncryptedFrameSize(DaveSession.MediaType.AUDIO, audio.remaining())

        output.buffer().mark()
        encryptBuffer.prepareWrite(maxSize)

        if (daveSession.encrypt(DaveSession.MediaType.AUDIO, ssrc, audio, encryptBuffer.buffer())) {
            transportCryptoAdapter.encrypt(output, encryptBuffer.buffer())
        } else {
            throw IllegalStateException("Failed to encrypt audio")
        }
    }

    override fun decrypt(
        extensionLength: Short,
        userId: Long,
        packet: ByteBuffer,
        decrypted: ResizingByteBuffer,
    ): Boolean {
        var decryptBuffer = decryptBuffer
        if (decryptBuffer == null) {
            decryptBuffer = ResizingByteBuffer(ByteBuffer.allocateDirect(DECRYPT_BUFFER_SIZE))
            this.decryptBuffer = decryptBuffer
        }

        val success = transportCryptoAdapter.decrypt(extensionLength, userId, packet, decryptBuffer)
        if (!success) {
            return false
        }

        handleRTPHeaderExtension(decryptBuffer.buffer(), extensionLength)

        val outputSize =
            daveSession.getMaxDecryptedFrameSize(
                DaveSession.MediaType.AUDIO,
                userId,
                decryptBuffer.buffer().remaining(),
            )

        decrypted.prepareWrite(outputSize)
        return daveSession.decrypt(DaveSession.MediaType.AUDIO, userId, decryptBuffer.buffer(), decrypted.buffer())
    }

    private fun handleRTPHeaderExtension(
        decrypted: ByteBuffer,
        extensionLength: Short,
    ) {
        if (extensionLength == 0.toShort()) {
            return
        }

        val length = extensionLength.toInt() and 0xFFFF
        val position = decrypted.position()
        val offset = position + 4 * length
        decrypted.position(minOf(offset, decrypted.limit() - 1))
    }
}
