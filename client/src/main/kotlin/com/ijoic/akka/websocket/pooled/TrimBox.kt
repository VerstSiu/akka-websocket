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

/**
 * Trim box
 *
 * @author verstsiu created at 2018-12-18 20:14
 */
internal class TrimBox {

  /**
   * Returns result of trim [items]
   */
  fun doFinal(items: List<SendMessage>): List<SendMessage> {
    val flagItems = items.mapIndexed { pos, message -> FlagItem(message, pos, null) }

    flagItems.forEach {
      if (it.keepFlag != null) {
        return@forEach
      }
      val message = it.message

      when(message) {
        is AppendMessage -> {
          upgradeAppendFlags(flagItems, message.info)
        }
        is ReplaceMessage -> {
          if (!message.strict) {
            upgradeReplaceFlags(flagItems, message.info)
          } else {
            upgradeStrictFlags(flagItems, message.info)
          }
        }
        is ClearAppendMessage -> {
          upgradeAppendFlags(flagItems, message.info)
        }
        is ClearReplaceMessage -> {
          if (!message.strict) {
            upgradeReplaceFlags(flagItems, message.info)
          } else {
            upgradeStrictFlags(flagItems, message.info)
          }
        }
        is QueueMessage -> {
          it.keepFlag = true
        }
      }
    }

    return flagItems
      .filter { it.keepFlag == true }
      .map { it.message }
  }

  private fun upgradeAppendFlags(flagItems: List<FlagItem>, info: SubscribeInfo) {
    val remainedItems = flagItems.filter { item -> item.keepFlag == null }
    val subscribeItems = remainedItems.filterMessageItems(AppendMessage::class.java, info)
    val unsubscribeItems = remainedItems.filterMessageItems(ClearAppendMessage::class.java, info)

    subscribeItems.forEach { item -> item.keepFlag = false }
    unsubscribeItems.forEach { item -> item.keepFlag = false }

    val lastSubscribe = subscribeItems.lastOrNull()?.pos
    val lastUnsubscribe = unsubscribeItems.lastOrNull()?.pos

    when {
      lastSubscribe == null -> {
        unsubscribeItems.first().keepFlag = true
      }
      lastUnsubscribe == null -> {
        subscribeItems.first().keepFlag = true
      }
      lastSubscribe > lastUnsubscribe -> {
        subscribeItems.first().keepFlag = true
      }
      lastSubscribe < lastUnsubscribe -> {
        unsubscribeItems.first().keepFlag = true
      }
    }
  }

  private fun upgradeReplaceFlags(flagItems: List<FlagItem>, info: SubscribeInfo) {
    val remainedItems = flagItems.filter { item -> item.keepFlag == null }
    val subscribeItems = remainedItems.filterReplaceItems(info)
    val unsubscribeItems = remainedItems.filterClearReplaceItems(info)

    subscribeItems.forEach { item -> item.keepFlag = false }
    unsubscribeItems.forEach { item -> item.keepFlag = false }

    val lastSubscribe = subscribeItems.lastOrNull()?.pos
    val lastUnsubscribe = unsubscribeItems.lastOrNull()?.pos

    when {
      lastSubscribe == null -> {
        unsubscribeItems.first().keepFlag = true
      }
      lastUnsubscribe == null -> {
        subscribeItems.last().keepFlag = true
      }
      lastSubscribe > lastUnsubscribe -> {
        subscribeItems.last().keepFlag = true
      }
      lastSubscribe < lastUnsubscribe -> {
        unsubscribeItems.first().keepFlag = true
      }
    }
  }

  private fun upgradeStrictFlags(flagItems: List<FlagItem>, info: SubscribeInfo) {
    val remainedItems = flagItems.filter { item -> item.keepFlag == null }
    val subscribeItems = remainedItems.filterStrictItems(info)
    val unsubscribeItems = remainedItems.filterClearStrictItems(info)

    subscribeItems.forEach { item -> item.keepFlag = false }
    unsubscribeItems.forEach { item -> item.keepFlag = false }

    val lastSubscribe = subscribeItems.lastOrNull()?.pos
    val lastUnsubscribe = unsubscribeItems.lastOrNull()?.pos

    when {
      lastSubscribe == null -> {
        unsubscribeItems.first().keepFlag = true
      }
      lastUnsubscribe == null -> {
        subscribeItems.first().keepFlag = true
      }
      lastSubscribe > lastUnsubscribe -> {
        subscribeItems.first().keepFlag = true
      }
      lastSubscribe < lastUnsubscribe -> {
        unsubscribeItems.first().keepFlag = true
      }
    }
  }

  private fun <T: SubscribeMessage> List<FlagItem>.filterMessageItems(type: Class<T>, info: SubscribeInfo): List<FlagItem> {
    return this.filter { item ->
      item.checkSubscribeInfo(info) && type.isInstance(item.message)
    }
  }

  private fun List<FlagItem>.filterReplaceItems(info: SubscribeInfo): List<FlagItem> {
    return this.filter { item ->
      item.message is ReplaceMessage && !item.message.strict && item.message.info.group == info.group
    }
  }

  private fun List<FlagItem>.filterClearReplaceItems(info: SubscribeInfo): List<FlagItem> {
    return this.filter { item ->
      item.message is ClearReplaceMessage && !item.message.strict && item.message.info.group == info.group
    }
  }

  private fun List<FlagItem>.filterStrictItems(info: SubscribeInfo): List<FlagItem> {
    return this.filter { item ->
      item.checkSubscribeInfo(info) && item.message is ReplaceMessage && item.message.strict
    }
  }

  private fun List<FlagItem>.filterClearStrictItems(info: SubscribeInfo): List<FlagItem> {
    return this.filter { item ->
      item.checkSubscribeInfo(info) && item.message is ClearReplaceMessage && item.message.strict
    }
  }

  private fun FlagItem.checkSubscribeInfo(info: SubscribeInfo): Boolean {
    return message is SubscribeMessage && message.info.group == info.group && message.info.subscribe == info.subscribe
  }

  /**
   * Flag item
   */
  private class FlagItem(
    val message: SendMessage,
    val pos: Int,
    var keepFlag: Boolean?
  )
}