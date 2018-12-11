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
import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import com.ijoic.akka.websocket.client.SocketManager
import com.ijoic.akka.websocket.message.AppendMessage
import com.ijoic.akka.websocket.message.BatchSendMessage
import com.ijoic.akka.websocket.message.QueueMessage
import com.ijoic.akka.websocket.message.SubscribeInfo
import com.ijoic.akka.websocket.state.SocketState
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * Pooled socket manager test
 *
 * @author verstsiu created at 2018-12-10 22:06
 */
class PooledSocketManagerTest {
  @Test
  fun testSendMessageSimple() {
    val receiver = TestKit(system)

    val m0 = TestKit(system)
    val m1 = TestKit(system)

    val manager = managerOf(receiver, listOf(m0, m1))

    manager.tell(QueueMessage("Hello world!"), ActorRef.noSender())

    // prepare connect
    m0.expectMsgClass(SocketManager.RequestConnect::class.java)
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m0 connect ready
    manager.notifyConnected(m0)

    // deploy subscribe message
    m0.expectMsgClass(QueueMessage::class.java).also {
      assert(it.message == "Hello world!")
    }
  }

  @Test
  fun testConnectBatchOverflow() {
    val receiver = TestKit(system)

    val m0 = TestKit(system)
    val m1 = TestKit(system)
    val m2 = TestKit(system)

    val manager = managerOf(receiver, listOf(m0, m1, m2), PooledConfig(
      initConnectionSize = 2,
      idleConnectionSize = 0,
      initSubscribe = 1
    ))

    val messages = mutableListOf("h1", "h2", "h3")

    manager.tell(
      messages.toBatchMessage(),
      ActorRef.noSender()
    )

    // prepare connect
    m0.expectMsgClass(SocketManager.RequestConnect::class.java)
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m0 connect ready
    manager.notifyConnected(m0)
    m0.consumeAppendMessage(messages)

    // m1 connect ready
    manager.notifyConnected(m1)
    m1.consumeAppendMessage(messages)

    // m2 connect ready
    manager.notifyConnected(m2)
    m2.consumeAppendMessage(messages)
  }

  @Test
  fun testConnectionAbortSimple() {
    val receiver = TestKit(system)

    val m0 = TestKit(system)
    val m1 = TestKit(system)
    val m2 = TestKit(system)

    val manager = managerOf(receiver, listOf(m0, m1, m2), PooledConfig(
      initConnectionSize = 2,
      idleConnectionSize = 0,
      initSubscribe = 1
    ))

    val messages = mutableListOf("h1", "h2", "h3")

    manager.tell(
      messages.toBatchMessage(),
      ActorRef.noSender()
    )

    // prepare connect
    m0.expectMsgClass(SocketManager.RequestConnect::class.java)
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    var msgRecycle: AppendMessage

    // m0 connect ready
    manager.notifyConnected(m0)
    m0.consumeAppendMessage(messages)

    // m1 connect ready
    manager.notifyConnected(m1)
    m1.consumeAppendMessage(messages)

    // m2 connect ready
    manager.notifyConnected(m2)
    m2.consumeAppendMessage(messages).also { msgRecycle = it }

    // m2 disconnected
    manager.notifyDisconnected(m2)
    m2.expectMsgClass(SocketManager.RequestClearSubscribe::class.java)

    // balance message
    m0.expectMsgClass(AppendMessage::class.java).also { assert(msgRecycle == it) }
  }

  /**
   * Returns pooled socket manager instance with [requester], [probes] and [config]
   */
  private fun managerOf(requester: TestKit, probes: List<TestKit>, config: PooledConfig? = null): ActorRef {
    return system!!.actorOf(
      PooledSocketManager.props(
        requester.ref,
        ListManagerProvider(
          probes.map { it.ref }
        ),
        config
      )
    )
  }

  /**
   * Parse string list to batch subscribe message
   */
  private fun List<String>.toBatchMessage(): BatchSendMessage {
    return BatchSendMessage(
      this.map { AppendMessage(SubscribeInfo(it, "test", "")) }
    )
  }

  private fun ActorRef.notifyConnected(probe: TestKit) {
    this.tell(SocketState.CONNECTING, probe.ref)
    this.tell(SocketState.CONNECTED, probe.ref)
  }

  private fun ActorRef.notifyDisconnected(probe: TestKit) {
    this.tell(SocketState.DISCONNECTED, probe.ref)
  }

  private fun TestKit.consumeAppendMessage(items: MutableList<String>): AppendMessage {
    return this.expectMsgClass(AppendMessage::class.java).also {
      assert(items.contains(it.info.subscribe))
      items.remove(it.info.subscribe)
    }
  }

  /**
   * List manager provider
   */
  private class ListManagerProvider(private val items: List<ActorRef>): (AbstractActor.ActorContext, ActorRef, Int) -> ActorRef {
    override fun invoke(context: AbstractActor.ActorContext, ref: ActorRef, id: Int): ActorRef {
      return items.getOrNull(id) ?: items.last()
    }
  }

  companion object {

    private var system: ActorSystem? = null

    @BeforeClass
    @JvmStatic
    fun setup() {
      system = ActorSystem.create()
    }

    @AfterClass
    @JvmStatic
    fun teardown() {
      system?.terminate()
    }
  }
}