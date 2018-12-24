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

import com.ijoic.akka.websocket.client.ClientOptions
import java.time.Duration

/**
 * Default socket options
 *
 * @author verstsiu created at 2018-11-29 11:57
 */
data class DefaultSocketOptions(
  val url: String,
  val pingMessage: String = "",
  val pingDuration: Duration = Duration.ZERO,
  val disconnectWhenIdle: Boolean = false,
  val disconnectWhenIdleDelay: Duration = Duration.ofSeconds(30),
  val retryType: RetryType = RetryType.PERIOD_REPEAT,
  val retryIntervals: List<Duration> = listOf(
    Duration.ZERO,
    Duration.ofSeconds(2),
    Duration.ofSeconds(4),
    Duration.ofSeconds(8),
    Duration.ofSeconds(16)
  ),
  val proxyHost: String? = null,
  val proxyPort: Int? = null
): ClientOptions