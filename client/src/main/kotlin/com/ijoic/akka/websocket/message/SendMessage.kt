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

import com.ijoic.metrics.MetricsMessage
import java.io.Serializable

/**
 * Send message
 *
 * @author verstsiu created at 2018-11-24 22:11
 */
sealed class SendMessage: MetricsMessage(), Serializable

/**
 * Subscribe message
 *
 * @author verstsiu created at 2018-12-11 17:44
 */
abstract class SubscribeMessage: SendMessage(), Serializable {
  /**
   * Subscribe info
   */
  abstract val info: SubscribeInfo

  /**
   * Identity
   */
  abstract val id: String?
}

/**
 * Append message
 */
data class AppendMessage(override val info: SubscribeInfo, override val id: String? = null): SubscribeMessage(), Serializable {
  companion object {
    private const val serialVersionUID: Long = 1
  }
}

/**
 * Replace message
 *
 * Clear replace would only match subscribe equals when [strict] == true
 */
data class ReplaceMessage(override val info: SubscribeInfo, val strict: Boolean = false, override val id: String? = null): SubscribeMessage(), Serializable {
  companion object {
    private const val serialVersionUID: Long = 1
  }
}

/**
 * Clear append message
 */
data class ClearAppendMessage(override val info: SubscribeInfo, override val id: String? = null): SubscribeMessage(), Serializable {
  companion object {
    private const val serialVersionUID: Long = 1
  }
}

/**
 * Clear replace message
 *
 * Clear replace would only match subscribe equals when [strict] == true
 */
data class ClearReplaceMessage(override val info: SubscribeInfo, val strict: Boolean = false, override val id: String? = null): SubscribeMessage(), Serializable {
  companion object {
    private const val serialVersionUID: Long = 1
  }
}

/**
 * Queue message
 */
data class QueueMessage(val message: Serializable): SendMessage(), Serializable {
  companion object {
    private const val serialVersionUID: Long = 1
  }
}

/**
 * Batch send message
 */
data class BatchSendMessage(val items: List<SendMessage>): MetricsMessage()

/**
 * Subscribe info
 *
 * @author verstsiu created at 2018-12-11 17:35
 */
data class SubscribeInfo(val subscribe: Serializable, val group: String, val unsubscribe: Serializable): Serializable {
  companion object {
    private const val serialVersionUID: Long = 1
  }
}

/**
 * Wrap current message items as batch or single send message automatically
 */
internal fun List<SendMessage>.autoBatch(): MetricsMessage {
  return if (this.size == 1) {
    this[0]
  } else {
    BatchSendMessage(this)
  }
}

/* -- subscribe info extensions :begin -- */

/**
 * Wrap current info as append message
 */
fun SubscribeInfo.asAppendMessage(id: String? = null): AppendMessage {
  return AppendMessage(this, id)
}

/**
 * Wrap current info as clear append message
 */
fun SubscribeInfo.asClearAppend(id: String? = null): ClearAppendMessage {
  return ClearAppendMessage(this, id)
}

/**
 * Wrap current info as replace message
 */
fun SubscribeInfo.asReplaceMessage(id: String? = null): ReplaceMessage {
  return ReplaceMessage(this, false, id)
}

/**
 * Wrap current info as clear replace message
 */
fun SubscribeInfo.asClearReplace(id: String? = null): ClearReplaceMessage {
  return ClearReplaceMessage(this, false, id)
}

/**
 * Wrap current info as strict message
 */
fun SubscribeInfo.asStrictMessage(id: String? = null): ReplaceMessage {
  return ReplaceMessage(this, true, id)
}

/**
 * Wrap current info as clear strict message
 */
fun SubscribeInfo.asClearStrict(id: String? = null): ClearReplaceMessage {
  return ClearReplaceMessage(this, true, id)
}

/* -- subscribe info extensions :end -- */

/**
 * Returns subscribe info equals status
 */
internal fun SubscribeMessage.subscribeEquals(other: SubscribeMessage): Boolean {
  if (this.id != null) {
    return this.id == other.id
  }
  return this.info.subscribe == other.info.subscribe
}

/**
 * Returns subscribe [message] contains status of current collection items
 */
internal fun <T: SubscribeMessage> Collection<T>.containsMessage(message: SubscribeMessage): Boolean {
  return this.any { it.subscribeEquals(message) }
}

/**
 * Returns old subscribe [message] or null
 */
internal fun <T: SubscribeMessage> Collection<T>.oldMessageOrNull(message: SubscribeMessage): T? {
  return this.firstOrNull { it.subscribeEquals(message) }
}

/**
 * Returns subscribe [info] contains status of current collection items
 */
internal fun Collection<SubscribeInfo>.containsInfo(info: SubscribeInfo): Boolean {
  return this.any { it.subscribe == info.subscribe }
}

/**
 * Returns old subscribe [info] or null
 */
internal fun Collection<SubscribeInfo>.oldInfoOrNull(info: SubscribeInfo): SubscribeInfo? {
  return this.firstOrNull { it.subscribe == info.subscribe }
}