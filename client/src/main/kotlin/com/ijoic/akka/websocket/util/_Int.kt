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
package com.ijoic.akka.websocket.util

import java.lang.IllegalArgumentException

/**
 * Ceil divided with expected [divisor]
 *
 * @author verstsiu created at 2018-12-11 15:08
 */
@Throws(IllegalArgumentException::class)
internal fun Int.ceilDivided(divisor: Int): Int {
  if (divisor == 0) {
    throw IllegalArgumentException("divisor could not be zero")
  }
  var result = this / divisor

  if (this > 0 && this % divisor != 0) {
    ++result
  }
  return result
}