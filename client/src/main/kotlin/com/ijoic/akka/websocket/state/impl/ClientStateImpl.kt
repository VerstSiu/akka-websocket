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
package com.ijoic.akka.websocket.state.impl

import com.ijoic.akka.websocket.message.MessageBox
import com.ijoic.akka.websocket.message.SendMessage
import com.ijoic.akka.websocket.message.impl.MessageBoxImpl
import com.ijoic.akka.websocket.state.ClientState
import com.ijoic.akka.websocket.state.SocketState

/**
 * Client state impl
 *
 * @author verstsiu created at 2018-11-26 17:41
 */
internal class ClientStateImpl(
  override val state: SocketState = SocketState.DISCONNECTED,
  override val messageList: List<SendMessage> = emptyList(),
  override val messages: MessageBox = MessageBoxImpl.blank,
  override val waitForConnect: Boolean = false
): ClientState {

  companion object {
    /**
     * Blank connect status instance
     */
    val blank: ClientState = ClientStateImpl()
  }
}