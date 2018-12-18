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
package com.ijoic.akka.websocket.message

import java.io.Serializable

/**
 * Message box
 *
 * @author verstsiu created at 2018-11-24 20:44
 */
internal class MessageBox {
  /**
   * Append messages (group - set items)
   */
  private val appendMessages: MutableMap<String, MutableList<SubscribeInfo>> = mutableMapOf()

  /**
   * Unique messages (group - unique item)
   */
  private val uniqueMessages: MutableMap<String, ReplaceMessage> = mutableMapOf()

  /**
   * Queue messages (items)
   */
  private val queueMessages: MutableList<Serializable> = mutableListOf()

  /**
   * Subscribe message size
   */
  var subscribeSize = 0
    private set

  /**
   * Add [message]
   */
  fun addMessage(message: SendMessage): Boolean {
    when(message) {
      is AppendMessage -> {
        val info = message.info
        val group = info.group
        val msgList = appendMessages[group] ?: mutableListOf<SubscribeInfo>().also {
          appendMessages[group] = it
        }

        if (msgList.isEmpty() || !msgList.containsInfo(info)) {
          msgList.add(info)
          ++subscribeSize
          return true
        }
      }
      is ReplaceMessage -> {
        val info = message.info
        val group = info.group
        val msgOld = uniqueMessages[group]

        if (msgOld?.info?.subscribe != info.subscribe) {
          if (msgOld == null) {
            ++subscribeSize
          }
          uniqueMessages[group] = message
          return true
        }
      }
      is ClearAppendMessage -> {
        val info = message.info
        val group = info.group
        val msgList = appendMessages[group]

        if (msgList != null) {
          val oldInfo = msgList.oldInfoOrNull(info)

          if (oldInfo != null) {
            msgList.remove(oldInfo)
            --subscribeSize
          }
          return true
        }
      }
      is ClearReplaceMessage -> {
        val info = message.info
        val group = info.group
        val msgOld = uniqueMessages[group]

        if (msgOld != null && (!message.strict || msgOld.info.subscribe == info.subscribe)) {
          --subscribeSize
          uniqueMessages.remove(group)
          return true
        }
      }
      is QueueMessage -> {
        queueMessages.add(message.message)
        return true
      }
    }
    return false
  }

  /**
   * Add message [items]
   */
  fun addMessageItems(items: Collection<SendMessage>) {
    items.forEach { addMessage(it) }
  }

  /**
   * Remove [message]
   */
  fun removeMessage(message: SendMessage): Boolean {
    when(message) {
      is AppendMessage -> {
        val info = message.info
        val group = info.group
        val msgList = appendMessages[group]

        if (msgList != null) {
          val oldInfo = msgList.oldInfoOrNull(info)

          if (oldInfo != null) {
            msgList.remove(oldInfo)
            --subscribeSize
          }
          return true
        }
      }
      is ReplaceMessage -> {
        val info = message.info
        val group = info.group
        val msgOld = uniqueMessages[group]

        if (msgOld != null) {
          --subscribeSize
          uniqueMessages.remove(group)
          return true
        }
      }
      is QueueMessage -> {
        if (queueMessages.contains(message.message)) {
          queueMessages.remove(message.message)
          return true
        }
      }
    }
    return false
  }

  /**
   * Remove message [items]
   */
  fun removeMessageItems(items: Collection<SendMessage>) {
    items.forEach { removeMessage(it) }
  }

  /**
   * Pop out subscribe messages with expected item [size]
   */
  fun popSubscribeMessages(size: Int): List<SendMessage> {
    if (size <= 0) {
      return emptyList()
    }
    if (subscribeSize <= size) {
      subscribeSize = 0
      return allSubscribeMessages().also {
        appendMessages.clear()
        uniqueMessages.clear()
      }
    }
    val appendItems = popAppendMessages(size)
    val uniqueItems = popUniqueMessages(size - appendItems.size)

    return appendItems
      .toMutableList()
      .apply { addAll(uniqueItems) }
  }

