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

package net.dv8tion.jda.internal.requests

/**
 * WebSocket OP Codes for discord
 * <br>Used in [WebSocketClient] to handle discord payloads
 * and send payloads with central readable OP Codes
 */
object WebSocketCode {
    const val DISPATCH: Int = 0
    const val HEARTBEAT: Int = 1
    const val IDENTIFY: Int = 2
    const val PRESENCE: Int = 3
    const val VOICE_STATE: Int = 4
    const val RESUME: Int = 6
    const val RECONNECT: Int = 7
    const val MEMBER_CHUNK_REQUEST: Int = 8
    const val INVALIDATE_SESSION: Int = 9
    const val HELLO: Int = 10
    const val HEARTBEAT_ACK: Int = 11
    const val GUILD_SYNC: Int = 12
}
