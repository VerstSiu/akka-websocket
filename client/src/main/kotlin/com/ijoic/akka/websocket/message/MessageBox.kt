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
 * Message box
 *
 * @author verstsiu created at 2018-11-24 20:44
 */
internal interface MessageBox {
  /**
   * Append messages (group - set items)
   */
  val appendMessages: Map<String, Set<String>>

  /**
   * Unique messages (group - unique item)
   */
  val uniqueMessages: Map<String, String>

  /**
   * Queue messages (items)
   */
  val queueMessages: List<String>

  /**
   * Box empty status
   */
  val isEmpty: Boolean

  /**
   * Returns true if subscribe messages not empty
   */
  val hasSubscribeMessages: Boolean

}