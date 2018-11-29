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

import com.ijoic.akka.websocket.message.MessageBox
import com.ijoic.akka.websocket.message.MutableMessageBox
import java.io.Serializable

/**
 * Returns mutable instance of current message box
 *
 * @author verstsiu created at 2018-11-24 22:17
 */
internal fun MessageBox.edit(): MutableMessageBox {
  return MutableMessageBoxImpl(this)
}

/**
 * Returns all messages of message box
 *
 * @author verstsiu created at 2018-11-26 12:37
 */
internal fun MessageBox.allMessages(): List<Serializable> {
  val messages = mutableListOf<Serializable>()

  for ((_, messageSet) in appendMessages) {
    messages.addAll(messageSet)
  }

  for ((_, message) in uniqueMessages) {
    messages.add(message)
  }

  messages.addAll(queueMessages)
  return messages
}