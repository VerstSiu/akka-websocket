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
import com.ijoic.akka.websocket.util.ceilDivided
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

  private val activeMessages = PooledMessageBox()
  private val idleMessages = PooledMessageBox()

  private fun dispatchBatchSendMessage(event: BatchSendMessage) {
    when {
      !isConnectionActive -> {
        idleMessages.addMessageItems(event.items)
      }
      childManagers.isEmpty() -> {
        idleMessages.addMessageItems(event.items)
        prepareChildManagers(
          Math.max(idleMessages.requiredConnectionSize, config.initConnectionSize)
        )
      }
      else -> {
        val activeChannels = genActiveChannels()

        if (activeChannels.isEmpty()) {
          idleMessages.addMessageItems(event.items)
        } else {
          // TODO optimize batch procedure
          event.items.forEach { dispatchSendMessageAlone(it) }
        }
        checkAndRestartChildConnections()
      }
    }
  }

  private fun dispatchSendMessage(msg: SendMessage) {
    when {
      !isConnectionActive -> {
        idleMessages.addMessage(msg)
      }
      childManagers.isEmpty() -> {
        idleMessages.addMessage(msg)
        prepareChildManagers(config.initConnectionSize)
      }
      else -> {
        dispatchSendMessageAlone(msg)
        checkAndRestartChildConnections()
      }
    }
  }

  private fun dispatchSendMessageAlone(msg: SendMessage) {
    when(msg) {
      is AppendMessage -> if (!activeMessages.containsMessage(msg) && !idleMessages.containsMessage(msg)) {
        dispatchAddSubscribeMessage(msg)
      }
      is ReplaceMessage -> if (!activeMessages.containsMessage(msg) && !idleMessages.containsMessage(msg)) {
        dispatchAddSubscribeMessage(msg)
      }
      is ClearAppendMessage -> {
        if (activeMessages.containsReverseMessage(msg)) {
          dispatchRemoveSubscribeMessage(msg)

        } else if (idleMessages.containsReverseMessage(msg)) {
          idleMessages.addMessage(msg)
        }
      }
      is ClearReplaceMessage -> {
        if (activeMessages.containsReverseMessage(msg)) {
          dispatchRemoveSubscribeMessage(msg)

        } else if (idleMessages.containsReverseMessage(msg)) {
          idleMessages.addMessage(msg)
        }
      }
      is QueueMessage -> dispatchQueueMessage(msg)
    }
  }

  private fun dispatchAddSubscribeMessage(msg: SendMessage) {
    val activeChannels = genActiveChannels()

    if (activeChannels.isEmpty()) {
      idleMessages.addMessage(msg)
    } else {
      activeMessages.addMessage(msg)
      val channel = activeChannels.minBy { it.messages.subscribeSize }
      channel?.apply {
        ref.tell(msg, self)
        messages.addMessage(msg)
      }
    }
  }

  private fun dispatchRemoveSubscribeMessage(msg: ClearAppendMessage) {
    val activeChannels = genActiveChannels()

    val channel = activeChannels.firstOrNull { it.messages.containsReverseMessage(msg) }
    channel?.apply {
      ref.tell(msg, self)
      messages.addMessage(msg)
    }
    activeMessages.addMessage(msg)
  }

  private fun dispatchRemoveSubscribeMessage(msg: ClearReplaceMessage) {
    val activeChannels = genActiveChannels()

    val channel = activeChannels.firstOrNull { it.messages.containsReverseMessage(msg) }
    channel?.apply {
      ref.tell(msg, self)
      messages.addMessage(msg)
    }
    activeMessages.addMessage(msg)
  }

  private fun dispatchQueueMessage(msg: QueueMessage) {
    val activeChannels = genActiveChannels()

    if (activeChannels.isEmpty()) {
      idleMessages.addMessage(msg)
    } else {
      val channel = activeChannels.minBy { it.messages.subscribeSize }
      channel?.ref?.tell(msg, self)
    }
  }

  private fun checkAndRestartChildConnections() {
    for ((child, channel) in channelsMap) {
      if (channel.state == SocketState.DISCONNECTED) {
        child.tell(SocketManager.RequestConnect(), self)
      }
    }
  }

  private fun onRequestConnect() {
    childManagers.forEach { it.tell(SocketManager.RequestConnect(), self) }
    isConnectionActive = true

    val prepareSize = Math.max(
      idleMessages.requiredConnectionSize,
      config.initConnectionSize - childManagers.size
    )
    prepareChildManagers(prepareSize)
  }

  private fun onRequestDisconnect() {
    childManagers.forEach { it.tell(SocketManager.RequestDisconnect(), self) }
    isConnectionActive = false

    // recycle subscribe messages
    for ((child, channel) in channelsMap) {
      val messages = channel.messages

      if (messages.subscribeSize > 0) {
        channel.messages.reset()
        child.tell(SocketManager.RequestClearSubscribe(), self)
      }
    }

    idleMessages.addMessageItems(
      activeMessages.allSubscribeMessages()
    )
    activeMessages.reset()
  }

  private fun onChildTerminated(child: ActorRef) {
    if (!childManagers.contains(child)) {
      return
    }
    context.unwatch(child)
    childManagers.remove(child)

    val messages = channelsMap[child]?.messages
    channelsMap.remove(child)

    if (messages != null) {
      recycleChannelMessages(messages)
    }
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
      val messages = channel.messages

      if (messages.subscribeSize > 0) {
        val subscribeMessages = messages.allSubscribeMessages()
        messages.reset()

        child.tell(SocketManager.RequestClearSubscribe(), self)
        activeMessages.removeMessageItems(subscribeMessages)
        idleMessages.addMessageItems(subscribeMessages)
      }
      return
    }
    if (oldState == state) {
      return
    }
    if (oldState == SocketState.CONNECTED) {
      recycleChannelMessages(channel.messages)
      child.tell(SocketManager.RequestClearSubscribe(), self)
    } else if (state == SocketState.CONNECTED) {
      balanceChannelMessages(child)
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

  private fun prepareChildManagers(childSize: Int) {
    if (childSize <= 0) {
      return
    }
    repeat(childSize + config.idleConnectionSize) {
      val child = createManager(context, self, genChildIndex)
      ++genChildIndex

      child.tell(SocketManager.RequestConnect(), self)
      println("request connect: child - ${child.path()}")
      context.watch(child)
      childManagers.add(child)
    }
  }

  private fun recycleChannelMessages(messages: PooledMessageBox) {
    if (messages.subscribeSize > 0) {
      val subscribeMessages = messages.allSubscribeMessages()
      messages.reset()

      if (!subscribeMessages.isEmpty()) {
        activeMessages.removeMessageItems(subscribeMessages)
        val activeChannels = genActiveChannels()

        if (activeChannels.isEmpty()) {
          idleMessages.addMessageItems(subscribeMessages)
        } else {
          balanceAddSubscribeMessages(activeChannels, subscribeMessages)
        }
      }
    }
  }

  private fun balanceChannelMessages(child: ActorRef) {
    val channel = channelsMap[child] ?: return

    if (!channel.isSubscribeInitialized) {
      channel.notifySubscribeInitialized()
      val subscribeMessages = idleMessages.popSubscribeMessages(config.initSubscribe)
      val queueMessages = idleMessages.allQueueMessages()
      idleMessages.clearQueueMessages()

      // subscribe messages
      if (!subscribeMessages.isEmpty()) {
        child.tell(subscribeMessages.autoBatch(), self)
        activeMessages.addMessageItems(subscribeMessages)
        channel.messages.addMessageItems(subscribeMessages)
      }

      // queue messages
      if (!queueMessages.isEmpty()) {
        child.tell(queueMessages.autoBatch(), self)
      }
    } else {
      balanceIdleMessages(child)
    }
  }

  private fun balanceIdleMessages(child: ActorRef) {
    // TODO adjust balance strategy
    val activeChannels = genActiveChannels()
    val channelSize = activeChannels.size

    if (channelSize <= 0) {
      return
    }
    val subscribeMessages = idleMessages.allSubscribeMessages()
    val queueMessages = idleMessages.allQueueMessages()
    idleMessages.reset()

    // subscribe messages
    if (!subscribeMessages.isEmpty()) {
      balanceAddSubscribeMessages(activeChannels, subscribeMessages)
    }

    // queue messages
    if (!queueMessages.isEmpty()) {
      child.tell(queueMessages.autoBatch(), self)
    }
  }

  private fun balanceAddSubscribeMessages(channels: List<ChannelState>, messages: List<SendMessage>) {
    val msgSize = messages.size
    val oldMsgSize = activeMessages.subscribeSize
    val newMsgSize = oldMsgSize + msgSize
    val averageSize = newMsgSize.ceilDivided(channels.size)

    var start = 0
    var editMsgSize: Int
    var editMessages: List<SendMessage>

    channels.forEach { channel ->
      editMsgSize = averageSize - channel.messages.subscribeSize
      editMsgSize = Math.min(msgSize - start, editMsgSize)
      editMsgSize = Math.max(editMsgSize, 0)

      if (editMsgSize > 0) {
        editMessages = messages.subList(start, start + editMsgSize)
        channel.messages.addMessageItems(editMessages)
        channel.ref.tell(editMessages.autoBatch(), self)
        start += editMsgSize
      }
    }
    activeMessages.addMessageItems(messages)
  }

  /**
   * Generate active channels
   */
  private fun genActiveChannels(): List<ChannelState> {
    return channelsMap
      .filter { (_, stat) -> stat.state == SocketState.CONNECTED }
      .map { (_, stat) -> stat }
  }

  /**
   * Required connection size
   */
  private val PooledMessageBox.requiredConnectionSize: Int
    get() = subscribeSize.ceilDivided(config.initSubscribe)

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