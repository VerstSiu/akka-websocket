/*
 *
 *  Copyright(c) 2018 VerstSiu
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.ijoic.akka.websocket.client

import com.ijoic.metrics.MetricsMessage

/**
 * Socket message
 *
 * @author verstsiu created at 2018-11-26 11:17
 */
abstract class SocketMessage: MetricsMessage()

/**
 * Socket error
 */
abstract class SocketError: SocketMessage() {
  /**
   * Error code
   */
  abstract val code: String?

  /**
   * Error message
   */
  abstract val message: String?

  /**
   * Error cause
   */
  abstract val cause: Throwable?
}

/**
 * Connection completed
 */
class ConnectionCompleted: SocketMessage()

/**
 * Connection error
 */
data class ConnectionError(
  override val code: String? = null,
  override val message: String? = null,
  override val cause: Throwable? = null
): SocketError()

/**
 * Connection closed
 */
data class ConnectionClosed(
  override val code: String? = null,
  override val message: String? = null,
  override val cause: Throwable? = null
): SocketError()

/**
 * Message error
 */
data class MessageError(
  override val code: String? = null,
  override val message: String? = null,
  override val cause: Throwable? = null
): SocketError()

/**
 * Receive text
 */
data class ReceiveText(val message: String): SocketMessage()

/**
 * Receive bytes
 */
data class ReceiveBytes(val bytes: ByteArray): SocketMessage() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ReceiveBytes

    if (!bytes.contentEquals(other.bytes)) return false

    return true
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }
}