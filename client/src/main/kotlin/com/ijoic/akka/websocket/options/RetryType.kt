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
package com.ijoic.akka.websocket.options

/**
 * Retry type
 *
 * @author verstsiu created at 2018-12-07 09:42
 */
enum class RetryType(val retryEnabled: Boolean, val peroidRepeat: Boolean) {
  /**
   * None.
   */
  NONE(false, false),

  /**
   * Repeat with retry intervals
   */
  PERIOD_REPEAT(true, true),

  /**
   * Repeat with retry intervals (once)
   */
  PERIOD_ONCE(true, false)
}