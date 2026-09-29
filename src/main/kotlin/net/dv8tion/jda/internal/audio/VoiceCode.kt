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

object VoiceCode {
    // PROTOCOL
    const val IDENTIFY = 0
    const val SELECT_PROTOCOL = 1
    const val READY = 2
    const val HEARTBEAT = 3
    const val SESSION_DESCRIPTION = 4
    const val USER_SPEAKING_UPDATE = 5
    const val HEARTBEAT_ACK = 6
    const val RESUME = 7
    const val HELLO = 8
    const val RESUMED = 9
    const val USER_BULK_CONNECT = 11
    const val USER_CONNECT = 12
    const val USER_DISCONNECT = 13
    const val DAVE_PREPARE_TRANSITION = 21
    const val DAVE_EXECUTE_TRANSITION = 22
    const val DAVE_TRANSITION_READY = 23
    const val DAVE_PREPARE_EPOCH = 24
    const val MLS_EXTERNAL_SENDER = 25
    const val MLS_KEY_PACKAGE = 26
    const val MLS_PROPOSALS = 27
    const val MLS_COMMIT_WELCOME = 28
    const val MLS_ANNOUNCE_COMMIT_TRANSITION = 29
    const val MLS_WELCOME = 30
    const val MLS_INVALID_COMMIT_WELCOME = 31

    // CLOSE
    enum class Close(
        val code: Int,
        val meaning: String,
    ) {
        HEARTBEAT_TIMEOUT(1000, "We did not heartbeat in time"),
        UNKNOWN_OP_CODE(4001, "Sent an invalid op code"),
        NOT_AUTHENTICATED(4003, "Tried to send payload before authenticating session"),
        AUTHENTICATION_FAILED(4004, "The token sent in the identify payload is incorrect"),
        ALREADY_AUTHENTICATED(4005, "Tried to authenticate when already authenticated"),
        INVALID_SESSION(4006, "The session with which we attempted to resume is invalid"),
        SESSION_TIMEOUT(4009, "Heartbeat timed out"),
        SERVER_NOT_FOUND(4011, "The server we attempted to connect to was not found"),
        UNKNOWN_PROTOCOL(4012, "The selected protocol is not supported"),
        DISCONNECTED(4014, "The connection has been dropped normally"),
        SERVER_CRASH(4015, "The server we were connected to has crashed"),
        UNKNOWN_ENCRYPTION_MODE(4016, "The specified encryption method is not supported"),
        BAD_REQUEST(4020, "We sent a malformed request"),
        RATE_LIMIT_EXCEEDED(4021, "We exceeded the rate limit"),
        DISCONNECTED_ALL_CLIENTS(4022, "All clients were disconnected, likely the channel was deleted"),

        UNKNOWN(0, "Unknown code"),
        ;

        companion object {
            @JvmStatic
            fun from(code: Int): Close {
                for (c in values()) {
                    if (c.code == code) {
                        return c
                    }
                }
                return UNKNOWN
            }
        }
    }
}
