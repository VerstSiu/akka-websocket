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

import org.junit.Test

/**
 * Integer extension test.
 *
 * @author verstsiu created at 2018-12-11 15:10
 */
class IntExtensionTest {
  @Test
  fun testCeilDivided() {
    testCeilDivided(-6, 3, -2)
    testCeilDivided(-4, 3, -1)
    testCeilDivided(-3, 3, -1)
    testCeilDivided(-2, 3, 0)
    testCeilDivided(-1, 3, 0)
    testCeilDivided(0, 3, 0)
    testCeilDivided(1, 3, 1)
    testCeilDivided(5, 3, 2)
    testCeilDivided(6, 3, 2)
    testCeilDivided(7, 3, 3)
  }

  private fun testCeilDivided(num: Int, divisor: Int, expected: Int) {
    assert(num.ceilDivided(divisor) == expected)
  }
}