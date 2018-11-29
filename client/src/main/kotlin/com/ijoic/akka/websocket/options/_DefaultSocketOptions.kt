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
 * Toggle current config with [newUrl]
 *
 * @author verstsiu created at 2018-11-29 17:33
 */
fun DefaultSocketOptions.toggleUrl(newUrl: String): DefaultSocketOptions {
  return DefaultSocketOptions(
    newUrl,
    this.pingMessage,
    this.pingDuration,
    this.disconnectWhenIdle,
    this.disconnectWhenIdleDelay
  )
}