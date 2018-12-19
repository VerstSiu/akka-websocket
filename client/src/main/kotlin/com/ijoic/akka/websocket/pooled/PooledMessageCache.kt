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
package com.ijoic.akka.websocket.pooled

import com.ijoic.akka.websocket.message.*
import com.ijoic.akka.websocket.util.bindVersion
import com.ijoic.akka.websocket.util.ceilDivided

/**
 * Pooled message cache
 *
 * @author verstsiu created 2018-12-17 16:57
 */
internal class PooledMessageCache {

  /**
   * Message box
   */
  private val msgBox = MessageBox()

  /**
   * Strict messages
   */
  private val strictMessages = mutableMapOf<String, MutableList<ReplaceMessage>>()

  /**
   * Strict subscribe size
   */
  private var strictSubscribeSize = 0

  /**
   * Edit count
   */
  private var editCount = 0

  /**
   * Message empty status
   */
  val isEmpty by bindVersion(this::editCount) {
    msgBox.isEmpty && strictMessages.isEmpty()
  }

  /**
   * Subscribe size
   */
  private val subscribeSize by bindVersion(this::editCount) {
    strictSubscribeSize + msgBox.subscribeSize
  }

  /**
   * Add [message]
   */
  fun addMessage(message: SendMessage) {
    when {
      message is ReplaceMessage && message.strict -> {
        val group = message.info.group
        val msgList = strictMessages[group] ?: mutableListOf<ReplaceMessage>().also {
          strictMessages[group] = it
        }

        if (msgList.isEmpty() || !msgList.any { it.info.subscribe == message.info.subscribe }) {
          ++editCount
          ++strictSubscribeSize
          msgList.add(message)
        }
      }
      message is ClearReplaceMessage && message.strict -> {
        removeStrictMessage(message.info)
      }
      else -> if (msgBox.addMessage(message)) {
        ++editCount
      }
    }
  }

  /**
   * Add message [items]
   */
  fun addMessageItems(items: Collection<SendMessage>) {
    items.forEach(this::addMessage)
  }

  /**
   * Remove [message]
   */
  fun removeMessage(message: SendMessage) {
    if (message is ReplaceMessage && message.strict) {
      removeStrictMessage(message.info)
    } else if (msgBox.removeMessage(message)) {
      ++editCount
    }
  }

  /**
   * Remove message [items]
   */
  fun removeMessageItems(items: Collection<SendMessage>) {
    items.forEach(this::removeMessage)
  }

  private fun removeStrictMessage(info: SubscribeInfo) {
    val group = info.group
    val msgList = strictMessages[group]
    val oldMessage = msgList?.firstOrNull { it.info.subscribe == info.subscribe }

    if (oldMessage != null) {
      ++editCount
      --strictSubscribeSize
      msgList.remove(oldMessage)

      if (msgList.isEmpty()) {
        strictMessages.remove(group)
      }
    }
  }

  /**
   * Popup subscribe message items with expected items [size]
   */
  fun popupSubscribeMessageItems(size: Int): List<SendMessage> {
    if (size <= 0) {
      return emptyList()
    }
    val items = mutableListOf<SendMessage>()
    var fetchSize = size

    // Add strict message items
    if (!strictMessages.isEmpty()) {
      val strictMsgListItems = strictMessages
        .map { (_, msgList) -> msgList }
        .sortedByDescending { it.size }

      if (!strictMsgListItems.isEmpty()) {
        for (index in 0 until strictMsgListItems.size) {
          val msgList = strictMsgListItems[index]
          val msg = msgList.firstOrNull()

          if (msg != null) {
            items.add(msg)
            --fetchSize
          }
        }
      }
    }
    removeMessageItems(items)

    // Add normal message items
    if (fetchSize > 0) {
      items.addAll(msgBox.popSubscribeMessages(fetchSize))
    }
    return items
  }

  /**
   * Popup queue message items
   */
  fun popupQueueMessageItems(): List<SendMessage> {
    val messages = msgBox.allQueueMessages()
    msgBox.clearQueueMessages()
    return messages
  }

  /**
   * Returns trimmed message items
   */
  fun trimMessageItems(items: List<SendMessage>): List<SendMessage> {
    val sourceItems = TrimBox().doFinal(items)
    val removedItems = mutableListOf<SendMessage>()

    items.forEach {
      when (it) {
        is AppendMessage -> if (msgBox.containsMessage(it)) {
          removedItems.add(it)
        }
        is ClearAppendMessage -> if (!msgBox.containsReverseAppendMessage(it.info)) {
          removedItems.add(it)
        }
        is ReplaceMessage -> {
          if (it.strict) {
            if (containsStrictMessage(it.info)) {
              removedItems.add(it)
            }
          } else if (msgBox.containsMessage(it)) {
            removedItems.add(it)
          }
        }
        is ClearReplaceMessage -> {
          if (it.strict) {
            if (!containsStrictMessage(it.info)) {
              removedItems.add(it)
            }
          } else if (!msgBox.containsReverseReplaceMessage(it.info)) {
            removedItems.add(it)
          }
        }
      }
    }

    return if (removedItems.isEmpty()) {
      sourceItems
    } else {
      sourceItems
        .toMutableList()
        .apply { removeAll(removedItems) }
    }
  }

  /**
   * Trim [message]
   */
  fun trimMessage(message: SendMessage): SendMessage? {
    when (message) {
      is AppendMessage -> if (msgBox.containsMessage(message)) {
        return null
      }
      is ClearAppendMessage -> if (!msgBox.containsReverseAppendMessage(message.info)) {
        return null
      }
      is ReplaceMessage -> {
        if (message.strict) {
          if (containsStrictMessage(message.info)) {
            return null
          }
        } else if (msgBox.containsMessage(message)) {
          return null
        }
      }
      is ClearReplaceMessage -> {
        if (message.strict) {
          if (!containsStrictMessage(message.info)) {
            return null
          }
        } else if (!msgBox.containsReverseReplaceMessage(message.info)) {
          return null
        }
      }
    }
    return message
  }

  /**
   * Returns strict message contains status
   */
  private fun containsStrictMessage(info: SubscribeInfo): Boolean {
    val msgList = strictMessages[info.group]

    return msgList != null && msgList.any { it.info.group == info.group && it.info.subscribe == info.subscribe }
  }

  /**
   * Returns required connection size
   */
  fun requiredConnectionSize(initSubscribe: Int): Int {
    return if (initSubscribe <= 0) {
      strictDepth()
    } else {
      Math.max(
        strictDepth(),
        subscribeSize.ceilDivided(initSubscribe)
      )
    }
  }

  /**
   * Returns average subscribe size measured by [maxSubscribe] and [connectionSize]
   */
  fun measureAverageSubscribeSize(maxSubscribe: Int, connectionSize: Int): Int {
    if (connectionSize <= 0) {
      return 0
    }
    val subscribeSize = this.subscribeSize

    if (connectionSize == 1) {
      return Math.min(subscribeSize, maxSubscribe)
    }
    return Math.min(
      subscribeSize.ceilDivided(connectionSize),
      maxSubscribe
    )
  }

  private fun strictDepth(): Int {
    if (strictMessages.isEmpty()) {
      return 0
    }
    val depth = strictMessages
      .map { (_, msgList) -> msgList.size }
      .max()

    return depth ?: 0
  }
}