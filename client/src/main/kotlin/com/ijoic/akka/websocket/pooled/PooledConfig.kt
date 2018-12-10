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

/**
 * Pooled config
 *
 * @author verstsiu created at 2018-12-08 12:02
 */
data class PooledConfig(
  val initConnectionSize: Int = DEFAULT_INIT_CONNECTION_SIZE) {

  /**
   * Returns valid pooled config instance
   */
  internal fun checkValid(): PooledConfig {
    return PooledConfig(
      initConnectionSize = this.initConnectionSize.takeIf { it >= 1 } ?: DEFAULT_INIT_CONNECTION_SIZE
    )
  }

  companion object {
    private const val DEFAULT_INIT_CONNECTION_SIZE = 2
  }
}