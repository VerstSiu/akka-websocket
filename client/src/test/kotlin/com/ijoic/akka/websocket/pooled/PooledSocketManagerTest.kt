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

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import com.ijoic.akka.websocket.client.SocketManager
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

    val manager = system!!.actorOf(PooledSocketManager.props(receiver.ref, { _, _, id ->
      if (id == 0) {
        m0.ref
      } else {
        m1.ref
      }
    }))

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