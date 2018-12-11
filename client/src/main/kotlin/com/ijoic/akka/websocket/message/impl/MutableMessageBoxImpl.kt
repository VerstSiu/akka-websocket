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
import java.io.Serializable

/**
 * Mutable message box impl
 *
 * @author verstsiu created at 2018-11-24 21:15
 */
internal class MutableMessageBoxImpl(
  private val src: MessageBox = MessageBoxImpl.blank): MutableMessageBox {

  private var _appendMessages: MutableMap<String, Set<SubscribeInfo>>? = null
  private var _uniqueMessages: MutableMap<String, SubscribeInfo>? = null
  private var _queueMessages: MutableList<Serializable>? = null

  private var editCount = 0

  override val appendMessages: Map<String, Set<SubscribeInfo>>
    get() = _appendMessages ?: src.appendMessages
  override val uniqueMessages: Map<String, SubscribeInfo>
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

  override fun dispatchSubscribeMessage(message: SubscribeMessage): Boolean {
    return when(message) {
      is AppendMessage -> append(message.info)
      is ReplaceMessage -> replace(message.info)
      is ClearAppendMessage -> clearAppend(message.info)
      is ClearReplaceMessage -> clearReplace(message.info)
      else -> false
    }
  }

  private fun append(info: SubscribeInfo): Boolean {
    val group = info.group
    val oldMessages = src.appendMessages[group]
    var statChanged = false

    if (oldMessages == null || !oldMessages.containsInfo(info)) {
      val groupMap = _appendMessages ?: src.appendMessages.toMutableMap()
      val oldEditMessages = groupMap[group]

      if (oldEditMessages == null) {
        groupMap[group] = setOf(info)
        statChanged = true
        ++editCount
      } else if (!oldEditMessages.containsInfo(info)) {
        groupMap[group] = oldEditMessages
          .toMutableSet()
          .apply { add(info) }
        statChanged = true
        ++editCount
      }
      _appendMessages = groupMap
    }
    return statChanged
  }

  private fun replace(info: SubscribeInfo): Boolean {
    val group = info.group
    val oldMessages = src.uniqueMessages[group]
    var statChanged = false

    if (oldMessages?.subscribe != info.subscribe) {
      val groupMap = _uniqueMessages ?: src.uniqueMessages.toMutableMap()

      groupMap[group] = info
      statChanged = true
      ++editCount
      _uniqueMessages = groupMap
    }
    return statChanged
  }

  private fun clearAppend(info: SubscribeInfo): Boolean {
    val group = info.group
    val oldMessages = src.appendMessages[group]
    var statChanged = false

    if (oldMessages != null && oldMessages.containsInfo(info)) {
      val groupMap = _appendMessages ?: src.appendMessages.toMutableMap()
      val oldEditMessages = groupMap[group]

      if (oldEditMessages != null) {
        val oldInfo = oldEditMessages.oldInfoOrNull(info)

        if (oldInfo != null) {
          val newEditMessages = oldEditMessages
            .toMutableSet()
            .apply { remove(oldInfo) }

          if (newEditMessages.isEmpty()) {
            groupMap.remove(group)
          } else {
            groupMap[group] = newEditMessages
          }
          statChanged = true
          ++editCount
        }
      }
      _appendMessages = groupMap
    }
    return statChanged
  }

  private fun clearReplace(info: SubscribeInfo): Boolean {
    val group = info.group
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