  /**
   * Pop out append messages with expected item [size]
   */
  private fun popAppendMessages(size: Int): List<SendMessage> {
    if (size <= 0) {
      return emptyList()
    }
    val messages = allAppendMessages()

    if (messages.size <= size) {
      appendMessages.clear()
      subscribeSize -= messages.size
      return messages
    }
    val editMessages = messages.subList(0, size)
    return editMessages.also {
      removeMessageItems(it)
    }
  }

  /**
   * Pop out unique messages with expected item [size]
   */
  private fun popUniqueMessages(size: Int): List<SendMessage> {
    if (size <= 0) {
      return emptyList()
    }
    val messages = allUniqueMessages()

    if (messages.size <= size) {
      uniqueMessages.clear()
      subscribeSize -= messages.size
      return messages
    }
    val editMessages = messages.subList(0, size)
    return editMessages.also {
      removeMessageItems(it)
    }
  }

  /**
   * Returns [message] contains status
   */
  fun containsMessage(message: AppendMessage): Boolean {
    val info = message.info
    val group = info.group
    val msgList = appendMessages[group]

    return msgList != null && msgList.containsInfo(info)
  }

  /**
   * Returns [message] contains status
   */
  fun containsMessage(message: ReplaceMessage): Boolean {
    val info = message.info
    val group = info.group
    val msgOld = uniqueMessages[group]

    return msgOld?.info?.subscribe == info.subscribe
  }

  /**
   * Returns reverse [message] contains status
   */
  fun containsReverseMessage(message: ClearAppendMessage): Boolean {
    val info = message.info
    val group = info.group
    val msgList = appendMessages[group]

    return msgList != null && msgList.containsInfo(info)
  }

  /**
   * Returns reverse [message] contains status
   */
  fun containsReverseMessage(message: ClearReplaceMessage): Boolean {
    val info = message.info
    val group = info.group
    val msgOld = uniqueMessages[group]

    return msgOld != null && (!message.strict || msgOld.info.subscribe == info.subscribe)
  }

  /**
   * Returns replace [group] contains status
   */
  fun containsReplaceGroup(group: String, strict: Boolean = false): Boolean {
    val msgOld = uniqueMessages[group]

    return msgOld != null && msgOld.strict == strict
  }

  /**
   * Returns all messages of current message box
   */
  fun allMessages(): List<SendMessage> {
    return mutableListOf<SendMessage>().apply {
      addAll(allAppendMessages())
      addAll(allUniqueMessages())
      addAll(allQueueMessages())
    }
  }

  /**
   * Returns all subscribe messages of current message box
   */
  fun allSubscribeMessages(): List<SendMessage> {
    return mutableListOf<SendMessage>().apply {
      addAll(allAppendMessages())
      addAll(allUniqueMessages())
    }
  }

  /**
   * Returns all append messages of current message box
   */
  private fun allAppendMessages(): List<SendMessage> {
    val messages = mutableListOf<SendMessage>()

    for ((_, info) in appendMessages) {
      messages.addAll(info.map { AppendMessage(it) })
    }
    return messages
  }

  /**
   * Returns all unique messages of current message box
   */
  private fun allUniqueMessages(): List<SendMessage> {
    val messages = mutableListOf<SendMessage>()

    for ((_, message) in uniqueMessages) {
      messages.add(message)
    }
    return messages
  }

  /**
   * Returns all queue messages of current message box
   */
  fun allQueueMessages(): List<SendMessage> {
    return queueMessages.map { QueueMessage(it) }
  }

  /**
   * Message empty status
   */
  val isEmpty: Boolean
    get() = subscribeSize == 0 && queueMessages.isEmpty()

  /**
   * Reset all contained messages
   */
  fun reset() {
    clearSubscribeMessages()
    clearQueueMessages()
  }

  /**
   * Clear subscribe messages
   */
  fun clearSubscribeMessages() {
    subscribeSize = 0
    appendMessages.clear()
    uniqueMessages.clear()
  }

  /**
   * Clear queue messages
   */
  fun clearQueueMessages() {
    queueMessages.clear()
  }

}