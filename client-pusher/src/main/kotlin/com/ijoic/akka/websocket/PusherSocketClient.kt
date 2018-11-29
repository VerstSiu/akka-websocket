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
import com.ijoic.akka.websocket.options.WrapPusherOptions
import com.ijoic.akka.websocket.pusher.AddSubscribe
import com.ijoic.akka.websocket.pusher.RemoveSubscribe
import com.pusher.client.Pusher
import com.pusher.client.channel.Channel
import com.pusher.client.channel.ChannelEventListener
import com.pusher.client.channel.SubscriptionEventListener
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange

/**
 * Pusher socket client
 *
 * @author verstsiu created at 2018-11-29 11:30
 */
class PusherSocketClient : SocketClient, ConnectionEventListener, ChannelEventListener {
  private var pusher: Pusher? = null
  private var options: WrapPusherOptions? = null
  private var listener: ((SocketMessage) -> Unit)? = null

  private val channelMap = mutableMapOf<String, Channel>()
  private val listenerMap = mutableMapOf<String, SubscriptionEventListener>()

  override fun connect(options: ClientOptions, listener: (SocketMessage) -> Unit) {
    if (options !is WrapPusherOptions) {
      throw IllegalArgumentException("invalid options: expected WrapPusherOptions, found $options")
    }
    this.listener = listener

    val oldPusher = this.pusher
    val oldOptions = this.options

    if (oldPusher != null) {
      if (options == oldOptions) {
        oldPusher.connect(this)
        return
      } else {
        oldPusher.disconnect()
        channelMap.clear()
        listenerMap.clear()
        this.pusher = null
      }
    }
    val pusher = Pusher(options.appKey, options.options)
    this.pusher = pusher

    pusher.connect(this)
  }

  override fun disconnect() {
    pusher?.disconnect()
  }

  override fun release() {
    this.pusher = null
    this.listener = null
    channelMap.clear()
  }

  override fun send(message: Any) {
    val pusher = this.pusher ?: return

    when(message) {
      is AddSubscribe -> pusher.subscribe(message.channel, this, message.event)
      is RemoveSubscribe -> pusher.getChannel(message.channel)?.unbind(message.event, this)
    }
  }

  override fun onConnectionStateChange(change: ConnectionStateChange?) {
    println("connection state changed: from - ${change?.previousState}, to - ${change?.currentState}")
    change ?: return
    val currentState = change.currentState ?: return

    if (change.previousState != currentState) {
      when(currentState) {
        ConnectionState.CONNECTED -> post(ConnectionCompleted)
        ConnectionState.DISCONNECTED -> post(ConnectionClosed())
        ConnectionState.ALL,
        ConnectionState.CONNECTING,
        ConnectionState.DISCONNECTING,
        ConnectionState.RECONNECTING -> {
          // do nothing
        }
      }
    }
  }

  override fun onError(message: String?, code: String?, e: java.lang.Exception?) {
    println("connection error: message - $message, code - $code, error - $e")
    post(ConnectionFailure(e ?: RuntimeException("connection error: message - $message, code - $code")))
  }

  override fun onEvent(channelName: String?, eventName: String?, data: String?) {
    println("channel event: channel - $channelName, event - $eventName, data - $data")
    data ?: return
    post(ReceiveText(data))
  }

  override fun onSubscriptionSucceeded(channelName: String?) {
    println("subscription succeed: channel - $channelName")
    // do nothing
  }

  /**
   * Post socket [message]
   */
  private fun post(message: SocketMessage) {
    listener?.invoke(message)
  }
}