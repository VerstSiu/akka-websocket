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

import com.ijoic.metrics.BaseMetricsMessage
import java.io.Serializable

/**
 * Send message
 *
 * @author verstsiu created at 2018-11-24 22:11
 */
sealed class SendMessage: BaseMetricsMessage(), Serializable

/**
 * Append message
 */
data class AppendMessage(val message: Serializable, val group: String): SendMessage(), Serializable {
  companion object {
    private const val serialVersionUID: Long = 1
  }
}

/**
 * Replace message
 */
data class ReplaceMessage(val message: Serializable, val group: String): SendMessage(), Serializable {
  companion object {
    private const val serialVersionUID: Long = 1
  }
}

/**
 * Clear append message
 */
data class ClearAppendMessage(val message: Serializable, val group: String, val pairMessage: Serializable): SendMessage(), Serializable {
  companion object {
    private const val serialVersionUID: Long = 1
  }
}

/**
 * Clear replace message
 */
data class ClearReplaceMessage(val message: Serializable, val group: String): SendMessage(), Serializable {
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