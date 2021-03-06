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
package com.ijoic.akka.websocket

import com.ijoic.akka.websocket.client.*
import com.ijoic.akka.websocket.options.DefaultSocketOptions
import okhttp3.*
import okio.ByteString
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.ByteBuffer

/**
 * OkHttp socket client
 *
 * @author verstsiu created at 2018-11-26 11:12
 */
class OkHttpSocketClient : SocketClient {
  private var socket: WebSocket? = null
  private var listener: ((SocketMessage) -> Unit)? = null

  override fun connect(options: ClientOptions, listener: (SocketMessage) -> Unit) {
    if (options !is DefaultSocketOptions) {
      throw IllegalArgumentException("invalid options: $options")
    }
    this.listener = listener

    val client = getClientInstance(options)
    val request = Request.Builder()
      .url(options.url)
      .build()

    client.newWebSocket(request, object: WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        socket = webSocket
        post(ConnectionCompleted())
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        post(ConnectionError(cause = t))
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        post(ReceiveText(text))
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        post(ReceiveBytes(bytes.toByteArray()))
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        socket = null
        post(ConnectionClosed(code.toString(), reason))
      }
    })
  }

  override fun disconnect() {
    socket?.also {
      socket = null
      it.close(1000, "normal close")
    }
  }

  override fun release() {
    listener = null
  }

  override fun send(message: Any) {
    val socket = this.socket ?: return

    when(message) {
      is String -> socket.send(message)
      is ByteArray -> socket.send(
        ByteString.of(
          ByteBuffer.wrap(message)
        )
      )
    }
  }

  private fun getClientInstance(options: DefaultSocketOptions): OkHttpClient {
    val proxyHost = options.proxyHost
    val proxyPort = options.proxyPort

    if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null) {
      return OkHttpClient.Builder()
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
        .build()
    }
    return OkHttpClient()
  }

  /**
   * Post socket [message]
   */
  private fun post(message: SocketMessage) {
    listener?.invoke(message)
  }

}