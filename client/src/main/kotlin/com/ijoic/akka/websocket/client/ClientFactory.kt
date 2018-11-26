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
package com.ijoic.akka.websocket.client

import java.util.*

/**
 * WebSocket client factory
 *
 * @author verstsiu created at 2018-11-26 15:51
 */
internal object ClientFactory {
  /**
   * Load webSocket instance
   *
   * And throws [RuntimeException] when config client path not found
   * or could create client instance
   *
   * Config file path: resources/ijoic_websocket.conf
   * Config file content: client-extension=your_class_extension_path
   */
  @Throws(RuntimeException::class)
  @JvmStatic
  fun loadClientInstance(): SocketClient {
    val props = Properties()
    val resInput = ClassLoader.getSystemResourceAsStream("ijoic_websocket.conf")

    props.load(resInput)

    try {
      resInput.close()
    } catch (t: Throwable) {
      t.printStackTrace()
    }

    val clientPath = props.getProperty("client-extension") ?: throw RuntimeException("client extension not configured")
    val clazz = Class.forName(clientPath)
    val instance = clazz.newInstance()

    if (instance is SocketClient) {
      return instance
    } else {
      throw RuntimeException("invalid clazz instance: expected - SocketClient, found - $instance")
    }
  }
}