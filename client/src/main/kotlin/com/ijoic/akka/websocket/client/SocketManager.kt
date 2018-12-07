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
import com.ijoic.akka.websocket.state.MutableClientState
import com.ijoic.akka.websocket.state.SocketState
import com.ijoic.akka.websocket.state.impl.ClientStateImpl
import com.ijoic.akka.websocket.state.impl.clearRetryStatus
import com.ijoic.akka.websocket.state.impl.edit
import com.ijoic.metrics.MetricsMessage
import com.ijoic.metrics.statReceived
import com.ijoic.metrics.statSend
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

  private val pingMessage: String
  private val pingDuration: Duration

  private val disconnectWhenIdle: Boolean
  private val disconnectWhenIdleDelay: Duration

  private val retryEnabled: Boolean
  private val retryRepeat: Boolean
  private val retryIntervals: List<Duration>

  init {
    if (options is DefaultSocketOptions) {
      pingMessage = options.pingMessage
      pingDuration = options.pingDuration
      disconnectWhenIdle = options.disconnectWhenIdle
      disconnectWhenIdleDelay = options.disconnectWhenIdleDelay
      retryEnabled = options.retryType.retryEnabled
      retryRepeat = options.retryType.peroidRepeat
      retryIntervals = options.retryIntervals
        .filter { !it.isNegative }
        .takeIf { !it.isEmpty() } ?: listOf(Duration.ZERO)
    } else {
      pingMessage = ""
      pingDuration = Duration.ZERO
      disconnectWhenIdle = false
      disconnectWhenIdleDelay = Duration.ZERO
      retryEnabled = false
      retryRepeat = false
      retryIntervals = emptyList()
    }
  }

  /**
   * Socket listener
   */
  private val socketListener = { message: SocketMessage ->
    self.tell(message.statSend(), self)
  }

  override fun createReceive(): Receive {
    return waitingForReplies(ClientStateImpl.blank, null)
  }

  private fun dispatchSendMessage(status: ClientState, msg: SendMessage) {
    val editStatus = status.edit()
    val editMessages = status.messages
      .edit()
      .dispatchMessage(msg)

    editStatus.messages = editMessages.commit()

    when(status.state) {
      SocketState.DISCONNECTED -> {
        if (!editMessages.isEmpty) {
          resetIdleDisconnectTask()
          editStatus.waitForConnect = false
          editStatus.state = SocketState.CONNECTING
          context.become(waitingForReplies(editStatus, status))
          client.connect(options, socketListener)
        } else if (editMessages.isChanged) {
          context.become(waitingForReplies(editStatus, status))
        }
      }
      SocketState.CONNECTING -> {
        resetIdleDisconnectTask()
        editStatus.waitForConnect = false
        context.become(waitingForReplies(editStatus, status))
      }
      SocketState.CONNECTED -> {
        editStatus.waitForConnect = false

        when(msg) {
          is AppendMessage -> if (editMessages.isChanged) {
            context.become(waitingForReplies(editStatus, status))
            client.send(msg.message)
          }
          is ReplaceMessage -> if (editMessages.isChanged) {
            context.become(waitingForReplies(editStatus, status))
            client.send(msg.message)
          }
          is ClearAppendMessage -> if (editMessages.isChanged) {
            context.become(waitingForReplies(editStatus, status))
            client.send(msg.message)
          }
          is ClearReplaceMessage -> if (editMessages.isChanged) {
            context.become(waitingForReplies(editStatus, status))
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
      SocketState.DISCONNECTING -> {
        if (!editMessages.isEmpty) {
          editStatus.waitForConnect = true
        }
        context.become(waitingForReplies(editStatus, status))
      }
      SocketState.RETRY_CONNECTING -> if(editMessages.isChanged) {
        context.become(waitingForReplies(editStatus, status))
      }
    }
  }

  private fun onConnectionCompleted(status: ClientState) {
    resetPingTask()
    resetRetryConnectTask()
    val editStatus = status.clearRetryStatus()

    when(status.state) {
      SocketState.DISCONNECTED,
      SocketState.CONNECTING,
      SocketState.DISCONNECTING,
      SocketState.RETRY_CONNECTING -> {
        editStatus.waitForConnect = false
        editStatus.state = SocketState.CONNECTED

        editStatus.messages = status.messages
          .edit()
          .apply {
            clearQueueMessages()
          }
          .commit()

        context.become(waitingForReplies(editStatus, status))

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
          context.become(waitingForReplies(editStatus, status))
          client.connect(options, socketListener)
        } else {
          prepareRetryConnect(editStatus, status)
        }
      }
      SocketState.CONNECTING,
      SocketState.CONNECTED -> {
        editStatus.waitForConnect = false
        editStatus.state = SocketState.DISCONNECTED

        if (!prepareRetryConnect(editStatus, status)) {
          context.become(waitingForReplies(editStatus, status))
        }
      }
      SocketState.DISCONNECTING -> {
        if (status.waitForConnect) {
          editStatus.waitForConnect = false
          editStatus.state = SocketState.CONNECTING
          context.become(waitingForReplies(editStatus, status))
          client.connect(options, socketListener)
        } else {
          editStatus.state = SocketState.DISCONNECTED
          context.become(waitingForReplies(editStatus, status))
        }
      }
      SocketState.RETRY_CONNECTING -> {
        prepareRetryConnect(editStatus, status)
      }
    }
  }

  private fun onConnectionClosed(status: ClientState) {
    resetPingTask()
    val editStatus = status.edit()
    editStatus.waitForConnect = false

    when(status.state) {
      SocketState.DISCONNECTED -> {
        prepareRetryConnect(editStatus, status)
      }
      SocketState.CONNECTING,
      SocketState.CONNECTED,
      SocketState.RETRY_CONNECTING -> {
        editStatus.state = SocketState.DISCONNECTED

        if (!prepareRetryConnect(editStatus, status)) {
          context.become(waitingForReplies(editStatus, status))
        }
      }
      SocketState.DISCONNECTING -> {
        if (status.waitForConnect) {
          editStatus.state = SocketState.CONNECTING
          context.become(waitingForReplies(editStatus, status))
          client.connect(options, socketListener)
        } else {
          editStatus.state = SocketState.DISCONNECTED
          context.become(waitingForReplies(editStatus, status))
        }
      }
    }
  }

  private fun waitingForReplies(status: ClientState, oldStatus: ClientState?): Receive {
    if (oldStatus != null && oldStatus.state != status.state) {
      requester.tell(status.state, self)
    }

    return receiveBuilder()
      .match(SendMessage::class.java) {
        it.statReceived()
        dispatchSendMessage(status, it)
      }
      .match(SocketMessage::class.java) {
        it.statReceived()
        requester.tell(it.statSend(), self)

        when(it) {
          is ConnectionCompleted -> onConnectionCompleted(status)
          is ConnectionFailure -> onConnectionFailure(status)
          is ConnectionClosed -> onConnectionClosed(status)
        }
      }
      .match(PingMessage::class.java) {
        it.statReceived()

        if (status.state == SocketState.CONNECTED) {
          client.send(pingMessage)
        }
      }
      .match(DisconnectMessage::class.java) {
        it.statReceived()
        if (status.state == SocketState.CONNECTED) {
          resetPingTask()
          client.disconnect()
        }
      }
      .match(RetryConnectMessage::class.java) {
        it.statReceived()
        if (status.state == SocketState.RETRY_CONNECTING) {
          resetRetryConnectTask()
          val editStatus = status.edit()
          editStatus.state = SocketState.CONNECTING
          context.become(waitingForReplies(editStatus, status))
          client.connect(options, socketListener)
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
    if (!pingDuration.isZero && !pingMessage.isEmpty()) {
      pingTask = context.system.scheduler
        .schedule(
          Duration.ZERO,
          pingDuration,
          { self.tell(PingMessage(), self) },
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
    if (disconnectWhenIdle && idleDisconnectTask == null) {
      idleDisconnectTask = context.system.scheduler
        .scheduleOnce(
          disconnectWhenIdleDelay,
          { self.tell(DisconnectMessage(), self) },
          context.system.dispatcher
        )
    }
  }

  private fun resetIdleDisconnectTask() {
    idleDisconnectTask.checkAndCancel()
  }

  /* -- idle disconnect task :end -- */

  /* -- retry task :begin -- */

  private var retryTask: Cancellable? = null

  private fun prepareRetryConnect(status: MutableClientState, oldStatus: ClientState): Boolean {
    if (!retryEnabled) {
      return false
    }
    if (disconnectWhenIdle && status.messages.isEmpty) {
      return false
    }
    val oldRetryCount = status.retryCount
    val oldRetryPeriod = status.retryPeriod

    var editRetryCount = oldRetryCount + 1
    var editRetryPeriod = oldRetryPeriod
    val retryInterval: Duration

    if (editRetryCount > retryIntervals.size) {
      editRetryCount = 0
      ++editRetryPeriod
      retryInterval = retryIntervals[0]
    } else {
      retryInterval = retryIntervals[oldRetryCount]
    }

    if (editRetryPeriod > 0 && !retryRepeat) {
      status.retryCount = 0
      status.retryPeriod = 0
      context.become(waitingForReplies(status, oldStatus))
    } else if (retryInterval.isZero) {
      status.state = SocketState.CONNECTING
      status.retryCount = editRetryCount
      status.retryPeriod = editRetryPeriod
      context.become(waitingForReplies(status, oldStatus))
      client.connect(options, socketListener)
    } else {
      status.state = SocketState.RETRY_CONNECTING
      status.retryCount = editRetryCount
      status.retryPeriod = editRetryPeriod
      context.become(waitingForReplies(status, oldStatus))

      retryTask = context.system.scheduler
        .scheduleOnce(
          retryInterval,
          { self.tell(RetryConnectMessage(), self) },
          context.system.dispatcher
        )
    }
    return true
  }

  private fun resetRetryConnectTask() {
    retryTask.checkAndCancel()
  }

  /* -- retry task :end -- */

  /**
   * Ping message
   */
  private class PingMessage: MetricsMessage()

  /**
   * Disconnect message
   */
  private class DisconnectMessage: MetricsMessage()

  /**
   * Retry connect message
   */
  private class RetryConnectMessage: MetricsMessage()

  companion object {
    /**
     * Returns socket manager props instance with [options], [requester] and [client]
     */
    @JvmStatic
    @JvmOverloads
    fun props(options: ClientOptions, requester: ActorRef, client: SocketClient? = null): Props {
      return Props.create(SocketManager::class.java, options, requester, client)
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