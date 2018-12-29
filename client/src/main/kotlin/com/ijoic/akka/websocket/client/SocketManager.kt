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
import com.ijoic.akka.websocket.options.DefaultSocketOptions
import com.ijoic.akka.websocket.state.SocketState
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

  /**
   * Connection active status
   */
  private var isConnectionActive = true

  /**
   * Wait for connect status
   */
  private var waitForConnect = false

  /**
   * Active messages
   */
  private val activeMessages = MessageBox()

  /**
   * Idle messages
   */
  private val idleMessages = MessageBox()

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

  override fun preStart() {
    isConnectionActive = true
    state = SocketState.DISCONNECTED
  }

  override fun postStop() {
    isConnectionActive = false
    waitForConnect = false
    clearRetryStatus()
    activeMessages.reset()
    idleMessages.reset()
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
      .match(BatchSendMessage::class.java) {
        it.statReceived()
        dispatchBatchSendMessage(it)
      }
      .match(SendMessage::class.java) {
        it.statReceived()
        dispatchSendMessage(it)
      }
      .match(SocketMessage::class.java) {
        it.statReceived()
        requester.tell(it.statSend(), self)

        if (!isConnectionActive) {
          checkAndCloseConnection()
        } else {
          when (it) {
            is ConnectionCompleted -> onConnectionCompleted()
            is ConnectionError -> onConnectionFailure()
            is ConnectionClosed -> onConnectionClosed()
          }
        }
      }
      .match(RequestConnect::class.java) {
        it.statReceived()
        onRequestConnect()
      }
      .match(RequestDisconnect::class.java) {
        it.statReceived()
        onRequestDisconnect()
      }
      .match(RequestClearSubscribe::class.java) {
        it.statReceived()
        onRequestClearSubscribe()
      }
      .match(PingMessage::class.java) {
        it.statReceived()

        if (state == SocketState.CONNECTED) {
          client.send(pingMessage)
        }
      }
      .match(DisconnectMessage::class.java) {
        it.statReceived()
        if (state == SocketState.CONNECTED) {
          resetPingTask()
          waitForConnect = false
          updateSocketState(SocketState.DISCONNECTING)
          client.disconnect()
        }
      }
      .match(RetryConnectMessage::class.java) {
        it.statReceived()
        if (state == SocketState.RETRY_CONNECTING) {
          resetRetryConnectTask()
          updateSocketState(SocketState.CONNECTING)
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

  private fun dispatchBatchSendMessage(event: BatchSendMessage) {
    dispatchMessageItems(event.items)
  }

  private fun dispatchSendMessage(msg: SendMessage) {
    dispatchMessageItems(listOf(msg))
  }

  private fun dispatchMessageItems(items: Collection<SendMessage>) {
    if (!isConnectionActive) {
      idleMessages.addMessageItems(items)
      checkAndCloseConnection()
    } else {
      when(state) {
        SocketState.DISCONNECTED -> {
          idleMessages.addMessageItems(items)

          if (!idleMessages.isEmpty) {
            prepareOpenConnection()
          }
        }
        SocketState.CONNECTING -> {
          idleMessages.addMessageItems(items)
          resetIdleDisconnectTask()
          waitForConnect = false
        }
        SocketState.CONNECTED -> {
          waitForConnect = false
          activeAndSendMessageItems(items)

          if (activeMessages.subscribeSize == 0) {
            prepareIdleDisconnectTask()
          } else {
            resetIdleDisconnectTask()
          }
        }
        SocketState.DISCONNECTING -> {
          idleMessages.addMessageItems(items)

          if (!idleMessages.isEmpty) {
            waitForConnect = true
          }
        }
        SocketState.RETRY_CONNECTING -> {
          idleMessages.addMessageItems(items)
        }
      }
    }
  }

  private fun onConnectionCompleted() {
    resetPingTask()
    resetRetryConnectTask()
    clearRetryStatus()

    when(state) {
      SocketState.DISCONNECTED,
      SocketState.CONNECTING,
      SocketState.DISCONNECTING,
      SocketState.RETRY_CONNECTING -> {
        waitForConnect = false
        clearRetryStatus()

        updateSocketState(SocketState.CONNECTED)
        val items = idleMessages.allMessages()
        idleMessages.reset()

        activeAndSendMessageItems(items)
        preparePingTask()
      }
      SocketState.CONNECTED -> {
        // already connected
        // do nothing
      }
    }

    if (activeMessages.subscribeSize <= 0) {
      prepareIdleDisconnectTask()
    } else {
      resetIdleDisconnectTask()
    }
  }

  private fun onConnectionFailure() {
    resetPingTask()

    when(state) {
      SocketState.DISCONNECTED -> {
        if (waitForConnect) {
          waitForConnect = false
          updateSocketState(SocketState.CONNECTING)
          client.connect(options, socketListener)
        } else {
          prepareRetryConnect()
        }
      }
      SocketState.CONNECTING -> {
        waitForConnect = false
        updateSocketState(SocketState.DISCONNECTED)
        prepareRetryConnect()
      }
      SocketState.CONNECTED -> {
        waitForConnect = false
        updateSocketState(SocketState.DISCONNECTED)
        translateActiveMessagesToIdle()
        prepareRetryConnect()
      }
      SocketState.DISCONNECTING -> {
        if (waitForConnect) {
          waitForConnect = false
          updateSocketState(SocketState.CONNECTING)
          client.connect(options, socketListener)
        } else {
          updateSocketState(SocketState.DISCONNECTED)
        }
      }
      SocketState.RETRY_CONNECTING -> {
        prepareRetryConnect()
      }
    }
  }

  private fun onConnectionClosed() {
    resetPingTask()
    waitForConnect = false

    when(state) {
      SocketState.DISCONNECTED -> {
        prepareRetryConnect()
      }
      SocketState.CONNECTING,
      SocketState.RETRY_CONNECTING -> {
        updateSocketState(SocketState.DISCONNECTED)
        prepareRetryConnect()
      }
      SocketState.CONNECTED -> {
        updateSocketState(SocketState.DISCONNECTED)
        translateActiveMessagesToIdle()
        prepareRetryConnect()
      }
      SocketState.DISCONNECTING -> {
        if (waitForConnect) {
          updateSocketState(SocketState.CONNECTING)
          client.connect(options, socketListener)
        } else {
          updateSocketState(SocketState.DISCONNECTED)
        }
      }
    }
  }

  private fun onRequestConnect() {
    resetRetryConnectTask()
    isConnectionActive = true

    when(state) {
      SocketState.DISCONNECTED,
      SocketState.RETRY_CONNECTING -> {
        resetPingTask()
        waitForConnect = false
        updateSocketState(SocketState.CONNECTING)
        client.connect(options, socketListener)
      }
      SocketState.CONNECTING,
      SocketState.CONNECTED -> {
        waitForConnect = false
      }
      SocketState.DISCONNECTING -> {
        resetPingTask()
        waitForConnect = true
      }
    }
  }

  private fun onRequestDisconnect() {
    resetRetryConnectTask()
    resetIdleDisconnectTask()
    resetPingTask()
    clearRetryStatus()
    isConnectionActive = true
    waitForConnect = false

    when(state) {
      SocketState.DISCONNECTED,
      SocketState.CONNECTING,
      SocketState.DISCONNECTING -> {
        // do nothing
      }
      SocketState.CONNECTED -> {
        updateSocketState(SocketState.DISCONNECTING)
        translateActiveMessagesToIdle()
        client.disconnect()
      }
      SocketState.RETRY_CONNECTING -> {
        updateSocketState(SocketState.DISCONNECTED)
      }
    }
  }

  private fun onRequestClearSubscribe() {
    if (!isConnectionActive) {
      activeMessages.reset()
      idleMessages.clearSubscribeMessages()
      checkAndCloseConnection()
      return
    }

    when(state) {
      SocketState.CONNECTED -> {
        waitForConnect = false

        val items = activeMessages.allSubscribeMessages()
        activeMessages.clearSubscribeMessages()

        items
          .mapNotNull { it as? SubscribeMessage }
          .forEach { client.send(it.info.unsubscribe) }

        prepareIdleDisconnectTask()
      }
      else -> {
        activeMessages.reset()
        idleMessages.clearSubscribeMessages()
      }
    }
  }

  private fun prepareOpenConnection() {
    resetIdleDisconnectTask()
    waitForConnect = false
    updateSocketState(SocketState.CONNECTING)
    client.connect(options, socketListener)
  }

  private fun checkAndCloseConnection() {
    waitForConnect = false

    when(state) {
      SocketState.DISCONNECTED,
      SocketState.CONNECTING,
      SocketState.DISCONNECTING -> {
        clearRetryStatus()
        resetRetryConnectTask()
      }
      SocketState.CONNECTED -> {
        resetPingTask()
        resetIdleDisconnectTask()
        updateSocketState(SocketState.DISCONNECTING)
        translateActiveMessagesToIdle()
        client.disconnect()
      }
      SocketState.RETRY_CONNECTING -> {
        clearRetryStatus()
        resetRetryConnectTask()
        updateSocketState(SocketState.DISCONNECTED)
      }
    }
  }

  private fun activeAndSendMessageItems(items: Collection<SendMessage>) {
    items.forEach { item ->
      if (activeMessages.addMessage(item)) {
        when(item) {
          is AppendMessage -> client.send(item.info.subscribe)
          is ReplaceMessage -> client.send(item.info.subscribe)
          is ClearAppendMessage -> client.send(item.info.unsubscribe)
          is ClearReplaceMessage -> client.send(item.info.unsubscribe)
          is QueueMessage -> client.send(item.message)
        }
      }
    }
    activeMessages.clearQueueMessages()
  }

  private fun translateActiveMessagesToIdle() {
    idleMessages.addMessageItems(activeMessages.allSubscribeMessages())
    activeMessages.reset()
  }

  /* -- socket state :begin -- */

  /**
   * Socket state
   */
  private var state = SocketState.DISCONNECTED

  private fun updateSocketState(state: SocketState) {
    if (this.state != state) {
      this.state = state
      requester.tell(state, self)
    }
  }

  /* -- socket state :end -- */

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

  /**
   * Retry count
   */
  private var retryCount = 0

  /**
   * Retry period
   */
  private var retryPeriod = 0

  private var retryTask: Cancellable? = null

  private fun prepareRetryConnect(): Boolean {
    if (!retryEnabled) {
      return false
    }
    if (disconnectWhenIdle && idleMessages.isEmpty) {
      return false
    }
    val oldRetryCount = retryCount
    val oldRetryPeriod = retryPeriod

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
      clearRetryStatus()
    } else if (retryInterval.isZero) {
      updateSocketState(SocketState.CONNECTING)
      retryCount = editRetryCount
      retryPeriod = editRetryPeriod
      client.connect(options, socketListener)
    } else {
      updateSocketState(SocketState.RETRY_CONNECTING)
      retryCount = editRetryCount
      retryPeriod = editRetryPeriod

      retryTask = context.system.scheduler
        .scheduleOnce(
          retryInterval,
          { self.tell(RetryConnectMessage(), self) },
          context.system.dispatcher
        )
    }
    return true
  }

  private fun clearRetryStatus() {
    retryCount = 0
    retryPeriod = 0
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

  /**
   * Request connect
   */
  class RequestConnect: MetricsMessage()

  /**
   * Request disconnect
   */
  class RequestDisconnect: MetricsMessage()

  /**
   * Request clear subscribe
   */
  class RequestClearSubscribe: MetricsMessage()

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