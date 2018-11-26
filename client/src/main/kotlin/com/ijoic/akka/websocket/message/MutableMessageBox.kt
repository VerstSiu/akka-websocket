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

/**
 * Mutable message box
 *
 * @author verstsiu created at 2018-11-24 20:50
 */
internal interface MutableMessageBox: MessageBox {
  /**
   * Append [message] within [group]
   */
  fun append(message: String, group: String)

  /**
   * Replace [message] with [group]
   */
  fun replace(message: String, group: String)

  /**
   * Clear append [message] within [group]
   */
  fun clearAppend(message: String, group: String)

  /**
   * Clear replace message within [group]
   */
  fun clearReplace(group: String)

  /**
   * Queue [message] for which send once after socket connect completed
   */
  fun queue(message: String)

  /**
   * Clear queue message list
   */
  fun clearQueueMessages()

  /**
   * Current edit change status
   */
  val isChanged: Boolean

  /**
   * Commit edit changes
   */
  fun commit(): MessageBox
}