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
package com.ijoic.akka.websocket.state

import com.ijoic.akka.websocket.message.MessageBox

/**
 * Client state
 *
 * @author verstsiu created at 2018-11-26 17:37
 */
internal interface ClientState {
  /**
   * Socket state
   */
  val state: SocketState

  /**
   * Messages
   */
  val messages: MessageBox

  /**
   * Wait for connect status
   */
  val waitForConnect: Boolean

  /**
   * Retry count
   */
  val retryCount: Int

  /**
   * Retry period
   */
  val retryPeriod: Int
}