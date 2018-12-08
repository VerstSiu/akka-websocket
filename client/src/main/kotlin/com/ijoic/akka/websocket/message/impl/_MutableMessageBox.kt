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
package com.ijoic.akka.websocket.message.impl

import com.ijoic.akka.websocket.message.*

/**
 * Dispatch send message
 *
 * @author verstsiu created at 2018-11-24 22:18
 */
internal fun MutableMessageBox.dispatchMessage(msg: SendMessage): Boolean {
  return when(msg) {
    is AppendMessage -> append(msg.message, msg.group)
    is ReplaceMessage -> replace(msg.message, msg.group)
    is ClearAppendMessage -> clearAppend(msg.pairMessage, msg.group)
    is ClearReplaceMessage -> clearReplace(msg.group)
    is QueueMessage -> queue(msg.message)
  }
}

/**
 * Dispatch send message all
 *
 * @author verstsiu created at 2018-12-08 16:40
 */
internal fun MutableMessageBox.dispatchMessageAll(msgItems: Collection<SendMessage>): List<SendMessage> {
  val dispatchedItems = mutableListOf<SendMessage>()

  msgItems.forEach {
    if (dispatchMessage(it)) {
      dispatchedItems.add(it)
    }
  }
  return dispatchedItems
}