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

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import com.ijoic.akka.websocket.client.ClientOptions
import com.ijoic.akka.websocket.client.SocketClient
import com.ijoic.akka.websocket.client.SocketManager
import com.ijoic.akka.websocket.client.SocketMessage
import com.ijoic.akka.websocket.message.*
import com.ijoic.akka.websocket.state.SocketState
import com.ijoic.metrics.statReceived

/**
 * Pooled socket manager
 *
 * @author verstsiu created at 2018-12-06 20:38
 */
class PooledSocketManager(
  private val requester: ActorRef,
  private val createManager: (ActorContext, ActorRef, Int) -> ActorRef,
  config: PooledConfig): AbstractActor() {

  private val config: PooledConfig = config.checkValid()
  private var isConnectionActive = true

  private var genChildIndex = 0
  private val childManagers = mutableListOf<ActorRef>()
  private val channelsMap = mutableMapOf<ActorRef, ChannelState>()

  private val allMessages = PooledMessageCache()
  private val activeMessages = PooledMessageCache()
  private val idleMessages = PooledMessageCache()

  private fun dispatchBatchSendMessage(event: BatchSendMessage) {
    dispatchMessageItems(event.items, trimRequired = true)
  }

  private fun dispatchSendMessage(msg: SendMessage) {
    dispatchMessage(msg, trimRequired = true)
  }

  private fun dispatchMessageItems(items: List<SendMessage>, trimRequired: Boolean = false) {
    if (items.isEmpty()) {
      return
    }
    if (items.size == 1) {
      dispatchMessage(items.first(), trimRequired)
    } else {
      when {
        !isConnectionActive -> {
          // all connections must have been released
          // and active messages cleared
          allMessages.addMessageItems(items)
          idleMessages.addMessageItems(items)
        }
        childManagers.isEmpty() -> {
          // no any exist connections
          // active messages empty
          allMessages.addMessageItems(items)
          idleMessages.addMessageItems(items)

          if (!idleMessages.isEmpty) {
            checkAndPrepareConnections()
          }
        }
        else -> {
          val activeChannels = channelsMap
            .filter { (_, stat) -> stat.state == SocketState.CONNECTED }
            .map { (_, stat) -> stat }

          if (!activeChannels.isEmpty()) {
            // exist some of active connections
            val srcItems = if (trimRequired) {
              activeMessages.trimMessageItems(items)
            } else {
              items
            }
            allMessages.addMessageItems(srcItems)
            val averageSubscribeSize = allMessages.measureAverageSubscribeSize(config.maxSubscribe, activeChannels.size)
            var assignIndex = 0
            var assignItems: List<SendMessage>

            for (channel in activeChannels) {
              val assignSize = Math.max(
                Math.min(averageSubscribeSize - channel.messages.subscribeSize, srcItems.size - assignIndex),
                0
              )

              if (assignSize > 0) {
                assignItems = srcItems.subList(assignIndex, assignIndex + assignSize)

                channel.ref.tell(assignItems.autoBatch(), self)
                channel.messages.addMessageItems(assignItems)
                assignIndex += assignSize
              }

              if (assignIndex >= srcItems.size) {
                break
              }
            }
            if (assignIndex < srcItems.size) {
              activeMessages.addMessageItems(srcItems.subList(0, assignIndex))
              idleMessages.addMessageItems(srcItems.subList(assignIndex, srcItems.size))
            }

          } else {
            // connections already under preparing
            allMessages.addMessageItems(items)
            idleMessages.addMessageItems(items)

            checkAndPrepareConnections()
          }
        }
      }
    }
  }

  private fun dispatchMessage(msg: SendMessage, trimRequired: Boolean = false) {
    when {
      !isConnectionActive -> {
        allMessages.addMessage(msg)
        idleMessages.addMessage(msg)
      }
      childManagers.isEmpty() -> {
        allMessages.addMessage(msg)
        idleMessages.addMessage(msg)

        if (!idleMessages.isEmpty) {
          checkAndPrepareConnections()
        }
      }
      else -> {
        val activeChannels = channelsMap
          .filter { (_, stat) -> stat.state == SocketState.CONNECTED }
          .map { (_, stat) -> stat }

        if (!activeChannels.isEmpty()) {
          if (trimRequired) {
            activeMessages.trimMessage(msg) ?: return
          }
          allMessages.addMessage(msg)
          activeMessages.addMessage(msg)
          val channel = activeChannels[0]

          channel.dispatchIdleMessage(msg)
        } else {
          allMessages.addMessage(msg)
          idleMessages.addMessage(msg)

          checkAndPrepareConnections()
        }
      }
    }
  }

  private fun onRequestConnect() {
    if (childManagers.isEmpty()) {
      prepareChildConnections(config.initConnectionSize + config.minIdle)

    } else {
      for ((child, channel) in channelsMap) {
        when(channel.state) {
          SocketState.DISCONNECTED,
          SocketState.DISCONNECTING,
          SocketState.RETRY_CONNECTING -> {
            // restart connection
            channel.state = SocketState.CONNECTING
            child.tell(SocketManager.RequestConnect(), self)
          }
          else -> {
            // do nothing
          }
        }
      }
    }
    isConnectionActive = true
  }

  private fun onRequestDisconnect() {
    for ((child, channel) in channelsMap) {
      val oldMessages = channel.messages.allMessages()

      if (!oldMessages.isEmpty()) {
        child.tell(SocketManager.RequestClearSubscribe(), self)
        activeMessages.removeMessageItems(oldMessages)
        idleMessages.addMessageItems(oldMessages)
      }
      if (channel.state != SocketState.DISCONNECTED) {
        channel.state = SocketState.DISCONNECTING
        child.tell(SocketManager.RequestDisconnect(), self)
      }
      channel.resetSubscribeInitialized()
    }
    isConnectionActive = false
  }

  private fun onChildStateChanged(child: ActorRef, state: SocketState) {
    if (!childManagers.contains(child)) {
      return
    }
    val channel = channelsMap[child] ?: ChannelState(child).also {
      channelsMap[child] = it
    }
    val oldState = channel.state
    channel.state = state

    if (!isConnectionActive) {
      return
    }
    if (state == SocketState.DISCONNECTED) {
      channel.state = SocketState.CONNECTING

      if (oldState == SocketState.CONNECTED) {
        val oldMessages = channel.messages.allMessages()
        channel.messages.reset()
        child.tell(SocketManager.RequestClearSubscribe(), self)
        child.tell(SocketManager.RequestConnect(), self)
        recycleMessageItems(oldMessages)
      } else {
        child.tell(SocketManager.RequestConnect(), self)
      }
      return
    }
    if (oldState == state) {
      return
    }
    if (oldState == SocketState.CONNECTED) {
      onConnectionInactive(channel)
    } else if (state == SocketState.CONNECTED) {
      val channelInitialized = channel.isSubscribeInitialized
      assignIdleQueueMessageItems(channel)
      onConnectionActive(channel)

      if (channelInitialized) {
        balanceConnection(channel)
      }
    }
  }

  private fun onChildTerminated(child: ActorRef) {
    if (!childManagers.contains(child)) {
      return
    }
    context.unwatch(child)
    childManagers.remove(child)

    val channel = channelsMap[child]
    channelsMap.remove(child)

    if (channel != null) {
      val items = channel.messages.allMessages()

      if (!items.isEmpty()) {
        activeMessages.removeMessageItems(items)
        dispatchMessageItems(items)
      }
      checkAndPrepareConnections()
    }
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
        requester.forward(it, context)
      }
      .match(SocketState::class.java) {
        onChildStateChanged(sender, it)
      }
      .match(SocketManager.RequestConnect::class.java) {
        it.statReceived()
        onRequestConnect()
      }
      .match(SocketManager.RequestDisconnect::class.java) {
        it.statReceived()
        onRequestDisconnect()
      }
      .match(Terminated::class.java) {
        onChildTerminated(sender)
      }
      .build()
  }

  private fun recycleMessageItems(items: List<SendMessage>) {
    activeMessages.removeMessageItems(items)
    dispatchMessageItems(items)
  }

  private fun balanceConnection(channel: ChannelState) {
    val activeChannels = channelsMap
      .filter { (_, stat) -> stat.state == SocketState.CONNECTED }
      .map { (_, stat) -> stat }

    val averageSubscribeSize = allMessages.measureAverageSubscribeSize(config.maxSubscribe, activeChannels.size)
    val balanceItems = mutableListOf<SendMessage>()

    for (ch in activeChannels) {
      if (ch != channel && ch.messages.subscribeSize > averageSubscribeSize) {
        val editItems = ch.messages.popSubscribeMessages(ch.messages.subscribeSize - averageSubscribeSize)
        val reverseItems = editItems.mapNotNull {
          when(it) {
            is AppendMessage -> ClearAppendMessage(it.info)
            is ReplaceMessage -> ClearReplaceMessage(it.info, it.strict)
            else -> null
          }
        }

        ch.ref.tell(reverseItems.autoBatch(), self)
        balanceItems.addAll(editItems)
      }
    }

    if (!balanceItems.isEmpty()) {
      recycleMessageItems(balanceItems)
    }
  }

  /* -- child connections :begin -- */

  private fun checkAndPrepareConnections() {
    val oldActive = childManagers.size
    val requiredActive = Math.max(
      allMessages.requiredConnectionSize(config.initSubscribe),
      config.initConnectionSize
    )
    val minActive = requiredActive + config.minIdle
    val maxActive = requiredActive + config.maxIdle

    when {
      oldActive < minActive -> prepareChildConnections(minActive - oldActive)
      oldActive > maxActive -> {
        val items = releaseChildConnections(oldActive - maxActive)

        if (!items.isEmpty()) {
          dispatchMessageItems(items)
        }
      }
    }
  }

  private fun prepareChildConnections(childSize: Int) {
    if (childSize <= 0) {
      return
    }
    repeat(childSize) {
      val child = createManager(context, self, genChildIndex)
      ++genChildIndex

      child.tell(SocketManager.RequestConnect(), self)
      context.watch(child)
      childManagers.add(child)
      channelsMap[child] = ChannelState(child).apply {
        state = SocketState.CONNECTING
      }
    }
  }

  private fun releaseChildConnections(childSize: Int): List<SendMessage> {
    var releaseSize = childSize

    releaseSize -= releaseNotConnectedChildConnections(releaseSize)
    releaseSize -= releaseConnectedIdleChildConnections(releaseSize)

    if (releaseSize <= 0) {
      return emptyList()
    }
    // release channels with least messages
    val recycleMessages = mutableListOf<SendMessage>()
    val channels = channelsMap
      .values
      .sortedBy { it.messages.subscribeSize }

    for (ch in channels) {
      recycleMessages.addAll(ch.release())
      --releaseSize

      if (releaseSize <= 0) {
        break
      }
    }
    activeMessages.removeMessageItems(recycleMessages)
    return recycleMessages
  }

  private fun releaseNotConnectedChildConnections(childSize: Int): Int {
    if (childSize <= 0) {
      return 0
    }
    val blankChannels = channelsMap
      .filter { (_, channel) -> channel.state != SocketState.CONNECTED }
      .map { (_, channel) -> channel }

    if (blankChannels.size >= childSize) {
      blankChannels.subList(0, childSize).forEach { it.release() }
      return childSize
    }
    blankChannels.forEach { it.release() }

    return blankChannels.size
  }

  private fun releaseConnectedIdleChildConnections(childSize: Int): Int {
    if (childSize <= 0) {
      return 0
    }
    val blankChannels = channelsMap
      .filter { (_, channel) -> channel.state == SocketState.CONNECTED && channel.messages.subscribeSize == 0 }
      .map { (_, channel) -> channel }

    if (blankChannels.size >= childSize) {
      blankChannels.subList(0, childSize).forEach { it.release() }
      return childSize
    }
    blankChannels.forEach { it.release() }

    return blankChannels.size
  }

  private fun onConnectionActive(channel: ChannelState) {
    val items = idleMessages.popupSubscribeMessageItems(config.initSubscribe)

    if (items.isEmpty()) {
      return
    }
    if (!channel.isSubscribeInitialized) {
      val editItems = items.subList(0, Math.min(config.initSubscribe, items.size))
      channel.ref.tell(editItems.autoBatch(), self)
      channel.messages.addMessageItems(editItems.filter { it !is QueueMessage })

      activeMessages.addMessageItems(editItems)
      idleMessages.addMessageItems(
        items
          .toMutableList()
          .apply { removeAll(editItems) }
      )
    } else {
      channel.ref.tell(items.autoBatch(), self)
      channel.messages.addMessageItems(items.filter { it !is QueueMessage })
    }
    channel.notifySubscribeInitialized()
  }

  private fun assignIdleQueueMessageItems(channel: ChannelState) {
    val messages = idleMessages.popupQueueMessageItems()

    if (!messages.isEmpty()) {
      activeMessages.addMessageItems(messages)
      channel.ref.tell(messages.autoBatch(), self)
    }
  }

  private fun onConnectionInactive(channel: ChannelState) {
    val oldMessages = channel.messages.allMessages()
    channel.messages.reset()
    channel.ref.tell(SocketManager.RequestClearSubscribe(), self)
    recycleMessageItems(oldMessages)
  }

  /* -- child connections :end -- */

  /* -- messages :begin -- */

  private fun ChannelState.dispatchIdleMessage(it: SendMessage) {
    if (!isSubscribeInitialized && messages.subscribeSize >= config.initSubscribe) {
      return
    }
    ref.tell(it, self)

    if (it !is QueueMessage) {
      messages.addMessage(it)
    }
    activeMessages.addMessage(it)
    idleMessages.removeMessage(it)
  }

  private fun ChannelState.release(): List<SendMessage> {
    val msgItems = this.messages.allMessages()
    childManagers.remove(ref)
    channelsMap.remove(ref)
    context.stop(ref)

    return msgItems
  }

  /* -- messages :end -- */

  companion object {
    /**
     * Returns pooled socket manager props instance with [requester], [createManager] and pool [config]
     */
    @JvmStatic
    @JvmOverloads
    internal fun props(requester: ActorRef, createManager: (ActorContext, ActorRef, Int) -> ActorRef, config: PooledConfig? = null): Props {
      return Props.create(PooledSocketManager::class.java, requester, createManager, config ?: PooledConfig())
    }

    /**
     * Returns pooled socket manager props instance with [options], [requester], [createClient] and pool [config]
     */
    @JvmStatic
    @JvmOverloads
    fun props(options: ClientOptions, requester: ActorRef, createClient: (() -> SocketClient?)? = null, config: PooledConfig? = null): Props {
      val createManager: (ActorContext, ActorRef, Int) -> ActorRef = { context, ref, id ->
        context.actorOf(SocketManager.props(options, ref, createClient?.invoke()), "child-$id")
      }
      return props(requester, createManager, config)
    }
  }
}