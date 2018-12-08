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
 * Mutable message box impl
 *
 * @author verstsiu created at 2018-11-24 21:15
 */
internal class MutableMessageBoxImpl(
  private val src: MessageBox = MessageBoxImpl.blank): MutableMessageBox {

  private var _appendMessages: MutableMap<String, Set<Serializable>>? = null
  private var _uniqueMessages: MutableMap<String, Serializable>? = null
  private var _queueMessages: MutableList<Serializable>? = null

  private var editCount = 0

  override val appendMessages: Map<String, Set<Serializable>>
    get() = _appendMessages ?: src.appendMessages
  override val uniqueMessages: Map<String, Serializable>
    get() = _uniqueMessages ?: src.uniqueMessages
  override val queueMessages: List<Serializable>
    get() = _queueMessages ?: src.queueMessages

  override val isEmpty: Boolean
    get() = if (editCount <= 0) {
      src.isEmpty
    } else {
      appendMessages.isEmpty() && uniqueMessages.isEmpty() && queueMessages.isEmpty()
    }

  override val subscribeMessageSize: Int
    get() = if (editCount <= 0) {
      src.subscribeMessageSize
    } else {
      measureSubscribeMessageSize()
    }

  override fun append(message: Serializable, group: String): Boolean {
    val oldMessages = src.appendMessages[group]
    var statChanged = false

    if (oldMessages == null || !oldMessages.contains(message)) {
      val groupMap = _appendMessages ?: src.appendMessages.toMutableMap()
      val oldEditMessages = groupMap[group]

      if (oldEditMessages == null) {
        groupMap[group] = setOf(message)
        statChanged = true
        ++editCount
      } else if (!oldEditMessages.contains(message)) {
        groupMap[group] = oldEditMessages
          .toMutableSet()
          .apply { add(message) }
        statChanged = true
        ++editCount
      }
      _appendMessages = groupMap
    }
    return statChanged
  }

  override fun replace(message: Serializable, group: String): Boolean {
    val oldMessages = src.uniqueMessages[group]
    var statChanged = false

    if (oldMessages != message) {
      val groupMap = _uniqueMessages ?: src.uniqueMessages.toMutableMap()

      groupMap[group] = message
      statChanged = true
      ++editCount
      _uniqueMessages = groupMap
    }
    return statChanged
  }

  override fun clearAppend(message: Serializable, group: String): Boolean {
    val oldMessages = src.appendMessages[group]
    var statChanged = false

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
        statChanged = true
        ++editCount
      }
      _appendMessages = groupMap
    }
    return statChanged
  }

  override fun clearReplace(group: String): Boolean {
    val oldMessages = src.uniqueMessages[group]
    var statChanged = false

    if (oldMessages != null) {
      val groupMap = _uniqueMessages ?: src.uniqueMessages.toMutableMap()

      if (groupMap.containsKey(group)) {
        groupMap.remove(group)
        statChanged = true
        ++editCount
      }
      _uniqueMessages = groupMap
    }
    return statChanged
  }

  override fun queue(message: Serializable): Boolean {
    val editMessages = _queueMessages ?: src.queueMessages.toMutableList()
    editMessages.add(message)
    ++editCount
    _queueMessages = editMessages
    return true
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