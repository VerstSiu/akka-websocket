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
package com.ijoic.akka.websocket.state.impl

import com.ijoic.akka.websocket.state.ClientState
import com.ijoic.akka.websocket.state.MutableClientState

/**
 * Returns mutable instance of current message box
 *
 * @author verstsiu created at 2018-11-26 17:45
 */
internal fun ClientState.edit(): MutableClientState {
  return MutableClientStateImpl(this)
}

/**
 * Returns mutable instance of current message box
 *
 * @author verstsiu created at 2018-11-26 17:45
 */
internal fun ClientState.clearRetryStatus(): MutableClientState {
  return MutableClientStateImpl(this).apply {
    retryCount = 0
    retryPeriod = 0
  }
}