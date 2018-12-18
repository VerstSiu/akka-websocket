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
  private val idleMessages = PooledMessageCache()

  private fun dispatchBatchSendMessage(event: BatchSendMessage) {
    val items = event.items

    if (items.isEmpty()) {
      return
    }
    if (items.size == 1) {
      dispatchSendMessage(items.first())
    } else {
      when {
        !isConnectionActive -> {
          allMessages.addMessageItems(items)
          idleMessages.addMessageItems(items)
        }
        childManagers.isEmpty() -> {
          allMessages.addMessageItems(items)
          idleMessages.addMessageItems(items)

          if (!idleMessages.isEmpty) {
            prepareChildConnections(
              Math.max(idleMessages.requiredConnectionSize(config.initSubscribe), config.initConnectionSize)
            )
          }
        }
        else -> {
          val activeChannels = channelsMap
            .filter { (_, stat) -> stat.state == SocketState.CONNECTED }
            .map { (_, stat) -> stat }

          if (!activeChannels.isEmpty()) {
            allMessages.addMessageItems(items)
            val averageSubscribeSize = allMessages.measureAverageSubscribeSize(config.maxSubscribe, activeChannels.size)
            var assignIndex = 0
            var assignItems: List<SendMessage>

            for (channel in activeChannels) {
              val assignSize = Math.max(
                Math.min(averageSubscribeSize - channel.messages.subscribeSize, items.size - assignIndex),
                0
              )

              if (assignSize > 0) {
                assignItems = items.subList(assignIndex, assignIndex + assignSize)

                channel.ref.tell(assignItems.autoBatch(), self)
                channel.messages.addMessageItems(assignItems)
                assignIndex += assignSize
              }

              if (assignIndex >= items.size) {
                break
              }
            }
            if (assignIndex < items.size) {
              idleMessages.addMessageItems(items.subList(assignIndex, items.size))
            }

          } else {
            allMessages.addMessageItems(items)
            idleMessages.addMessageItems(items)

            if (!idleMessages.isEmpty) {
              prepareChildConnections(
                idleMessages.requiredConnectionSize(config.initSubscribe) - childManagers.size
              )
            }
          }
        }
      }
    }
  }

  private fun dispatchSendMessage(msg: SendMessage) {
    when {
      !isConnectionActive -> {
        allMessages.addMessage(msg)
        idleMessages.addMessage(msg)
      }
      childManagers.isEmpty() -> {
        allMessages.addMessage(msg)
        idleMessages.addMessage(msg)

        if (!idleMessages.isEmpty) {
          prepareChildConnections(config.initConnectionSize)
        }
      }
      else -> {
        val activeChannels = channelsMap
          .filter { (_, stat) -> stat.state == SocketState.CONNECTED }
          .map { (_, stat) -> stat }

        if (!activeChannels.isEmpty()) {
          allMessages.addMessage(msg)
          val channel = activeChannels[0]

          channel.dispatchIdleMessage(msg)
        } else {
          allMessages.addMessage(msg)
          idleMessages.addMessage(msg)

          if (!idleMessages.isEmpty) {
            prepareChildConnections(
              idleMessages.requiredConnectionSize(config.initSubscribe) - childManagers.size
            )
          }
        }
      }
    }
  }

  private fun onRequestConnect() {
    if (childManagers.isEmpty()) {
      prepareChildConnections(config.initConnectionSize)

    } else {
      for ((child, channel) in channelsMap) {
        when(channel.state) {
          SocketState.DISCONNECTED,
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
      if (channel.state != SocketState.DISCONNECTED) {
        child.tell(SocketManager.RequestDisconnect(), self)
      }
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
        dispatchBatchSendMessage(
          BatchSendMessage(items)
        )
      }
      prepareChildConnections(1)
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
    dispatchBatchSendMessage(
      BatchSendMessage(items)
    )
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
      dispatchBatchSendMessage(
        BatchSendMessage(balanceItems)
      )
    }
  }

  /* -- child connections :begin -- */

  private fun prepareChildConnections(childSize: Int) {
    if (childSize <= 0) {
      return
    }
    repeat(childSize + config.idleConnectionSize) {
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

  private fun onConnectionActive(channel: ChannelState) {
    val items = idleMessages.popupSubscribeMessageItems(config.initSubscribe)

    if (items.isEmpty()) {
      return
    }
    if (!channel.isSubscribeInitialized) {
      val editItems = items.subList(0, Math.min(config.initSubscribe, items.size))
      channel.ref.tell(editItems.autoBatch(), self)
      channel.messages.addMessageItems(editItems.filter { it !is QueueMessage })

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
    val messages = idleMessages.popuoQueueMessageItems()

    if (!messages.isEmpty()) {
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
    idleMessages.removeMessage(it)
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