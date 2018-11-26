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

import com.ijoic.akka.websocket.message.*
import com.ijoic.akka.websocket.state.ClientState
import com.ijoic.akka.websocket.state.MutableClientState

/**
 * Mutable client state impl
 *
 * @author verstsiu created at 2018-11-26 17:44
 */
internal class MutableClientStateImpl(
  src: ClientState = ClientStateImpl.blank): MutableClientState {

  private val oldMessageList = src.messageList
  private var _messageList: MutableList<SendMessage>? = null

  private val editMessageList: MutableList<SendMessage>
    get() {
      return _messageList ?: oldMessageList
        .toMutableList()
        .also { _messageList = it }
    }

  override var state = src.state

  override val messageList: List<SendMessage>
    get() = _messageList ?: oldMessageList

  override var messages = src.messages
  override var waitForConnect = src.waitForConnect

  override fun upgradeMessageList(message: SendMessage): Boolean {
    val messageList = this.messageList

    when(message) {
      is AppendMessage,
      is ReplaceMessage -> if (!messageList.contains(message)) {
        editMessageList.add(message)
        return true
      }
      is ClearAppendMessage -> {
        val reverseMessage = AppendMessage(message.pairMessage, message.group)

        if (messageList.contains(reverseMessage)) {
          editMessageList.remove(reverseMessage)
          return true
        }
      }
      is ClearReplaceMessage -> {
        val reverseMessage = messageList.firstOrNull { it is ReplaceMessage && it.group == message.group }

        if (reverseMessage != null) {
          editMessageList.remove(reverseMessage)
          return true
        }
      }
    }
    return false
  }

}