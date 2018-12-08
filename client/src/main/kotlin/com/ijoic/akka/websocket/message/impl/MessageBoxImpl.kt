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
import java.io.Serializable

/**
 * Message box impl
 *
 * @author verstsiu created at 2018-11-24 21:09
 */
internal data class MessageBoxImpl(
  override val appendMessages: Map<String, Set<Serializable>> = emptyMap(),
  override val uniqueMessages: Map<String, Serializable> = emptyMap(),
  override val queueMessages: List<Serializable> = emptyList()
): MessageBox {

  override val isEmpty: Boolean by lazy {
    appendMessages.isEmpty() && uniqueMessages.isEmpty() && queueMessages.isEmpty()
  }

  override val subscribeMessageSize: Int by lazy { measureSubscribeMessageSize() }

  companion object {
    /**
     * Blank message box instance
     */
    internal val blank: MessageBox = MessageBoxImpl()
  }
}