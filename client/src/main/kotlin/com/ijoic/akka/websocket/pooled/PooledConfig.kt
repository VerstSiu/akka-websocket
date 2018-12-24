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

import com.ijoic.akka.websocket.pooled.proxy.ProxyConfig

/**
 * Pooled config
 *
 * @author verstsiu created at 2018-12-08 12:02
 */
data class PooledConfig(
  val initConnectionSize: Int = DEFAULT_INIT_CONNECTION_SIZE,
  val initSubscribe: Int = DEFAULT_INIT_SUBSCRIBE,
  val maxSubscribe: Int = DEFAULT_MAX_SUBSCRIBE,
  val minIdle: Int = DEFAULT_MIN_IDLE,
  val maxIdle: Int = DEFAULT_MAX_IDLE,
  val assignMessageEnabled: Boolean = false,
  val proxyConfig: ProxyConfig? = null) {

  /**
   * Returns valid pooled config instance
   */
  internal fun checkValid(): PooledConfig {
    return PooledConfig(
      initConnectionSize = this.initConnectionSize.takeIf { it >= 1 } ?: DEFAULT_INIT_CONNECTION_SIZE,
      initSubscribe = this.initSubscribe.takeIf { it >= 1 } ?: DEFAULT_INIT_SUBSCRIBE,
      maxSubscribe = this.maxSubscribe.takeIf { it >= 1 } ?: DEFAULT_MAX_SUBSCRIBE,
      minIdle = this.minIdle.takeIf { it >= 0 } ?: DEFAULT_MIN_IDLE,
      maxIdle = this.maxIdle.takeIf { it >= 0 } ?: DEFAULT_MAX_IDLE,
      assignMessageEnabled = this.assignMessageEnabled,
      proxyConfig = this.proxyConfig
    )
  }

  companion object {
    private const val DEFAULT_INIT_CONNECTION_SIZE = 2
    private const val DEFAULT_INIT_SUBSCRIBE = 20
    private const val DEFAULT_MAX_SUBSCRIBE = 40
    private const val DEFAULT_MIN_IDLE = 0
    private const val DEFAULT_MAX_IDLE = 5
  }
}