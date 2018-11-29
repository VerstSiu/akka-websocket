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

import akka.actor.*
import com.ijoic.akka.websocket.message.*
import com.ijoic.akka.websocket.message.impl.allMessages
import com.ijoic.akka.websocket.message.impl.dispatchMessage
import com.ijoic.akka.websocket.message.impl.edit
import com.ijoic.akka.websocket.options.DefaultSocketOptions
import com.ijoic.akka.websocket.state.ClientState
import com.ijoic.akka.websocket.state.SocketState
import com.ijoic.akka.websocket.state.impl.ClientStateImpl
import com.ijoic.akka.websocket.state.impl.edit
import java.time.Duration

/**
 * WebSocket client manager
 *
 * @author verstsiu created at 2018-11-24 09:24
 */
class SocketManager(
  private val options: ClientOptions,
  private val requester: ActorRef,
  client: SocketClient? = null): AbstractActor() {

  private val client: SocketClient = client ?: ClientFactory.loadClientInstance()

  /**
   * Socket listener
   */
  private val socketListener = { message: SocketMessage ->
    self.tell(message, self)
  }

  override fun createReceive(): Receive {
    return waitingForReplies(ClientStateImpl.blank)
  }

  private fun dispatchSendMessage(status: ClientState, msg: SendMessage) {
    val editStatus = status.edit()
    val editMessages = status.messages
      .edit()
      .dispatchMessage(msg)

    editStatus.messages = editMessages.commit()

    when(status.state) {
      SocketState.DISCONNECTED -> if (!editMessages.isEmpty) {
        resetIdleDisconnectTask()
        editStatus.waitForConnect = false
        editStatus.state = SocketState.CONNECTING
        context.become(waitingForReplies(editStatus))
        client.connect(options, socketListener)
      }
      SocketState.CONNECTING -> {
        resetIdleDisconnectTask()
        editStatus.waitForConnect = false
        context.become(waitingForReplies(editStatus))
      }
      SocketState.CONNECTED -> {
        editStatus.waitForConnect = false

        when(msg) {
          is AppendMessage -> if (editMessages.isChanged) {
            context.become(waitingForReplies(editStatus))
            client.send(msg.message)
          }
          is ReplaceMessage -> if (editMessages.isChanged) {
            context.become(waitingForReplies(editStatus))
            client.send(msg.message)
          }
          is ClearAppendMessage -> if (editMessages.isChanged) {
            context.become(waitingForReplies(editStatus))
            client.send(msg.message)
          }
          is ClearReplaceMessage -> if (editMessages.isChanged) {
            context.become(waitingForReplies(editStatus))
            client.send(msg.message)
          }
          is QueueMessage -> {
            client.send(msg.message)
          }
        }

        if (!editMessages.hasSubscribeMessages) {
          prepareIdleDisconnectTask()
        } else {
          resetIdleDisconnectTask()
        }
      }
      SocketState.DISCONNECTING -> if (!editMessages.isEmpty) {
        editStatus.waitForConnect = true
        context.become(waitingForReplies(editStatus))
      }
    }
  }

  private fun onConnectionCompleted(status: ClientState) {
    resetPingTask()
    val editStatus = status.edit()

    when(status.state) {
      SocketState.DISCONNECTED,
      SocketState.CONNECTING,
      SocketState.DISCONNECTING -> {
        editStatus.waitForConnect = false
        editStatus.state = SocketState.CONNECTED

        editStatus.messages = status.messages
          .edit()
          .apply {
            clearQueueMessages()
          }
          .commit()

        context.become(waitingForReplies(editStatus))

        status.messages.allMessages().forEach {
          client.send(it)
        }

        preparePingTask()
      }
      SocketState.CONNECTED -> {
        // already connected
        // do nothing
      }
    }

    if (!editStatus.messages.hasSubscribeMessages) {
      prepareIdleDisconnectTask()
    } else {
      resetIdleDisconnectTask()
    }
  }

  private fun onConnectionFailure(status: ClientState) {
    resetPingTask()
    val editStatus = status.edit()

    when(status.state) {
      SocketState.DISCONNECTED -> {
        if (status.waitForConnect) {
          editStatus.waitForConnect = false
          editStatus.state = SocketState.CONNECTING
          context.become(waitingForReplies(editStatus))
          client.connect(options, socketListener)
        }
      }
      SocketState.CONNECTING,
      SocketState.CONNECTED -> {
        editStatus.waitForConnect = false
        editStatus.state = SocketState.DISCONNECTED
        context.become(waitingForReplies(editStatus))
      }
      SocketState.DISCONNECTING -> {
        if (status.waitForConnect) {
          editStatus.waitForConnect = false
          editStatus.state = SocketState.CONNECTING
          context.become(waitingForReplies(editStatus))
          client.connect(options, socketListener)
        } else {
          editStatus.state = SocketState.DISCONNECTED
          context.become(waitingForReplies(editStatus))
        }
      }
    }
  }

  private fun onConnectionClosed(status: ClientState) {
    resetPingTask()
    val editStatus = status.edit()

    when(status.state) {
      SocketState.DISCONNECTED -> {
        // already disconnected
        // do nothing
      }
      SocketState.CONNECTING,
      SocketState.CONNECTED -> {
        editStatus.waitForConnect = false
        editStatus.state = SocketState.DISCONNECTED
        context.become(waitingForReplies(editStatus))
      }
      SocketState.DISCONNECTING -> {
        if (status.waitForConnect) {
          editStatus.waitForConnect = false
          editStatus.state = SocketState.CONNECTING
          context.become(waitingForReplies(editStatus))
          client.connect(options, socketListener)
        } else {
          editStatus.state = SocketState.DISCONNECTED
          context.become(waitingForReplies(editStatus))
        }
      }
    }
  }

  private fun waitingForReplies(status: ClientState): Receive {
    return receiveBuilder()
      .match(SendMessage::class.java) {
        dispatchSendMessage(status, it)
      }
      .match(SocketMessage::class.java) {
        requester.tell(it, self)

        when(it) {
          is ConnectionCompleted -> onConnectionCompleted(status)
          is ConnectionFailure -> onConnectionFailure(status)
          is ConnectionClosed -> onConnectionClosed(status)
        }
      }
      .match(PingMessage::class.java) {
        val options = this.options as? DefaultSocketOptions

        if (options != null && status.state == SocketState.CONNECTED) {
          client.send(options.pingMessage)
        }
      }
      .match(DisconnectMessage::class.java) {
        if (status.state == SocketState.CONNECTED) {
          resetPingTask()
          client.disconnect()
        }
      }
      .match(Terminated::class.java) {
        resetPingTask()
        client.disconnect()
        client.release()
      }
      .build()
  }

  /* -- ping task :begin -- */

  private var pingTask: Cancellable? = null

  private fun preparePingTask() {
    val options = this.options as? DefaultSocketOptions ?: return

    if (!options.pingDuration.isZero && !options.pingMessage.isEmpty()) {
      pingTask = context.system.scheduler
        .schedule(
          Duration.ZERO,
          options.pingDuration,
          { self.tell(PingMessage, self) },
          context.system.dispatcher
        )
    }
  }

  private fun resetPingTask() {
    pingTask.checkAndCancel()
  }

  /* -- ping task :end -- */

  /* -- idle disconnect task :begin -- */

  private var idleDisconnectTask: Cancellable? = null

  private fun prepareIdleDisconnectTask() {
    val options = this.options as? DefaultSocketOptions ?: return

    if (options.disconnectWhenIdle && idleDisconnectTask == null) {
      idleDisconnectTask = context.system.scheduler
        .scheduleOnce(
          options.disconnectWhenIdleDelay,
          { self.tell(DisconnectMessage, self) },
          context.system.dispatcher
        )
    }
  }

  private fun resetIdleDisconnectTask() {
    idleDisconnectTask.checkAndCancel()
  }

  /* -- idle disconnect task :end -- */

  /**
   * Ping message
   */
  private object PingMessage

  /**
   * Disconnect message
   */
  private object DisconnectMessage

  companion object {
    /**
     * Returns webSocket actor props instance with [config], [requester] and [client]
     */
    @JvmStatic
    fun props(config: ClientOptions, requester: ActorRef, client: SocketClient? = null): Props {
      return Props.create(SocketManager::class.java, config, requester, client)
    }

    /**
     * Check and cancel current cancellable
     */
    private fun Cancellable?.checkAndCancel() {
      if (this != null && !this.isCancelled) {
        this.cancel()
      }
    }
  }
}