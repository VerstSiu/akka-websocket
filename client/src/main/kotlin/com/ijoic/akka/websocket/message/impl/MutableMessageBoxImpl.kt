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

/**
 * Mutable message box impl
 *
 * @author verstsiu created at 2018-11-24 21:15
 */
internal class MutableMessageBoxImpl(
  private val src: MessageBox = MessageBoxImpl.blank): MutableMessageBox {

  private var _appendMessages: MutableMap<String, Set<String>>? = null
  private var _uniqueMessages: MutableMap<String, String>? = null
  private var _queueMessages: MutableList<String>? = null

  private var editCount = 0

  override val appendMessages: Map<String, Set<String>>
    get() = _appendMessages ?: src.appendMessages
  override val uniqueMessages: Map<String, String>
    get() = _uniqueMessages ?: src.uniqueMessages
  override val queueMessages: List<String>
    get() = _queueMessages ?: src.queueMessages

  override val isEmpty: Boolean
    get() = if (editCount <= 0) {
      src.isEmpty
    } else {
      appendMessages.isEmpty() && uniqueMessages.isEmpty() && queueMessages.isEmpty()
    }

  override val hasSubscribeMessages: Boolean
    get() = if (editCount <= 0) {
      src.isEmpty
    } else {
      !appendMessages.isEmpty() || !uniqueMessages.isEmpty()
    }

  override fun append(message: String, group: String) {
    val oldMessages = src.appendMessages[group]

    if (oldMessages == null || !oldMessages.contains(message)) {
      val groupMap = _appendMessages ?: src.appendMessages.toMutableMap()
      val oldEditMessages = groupMap[group]

      if (oldEditMessages == null) {
        groupMap[group] = setOf(message)
        ++editCount
      } else if (!oldEditMessages.contains(message)) {
        groupMap[group] = oldEditMessages
          .toMutableSet()
          .apply { add(message) }
        ++editCount
      }
      _appendMessages = groupMap
    }
  }

  override fun replace(message: String, group: String) {
    val oldMessages = src.uniqueMessages[group]

    if (oldMessages != message) {
      val groupMap = _uniqueMessages ?: src.uniqueMessages.toMutableMap()

      groupMap[group] = message
      ++editCount
      _uniqueMessages = groupMap
    }
  }

  override fun clearAppend(message: String, group: String) {
    val oldMessages = src.appendMessages[group]

    if (oldMessages != null && oldMessages.contains(message)) {
      val groupMap = _appendMessages ?: src.appendMessages.toMutableMap()
      val oldEditMessages = groupMap[group]

      if (oldEditMessages != null && oldEditMessages.contains(message)) {
        val newEditMessages = oldEditMessages
          .toMutableSet()
          .apply { remove(message) }

        if (newEditMessages.isEmpty()) {
          groupMap.remove(group)
        } else {
          groupMap[group] = newEditMessages
        }
        ++editCount
      }
      _appendMessages = groupMap
    }
  }

  override fun clearReplace(group: String) {
    val oldMessages = src.uniqueMessages[group]

    if (oldMessages != null) {
      val groupMap = _uniqueMessages ?: src.uniqueMessages.toMutableMap()

      if (groupMap.containsKey(group)) {
        groupMap.remove(group)
        ++editCount
      }
      _uniqueMessages = groupMap
    }
  }

  override fun queue(message: String) {
    val editMessages = _queueMessages ?: src.queueMessages.toMutableList()
    editMessages.add(message)
    ++editCount
    _queueMessages = editMessages
  }

  override fun clearQueueMessages() {
    val oldMessages = src.queueMessages

    if (!oldMessages.isEmpty()) {
      ++editCount
      _queueMessages = mutableListOf()
    }
  }

  override val isChanged: Boolean
    get() = editCount > 0

  override fun commit(): MessageBox {
    return if (isChanged) {
      MessageBoxImpl(
        appendMessages,
        uniqueMessages,
        queueMessages
      )
    } else {
      src
    }
  }
}