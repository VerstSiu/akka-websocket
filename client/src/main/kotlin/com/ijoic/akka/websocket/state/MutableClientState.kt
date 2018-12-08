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
 * Mutable client state
 *
 * @author verstsiu created at 2018-11-26 17:39
 */
internal interface MutableClientState: ClientState {
  /**
   * Connection active status
   */
  override var isConnectionActive: Boolean

  /**
   * Socket state (read & write access)
   */
  override var state: SocketState

  /**
   * Messages (read & write access)
   */
  override var messages: MessageBox

  /**
   * Wait for connect status (read & write access)
   */
  override var waitForConnect: Boolean

  /**
   * Retry count
   */
  override var retryCount: Int

  /**
   * Retry period
   */
  override var retryPeriod: Int
}