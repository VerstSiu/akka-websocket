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
    manager.tell(SocketState.CONNECTING, m0.ref)
    manager.tell(SocketState.CONNECTED, m0.ref)

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

    manager.tell(
      BatchSendMessage(
        items = listOf(
          AppendMessage("h1", "test"),
          AppendMessage("h2", "test"),
          AppendMessage("h3", "test")
        )
      ),
      ActorRef.noSender()
    )

    val consumedMessages = mutableListOf("h1", "h2", "h3")

    // prepare connect
    m0.expectMsgClass(SocketManager.RequestConnect::class.java)
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m0 connect ready
    manager.tell(SocketState.CONNECTING, m0.ref)
    manager.tell(SocketState.CONNECTED, m0.ref)

    // deploy subscribe message
    m0.expectMsgClass(AppendMessage::class.java).also {
      assert(consumedMessages.contains(it.message))
      consumedMessages.remove(it.message)
    }

    // m1 connect ready
    manager.tell(SocketState.CONNECTING, m1.ref)
    manager.tell(SocketState.CONNECTED, m1.ref)

    // deploy subscribe message
    m1.expectMsgClass(AppendMessage::class.java).also {
      assert(consumedMessages.contains(it.message))
      consumedMessages.remove(it.message)
    }

    // m2 connect ready
    manager.tell(SocketState.CONNECTING, m2.ref)
    manager.tell(SocketState.CONNECTED, m2.ref)

    // deploy subscribe message
    m2.expectMsgClass(AppendMessage::class.java).also {
      assert(consumedMessages.contains(it.message))
      consumedMessages.remove(it.message)
    }
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