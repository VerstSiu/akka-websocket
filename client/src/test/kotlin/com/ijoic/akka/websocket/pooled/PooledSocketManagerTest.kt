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
import com.ijoic.akka.websocket.message.*
import com.ijoic.akka.websocket.pooled.proxy.ProxyConfig
import com.ijoic.akka.websocket.state.SocketState
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * Pooled socket manager test
 *
 * Test cases:
 *
 * Single {
 *   Append
 *   ClearAppend
 *   Replace
 *   ClearReplace
 *   Strict
 *   ClearStrict
 *   Queue
 * }
 *
 * Batch {
 *   Append
 *   ClearAppend
 *   Replace
 *   ClearReplace
 *   Strict
 *   ClearStrict
 *   Queue
 * }
 *
 * SocketState {
 *   DISCONNECTED
 *   CONNECTING
 *   CONNECTED
 *   DISCONNECTING
 *   RETRY_CONNECTING
 * }
 *
 * RequestConnect
 * RequestDisconnect
 * Terminated
 *
 * Scenarios:
 *
 * Send (prepare connection) > Connected (dispatch inactive messages) > Not Connected (recycle active messages)
 *
 * RequestConnect (prepare connection) > Connected (wait messages) > Send (dispatch messages) > Not Connected (recycle messages)
 *
 * @author verstsiu created at 2018-12-10 22:06
 */
class PooledSocketManagerTest {

  @Test
  fun testPrepareConnectionSend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testPrepareConnectionExtendAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 1
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testPrepareConnectionExtendStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict(),
      "h3".toStrict()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testPrepareConnectionReduceStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()
    val m5 = probeOf()

    receiver.watch(m1.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4, m5), PooledConfig(
      initSubscribe = 2,
      maxIdle = 2
    ))
    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict(),
      "h3".toStrict(),
      "h4".toStrict(),
      "h5".toStrict()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)
    m5.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.tellBatchMessage(
      "h3".toClearStrict(),
      "h4".toClearStrict(),
      "h5".toClearStrict()
    )

    receiver.expectTerminated(m1.ref)
    m2.expectNoMessage()
    m3.expectNoMessage()
    m4.expectNoMessage()
    m5.expectNoMessage()
  }

  @Test
  fun testPrepareConnectionRequestConnect() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testSendClearAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend()
    )
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h2")

    manager.tellMessage("h2".toClearAppend())
    m2.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h2")
  }

  @Test
  fun testSendClearAppendBatch() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend()
    )
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h2")

    manager.tellBatchMessage(
      "h2".toClearAppend(),
      "h3".toClearAppend()
    )
    m2.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h2")
  }

  @Test
  fun testSendClearReplace() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toReplace(),
      "h2".toReplace()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkMessage("h2")

    manager.tellMessage("h2".toClearReplace())
    m1.expectMsgClass(ClearReplaceMessage::class.java).checkMessage("h2")
  }

  @Test
  fun testSendClearReplaceBatch() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toReplace(),
      "h2".toReplace()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkMessage("h2")

    manager.tellBatchMessage(
      "h3".toReplace(),
      "h2".toClearReplace()
    )
    m1.expectMsgClass(ClearReplaceMessage::class.java).checkMessage("h2")
  }

  @Test
  fun testSendClearStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")

    manager.tellMessage("h2".toClearStrict())
    m2.expectMsgClass(ClearReplaceMessage::class.java).checkStrict("h2")
  }

  @Test
  fun testSendClearStrictBatch() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")

    manager.tellBatchMessage(
      "h2".toClearStrict(),
      "h3".toClearStrict()
    )
    m2.expectMsgClass(ClearReplaceMessage::class.java).checkStrict("h2")
  }

  @Test
  fun testSendQueueSingle() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toQueue())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectMsgClass(QueueMessage::class.java).checkQueue("h1")

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellMessage("h2".toQueue())
    m1.expectMsgClass(QueueMessage::class.java).checkQueue("h2")
  }

  @Test
  fun testSendQueueBatch() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellBatchMessage(
      "h1".toQueue(),
      "h2".toQueue()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchQueue("h1", "h2")

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h3".toQueue(),
      "h4".toQueue()
    )
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchQueue("h3", "h4")
  }

  @Test
  fun testSendQueueExecuteAtMinSubscribe() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toQueue(),
      "h3".toQueue()
    )
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
    m2.expectMsgClass(BatchSendMessage::class.java).checkBatchQueue("h2", "h3")

    manager.tellMessage("h2".toQueue())
    m2.expectMsgClass(QueueMessage::class.java).checkQueue("h2")
  }

  @Test
  fun testSendUpgradeReplace() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toReplace(),
      "h2".toReplace()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkMessage("h2")

    manager.tellMessage("h3".toReplace())
    m1.expectMsgClass(ReplaceMessage::class.java).checkMessage("h3")
  }

  @Test
  fun testSendAppendExtendConnection() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend()
    )
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h2")
    m3.expectNoMessage()

    manager.tellMessage("h3".toAppend())
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h3")
    m2.expectNoMessage()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m3)
    m1.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h1")
    m2.expectNoMessage()
    m3.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
  }

  @Test
  fun testSendStrictExtendConnection() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")
    m3.expectNoMessage()

    manager.tellMessage("h3".toStrict())
    m1.expectNoMessage()
    m2.expectNoMessage()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m3)
    m1.expectNoMessage()
    m2.expectNoMessage()
    m3.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")
  }

  @Test
  fun testSendAppendReduceConnection() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()
    val m5 = probeOf()

    receiver.watch(m2.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4, m5), PooledConfig(
      initSubscribe = 1,
      maxSubscribe = 2,
      maxIdle = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend(),
      "h4".toAppend(),
      "h5".toAppend()
    )
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")
    m2.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h3", "h4")
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)
    m5.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m3)
    m3.expectMsgClass(AppendMessage::class.java).checkMessage("h5")

    manager.notifyConnected(m4)
    m4.expectNoMessage()

    manager.notifyConnected(m5)
    m1.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h1")
    m2.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h3")
    m4.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
    m5.expectMsgClass(AppendMessage::class.java).checkMessage("h3")

    manager.tellBatchMessage(
      "h3".toClearAppend(),
      "h4".toClearAppend(),
      "h5".toClearAppend()
    )
    m2.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h4")
    m3.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h5")
    m5.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h3")

    receiver.expectTerminated(m2.ref)
  }

  @Test
  fun testSendStrictReduceConnection() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()
    val m5 = probeOf()

    receiver.watch(m3.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4, m5), PooledConfig(
      initSubscribe = 2,
      maxIdle = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict(),
      "h3".toStrict(),
      "h4".toStrict(),
      "h5".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)
    m5.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m3)
    m3.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")

    manager.notifyConnected(m4)
    m4.expectMsgClass(ReplaceMessage::class.java).checkStrict("h4")

    manager.notifyConnected(m5)
    m5.expectMsgClass(ReplaceMessage::class.java).checkStrict("h5")

    manager.tellBatchMessage(
      "h3".toClearStrict(),
      "h4".toClearStrict(),
      "h5".toClearStrict()
    )
    m3.expectMsgClass(ClearReplaceMessage::class.java).checkStrict("h3")
    m4.expectMsgClass(ClearReplaceMessage::class.java).checkStrict("h4")
    m5.expectMsgClass(ClearReplaceMessage::class.java).checkStrict("h5")

    receiver.expectTerminated(m3.ref)
  }

  @Test
  fun testInactiveBalanceAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initConnectionSize = 3,
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend(),
      "h4".toAppend(),
      "h5".toAppend(),
      "h6".toAppend()
    )
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")
    m2.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h3", "h4")
    m3.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h5", "h6")

    manager.tellState(SocketState.DISCONNECTING, m3)
    m3.expectMsgClass(SocketManager.RequestClearSubscribe::class.java)
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h5")
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h6")

    manager.notifyConnected(m3)
    m1.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h1")
    m2.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h3")
    m3.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h3")
  }

  @Test
  fun testInactiveBalanceAppendExistIdleConnection() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4), PooledConfig(
      initConnectionSize = 2,
      initSubscribe = 2,
      minIdle = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend(),
      "h4".toAppend(),
      "h5".toAppend(),
      "h6".toAppend()
    )
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")
    m2.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h3", "h4")
    m3.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h5", "h6")
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m4)
    m4.expectNoMessage()

    manager.tellState(SocketState.DISCONNECTING, m3)
    m3.expectMsgClass(SocketManager.RequestClearSubscribe::class.java)
    m4.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h5", "h6")

    manager.notifyConnected(m3)
    m3.expectNoMessage()
  }

  @Test
  fun testInactiveBalanceStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initConnectionSize = 3,
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict(),
      "h3".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")
    m3.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")

    manager.tellState(SocketState.DISCONNECTING, m3)
    m3.expectMsgClass(SocketManager.RequestClearSubscribe::class.java)
    m1.expectNoMessage()
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")
  }

  @Test
  fun testInactiveBalanceStrictExistIdleConnection() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4), PooledConfig(
      initConnectionSize = 2,
      initSubscribe = 2,
      minIdle = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict(),
      "h3".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")
    m3.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m4)
    m4.expectNoMessage()

    manager.tellState(SocketState.DISCONNECTING, m3)
    m3.expectMsgClass(SocketManager.RequestClearSubscribe::class.java)
    m4.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")

    manager.notifyConnected(m3)
    m3.expectNoMessage()
  }

  @Test
  fun testTerminatedBalanceAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()

    receiver.watch(m3.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4), PooledConfig(
      initConnectionSize = 3,
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend(),
      "h4".toAppend(),
      "h5".toAppend(),
      "h6".toAppend()
    )
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")
    m2.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h3", "h4")
    m3.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h5", "h6")
    m4.expectNoMessage()

    m3.requestTerminated()
    receiver.expectTerminated(m3.ref)
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h5")
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h6")
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m4)
    m1.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h1")
    m2.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h3")
    m4.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h3")
  }

  @Test
  fun testTerminatedBalanceAppendExistIdleConnection() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()
    val m5 = probeOf()

    receiver.watch(m3.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4, m5), PooledConfig(
      initConnectionSize = 2,
      initSubscribe = 2,
      minIdle = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectNoMessage()
    m5.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend(),
      "h4".toAppend(),
      "h5".toAppend(),
      "h6".toAppend()
    )
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")
    m2.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h3", "h4")
    m3.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h5", "h6")
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)
    m5.expectNoMessage()

    manager.notifyConnected(m4)
    m4.expectNoMessage()

    m3.requestTerminated()
    receiver.expectTerminated(m3.ref)
    m4.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h5", "h6")
    m5.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m5)
    m5.expectNoMessage()
  }

  @Test
  fun testTerminatedBalanceStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()

    receiver.watch(m3.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4), PooledConfig(
      initConnectionSize = 3,
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict(),
      "h3".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")
    m3.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")
    m4.expectNoMessage()

    m3.requestTerminated()
    receiver.expectTerminated(m3.ref)
    m1.expectNoMessage()
    m2.expectNoMessage()
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m4)
    m4.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")
  }

  @Test
  fun testTerminatedBalanceStrictExistIdleConnection() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()
    val m5 = probeOf()

    receiver.watch(m3.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4, m5), PooledConfig(
      initConnectionSize = 2,
      initSubscribe = 2,
      minIdle = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectNoMessage()
    m5.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict(),
      "h3".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")
    m3.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)
    m5.expectNoMessage()

    manager.notifyConnected(m4)
    m4.expectNoMessage()

    m3.requestTerminated()
    receiver.expectTerminated(m3.ref)
    m4.expectMsgClass(ReplaceMessage::class.java).checkStrict("h3")
    m5.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m5)
    m5.expectNoMessage()
  }

  @Test
  fun testDisconnectedBalanceAppendResume() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()
    val m5 = probeOf()
    val m6 = probeOf()

    receiver.watch(m1.ref)
    receiver.watch(m2.ref)
    receiver.watch(m3.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4, m5, m6), PooledConfig(
      initConnectionSize = 2,
      initSubscribe = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend()
    )
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h3")
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m3)
    m1.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h1")
    m2.expectNoMessage()
    m3.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    manager.requestDisconnect()
    receiver.expectTerminated(m1.ref)
    receiver.expectTerminated(m2.ref)
    receiver.expectTerminated(m3.ref)

    manager.requestConnect()
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)
    m5.expectMsgClass(SocketManager.RequestConnect::class.java)
    m6.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m4)
    m4.expectMsgClass(AppendMessage::class.java).checkMessage("h2")

    manager.notifyConnected(m5)
    m5.expectMsgClass(AppendMessage::class.java).checkMessage("h3")

    manager.notifyConnected(m6)
    m6.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
  }

  @Test
  fun testDisconnectedBalanceAppendExistIdleConnectionResume() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()
    val m5 = probeOf()
    val m6 = probeOf()
    val m7 = probeOf()
    val m8 = probeOf()

    receiver.watch(m1.ref)
    receiver.watch(m2.ref)
    receiver.watch(m3.ref)
    receiver.watch(m4.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4, m5, m6, m7, m8), PooledConfig(
      initConnectionSize = 2,
      initSubscribe = 1,
      minIdle = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectNoMessage()

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend()
    )
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h2")
    m3.expectMsgClass(AppendMessage::class.java).checkMessage("h3")
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m4)
    m4.expectNoMessage()

    manager.requestDisconnect()
    receiver.expectTerminated(m1.ref)
    receiver.expectTerminated(m2.ref)
    receiver.expectTerminated(m3.ref)
    receiver.expectTerminated(m4.ref)

    manager.requestConnect()
    m5.expectMsgClass(SocketManager.RequestConnect::class.java)
    m6.expectMsgClass(SocketManager.RequestConnect::class.java)
    m7.expectMsgClass(SocketManager.RequestConnect::class.java)
    m8.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m5)
    m5.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    manager.notifyConnected(m6)
    m6.expectMsgClass(AppendMessage::class.java).checkMessage("h2")

    manager.notifyConnected(m7)
    m7.expectMsgClass(AppendMessage::class.java).checkMessage("h3")

    manager.notifyConnected(m8)
    m8.expectNoMessage()
  }

  @Test
  fun testDisconnectedBalanceStrictResume() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()

    receiver.watch(m1.ref)
    receiver.watch(m2.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4), PooledConfig(
      initConnectionSize = 2,
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")

    manager.requestDisconnect()
    receiver.expectTerminated(m1.ref)
    receiver.expectTerminated(m2.ref)

    manager.requestConnect()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m3)
    m3.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")

    manager.notifyConnected(m4)
    m4.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")
  }

  @Test
  fun testDisconnectedBalanceStrictExistIdleConnection() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()
    val m5 = probeOf()
    val m6 = probeOf()

    receiver.watch(m1.ref)
    receiver.watch(m2.ref)
    receiver.watch(m3.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4, m5, m6), PooledConfig(
      initConnectionSize = 2,
      initSubscribe = 2,
      minIdle = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict()
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")
    m3.expectNoMessage()

    manager.requestDisconnect()
    receiver.expectTerminated(m1.ref)
    receiver.expectTerminated(m2.ref)
    receiver.expectTerminated(m3.ref)

    manager.requestConnect()
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)
    m5.expectMsgClass(SocketManager.RequestConnect::class.java)
    m6.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m4)
    m4.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")

    manager.notifyConnected(m5)
    m5.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")

    manager.notifyConnected(m6)
    m6.expectNoMessage()
  }

  @Test
  fun testAndAndRemoveStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initConnectionSize = 2,
      initSubscribe = 2,
      minIdle = 1,
      maxIdle = 1
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.notifyConnected(m3)
    m3.expectNoMessage()

    val subscribeId = System.currentTimeMillis()
    val sourceH1: () -> String = { "h1-$subscribeId" }
    val sourceH2: () -> String = { "h2-$subscribeId" }
    val sourceRem: () -> String = { "rem-$subscribeId" }
    val msgStrict: (() -> String) -> SendMessage = { it().toStrict(unsubscribe = sourceRem()) }
    val msgClearStrict: (() -> String) -> SendMessage = { it().toClearStrict(unsubscribe = sourceRem()) }

    manager.tellBatchMessage(
      msgStrict(sourceH1),
      msgStrict(sourceH2)
    )
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict(sourceH1())
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict(sourceH2())

    manager.tellMessage(msgClearStrict(sourceH1))
    m1.expectMsgClass(ClearReplaceMessage::class.java).checkStrict(sourceH1())

    manager.tellMessage(msgClearStrict(sourceH2))
    m2.expectMsgClass(ClearReplaceMessage::class.java).checkStrict(sourceH2())
  }

  /* -- single step test cases :begin -- */

  @Test
  fun testS1SingleClearAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toClearAppend())

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1SingleReplace() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toReplace())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1SingleClearReplace() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toClearReplace())

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1SingleStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toStrict())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1SingleClearStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toClearStrict())

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1SingleQueue() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toQueue())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1BatchAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1BatchStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage("h1".toStrict())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1BatchPairAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1BatchPairAppendCancel() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h1".toClearAppend()
    )

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1BatchPairReplace() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage(
      "h1".toReplace(),
      "h2".toReplace()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1BatchPairReplaceCancel() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage(
      "h1".toReplace(),
      "h1".toClearReplace()
    )

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1BatchPairStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1BatchPairStrictCancel() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage(
      "h1".toStrict(),
      "h1".toClearStrict()
    )

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1BatchPairQueue() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage(
      "h1".toQueue(),
      "h2".toQueue()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1BatchMix() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 4
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toReplace(),
      "h4".toClearReplace(),
      "h5".toStrict(),
      "h6".toClearStrict(),
      "h7".toQueue(),
      "h8".toQueue()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS1BatchMixCancel() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 4
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h1".toClearAppend(),
      "h2".toReplace(),
      "h2".toClearReplace(),
      "h3".toStrict(),
      "h3".toClearStrict()
    )

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1StateDisconnected() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellState(SocketState.DISCONNECTED, m1)

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1StateConnecting() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellState(SocketState.CONNECTING, m1)

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1StateConnected() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellState(SocketState.CONNECTED, m1)

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1StateDisconnecting() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellState(SocketState.DISCONNECTING, m1)

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1StateRetryConnecting() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellState(SocketState.RETRY_CONNECTING, m1)

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1Disconnect() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.requestDisconnect()

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS1Terminated() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    managerOf(receiver, m1, m2)
    m1.requestTerminated()

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  /* -- single step test cases :end -- */

  /* -- couple step test cases :begin -- */

  @Test
  fun testS2SingleBatchAppendConnectionExtend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()

    manager.tellBatchMessage(
      "h2".toAppend(),
      "h3".toAppend(),
      "h4".toAppend(),
      "h5".toAppend()
    )

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS2SingleBatchAppendStrictConnectionExtend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()
    m4.expectNoMessage()

    manager.tellBatchMessage(
      "h2".toStrict(),
      "h3".toStrict(),
      "h4".toStrict()
    )

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectNoMessage()
  }

  @Test
  fun testS2SingleBatchStrictConnectionExtend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toStrict())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()
    m4.expectNoMessage()

    manager.tellBatchMessage(
      "h2".toStrict(),
      "h3".toStrict(),
      "h4".toStrict()
    )

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS2SingleBatchStrictMixGroupConnectionExtend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toStrict())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()
    m4.expectNoMessage()

    manager.tellBatchMessage(
      "h2".toStrict(),
      "h3".toStrict(group = "test2"),
      "h4".toStrict()
    )

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectNoMessage()
  }

  @Test
  fun testS2SingleStateAppendDisconnected() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.tellState(SocketState.DISCONNECTED, m1)

    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectNoMessage()
  }

  @Test
  fun testS2SingleStateAppendConnecting() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.tellState(SocketState.CONNECTING, m1)

    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS2SingleStateAppendConnected() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.tellState(SocketState.CONNECTED, m1)

    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
    m2.expectNoMessage()
  }

  @Test
  fun testS2SingleConnectAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.requestConnect()

    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS2SingleDisconnectAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    receiver.watch(m1.ref)
    receiver.watch(m2.ref)

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.requestDisconnect()
    receiver.expectTerminated(m1.ref)
    receiver.expectTerminated(m2.ref)
  }

  @Test
  fun testS2SingleTerminatedAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, m1, m2, m3)
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()

    m1.requestTerminated()

    m1.expectNoMessage()
    m2.expectNoMessage()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS2BatchSingleAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.tellMessage("h2".toAppend())

    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testS2BatchSingleAppendConnectionExtend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend(),
      "h4".toAppend()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()

    manager.tellMessage("h5".toAppend())

    m1.expectNoMessage()
    m2.expectNoMessage()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  @Test
  fun testS2BatchSingleStrictConnectionExtend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellBatchMessage(
      "h1".toStrict(),
      "h2".toStrict()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectNoMessage()

    manager.tellMessage("h3".toStrict())

    m1.expectNoMessage()
    m2.expectNoMessage()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
  }

  /* -- couple step test cases :end -- */

  @Test
  fun testSingleAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSingleReplace() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toReplace())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(ReplaceMessage::class.java).checkMessage("h1")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSingleStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toStrict())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSingleQueue() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toQueue())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(QueueMessage::class.java).checkQueue("h1")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSinglePairAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toAppend())
    manager.tellMessage("h2".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSinglePairAppendCancel() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toAppend())
    manager.tellMessage("h1".toClearAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectNoMessage()

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSinglePairAppendReplace() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toAppend())
    manager.tellMessage("h2".toReplace())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSinglePairAppendForceBalance() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 1
    ))
    manager.tellMessage("h1".toAppend())
    manager.tellMessage("h2".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h2")
  }

  @Test
  fun testSinglePairReplace() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toReplace())
    manager.tellMessage("h2".toReplace())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(ReplaceMessage::class.java).checkMessage("h2")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSinglePairReplaceCancel() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toReplace())
    manager.tellMessage("h1".toClearReplace())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectNoMessage()

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSinglePairStrict() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toStrict())
    manager.tellMessage("h2".toStrict())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(ReplaceMessage::class.java).checkStrict("h1")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectMsgClass(ReplaceMessage::class.java).checkStrict("h2")
  }

  @Test
  fun testSinglePairStrictCancel() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toStrict())
    manager.tellMessage("h1".toClearStrict())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectNoMessage()

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testSinglePairQueue() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellMessage("h1".toQueue())
    manager.tellMessage("h2".toQueue())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchQueue("h1", "h2")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testBatchAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testBatchPairAppend() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectNoMessage()
  }

  @Test
  fun testBatchPairAppendCancel() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h1".toClearAppend()
    )

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testConnect() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    receiver.watch(m1.ref)
    receiver.watch(m2.ref)

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 2
    ))
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)
    m1.expectNoMessage()

    // m2 connect ready
    manager.notifyConnected(m2)
    m2.expectNoMessage()

    // disconnect
    manager.requestDisconnect()
    receiver.expectTerminated(m1.ref)
    receiver.expectTerminated(m2.ref)
  }

  @Test
  fun testTerminated() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 1
    ))
    manager.tellMessage("h1".toAppend())
    manager.tellMessage("h2".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)

    // deploy subscribe message
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    // m2 connect ready
    manager.notifyConnected(m2)

    // deploy subscribe message
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h2")

    // m1 terminated
    m1.requestTerminated()

    m1.expectNoMessage()
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h1")
  }

  @Test
  fun testBatchConnectionOverflow() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 1
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    // m2 connect ready
    manager.notifyConnected(m2)
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h2")

    // m3 connect ready
    manager.notifyConnected(m3)
    m3.expectMsgClass(AppendMessage::class.java).checkMessage("h3")
  }

  @Test
  fun testBatchConnectionAbort() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 1
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    // m2 connect ready
    manager.notifyConnected(m2)
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h2")

    // m3 connect ready
    manager.notifyConnected(m3)
    m3.expectMsgClass(AppendMessage::class.java).checkMessage("h3")

    // m2 disconnected
    manager.tellState(SocketState.DISCONNECTED, m2)
    m2.expectMsgClass(SocketManager.RequestClearSubscribe::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // balance message
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h2")
  }

  @Test
  fun testBatchConnectionAbortResume() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2, m3), PooledConfig(
      initSubscribe = 2,
      maxSubscribe = 4
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend(),
      "h3".toAppend(),
      "h4".toAppend(),
      "h5".toAppend(),
      "h6".toAppend()
    )

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)

    // m1 connect ready
    manager.notifyConnected(m1)
    m1.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h2")

    // m2 connect ready
    manager.notifyConnected(m2)
    m2.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h3", "h4")

    // m3 connect ready
    manager.notifyConnected(m3)
    m3.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h5", "h6")

    // m2 disconnected
    manager.tellState(SocketState.DISCONNECTED, m2)
    m2.expectMsgClass(SocketManager.RequestClearSubscribe::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    // balance message
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h3")
    m3.expectMsgClass(AppendMessage::class.java).checkMessage("h4")

    // m2 connected
    manager.notifyConnected(m2)
    m1.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h1")
    m3.expectMsgClass(ClearAppendMessage::class.java).checkMessage("h5")
    m2.expectMsgClass(BatchSendMessage::class.java).checkBatchMessage("h1", "h5")
  }

  /* -- batch :begin -- */

  @Test
  fun testBatchEmpty() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage()

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testBatchEmptyConnectionExist() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.requestConnect()

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectNoMessage()

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage()
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testBatchEmptySubscribeExist() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellMessage("h1".toAppend())

    // prepare connect
    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.tellBatchMessage()
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  @Test
  fun testBatchInactiveResume() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.requestDisconnect()

    // prepare connect
    m1.expectNoMessage()
    m2.expectNoMessage()

    manager.tellBatchMessage("h1".toAppend())

    m1.expectNoMessage()
    m2.expectNoMessage()

    manager.requestConnect()

    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    manager.notifyConnected(m2)
    m2.expectNoMessage()
  }

  @Test
  fun testBatchSubscribeInactiveResume() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()
    val m3 = probeOf()
    val m4 = probeOf()

    receiver.watch(m1.ref)
    receiver.watch(m2.ref)

    val manager = managerOf(receiver, listOf(m1, m2, m3, m4), PooledConfig(
      initSubscribe = 1
    ))
    manager.tellBatchMessage("h1".toAppend())

    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    manager.notifyConnected(m2)
    m2.expectNoMessage()

    manager.requestDisconnect()
    receiver.expectTerminated(m1.ref)
    receiver.expectTerminated(m2.ref)

    manager.tellBatchMessage("h2".toAppend())
    m1.expectNoMessage()
    m2.expectNoMessage()

    manager.requestConnect()
    m3.expectMsgClass(SocketManager.RequestConnect::class.java)
    m4.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m3)
    m3.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    manager.notifyConnected(m4)
    m4.expectMsgClass(AppendMessage::class.java).checkMessage("h2")
  }

  @Test
  fun testBatchAppendDuplicated() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, m1, m2)
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h1".toAppend()
    )

    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    manager.notifyConnected(m2)
    m2.expectNoMessage()
  }

  @Test
  fun testBatchAppendDuplicatedSubscribeExist() {
    val receiver = probeOf()

    val m1 = probeOf()
    val m2 = probeOf()

    val manager = managerOf(receiver, listOf(m1, m2), PooledConfig(
      initSubscribe = 1
    ))
    manager.tellBatchMessage(
      "h1".toAppend(),
      "h2".toAppend()
    )

    m1.expectMsgClass(SocketManager.RequestConnect::class.java)
    m2.expectMsgClass(SocketManager.RequestConnect::class.java)

    manager.notifyConnected(m1)
    m1.expectMsgClass(AppendMessage::class.java).checkMessage("h1")

    manager.notifyConnected(m2)
    m2.expectMsgClass(AppendMessage::class.java).checkMessage("h2")

    manager.tellBatchMessage("h1".toAppend())
    m1.expectNoMessage()
    m2.expectNoMessage()
  }

  /* -- batch :end -- */

  /* -- test kit extensions :begin -- */

  /**
   * Returns probe instance
   */
  private fun probeOf() = TestKit(system)

  /**
   * Request terminated
   */
  private fun TestKit.requestTerminated() {
    system.stop(this.ref)
  }

  /* -- test kit extensions :end -- */

  /* -- manager extensions :begin -- */

  private fun managerOf(requester: TestKit, vararg child: TestKit, config: PooledConfig? = null): ActorRef {
    return system.actorOf(
      PooledSocketManager.props(
        requester.ref,
        ListManagerProvider(
          child.map { it.ref }
        ),
        config
      )
    )
  }

  private fun managerOf(requester: TestKit, childProbes: List<TestKit>, config: PooledConfig? = null): ActorRef {
    return system.actorOf(
      PooledSocketManager.props(
        requester.ref,
        ListManagerProvider(
          childProbes.map { it.ref }
        ),
        config
      )
    )
  }

  private fun ActorRef.tellMessage(message: Any) {
    this.tell(message, ActorRef.noSender())
  }

  private fun ActorRef.tellBatchMessage(vararg message: SendMessage) {
    this.tell(
      batchOf(*message),
      ActorRef.noSender()
    )
  }

  private fun ActorRef.tellState(state: SocketState, probe: TestKit) {
    this.tell(state, probe.ref)
  }

  private fun ActorRef.requestConnect() {
    this.tell(
      SocketManager.RequestConnect(),
      ActorRef.noSender()
    )
  }

  private fun ActorRef.requestDisconnect() {
    this.tell(
      SocketManager.RequestDisconnect(),
      ActorRef.noSender()
    )
  }

  private fun ActorRef.notifyConnected(probe: TestKit) {
    this.tell(SocketState.CONNECTING, probe.ref)
    this.tell(SocketState.CONNECTED, probe.ref)
  }

  /**
   * List manager provider
   */
  private class ListManagerProvider(private val items: List<ActorRef>): (AbstractActor.ActorContext, ActorRef, ProxyConfig.HostInfo?, Int) -> ActorRef {
    override fun invoke(context: AbstractActor.ActorContext, ref: ActorRef, hostInfo: ProxyConfig.HostInfo?, id: Int): ActorRef {
      return items.getOrNull(id) ?: items.last()
    }
  }

  /* -- manager extensions :end -- */

  /* -- message extensions :begin -- */

  /**
   * Parse current value to append message
   */
  private fun String.toAppend(group: String = "test", unsubscribe: String = ""): AppendMessage {
    return AppendMessage(
      SubscribeInfo(this, group, unsubscribe)
    )
  }

  /**
   * Parse current value to clear append message
   */
  private fun String.toClearAppend(group: String = "test", unsubscribe: String = ""): ClearAppendMessage {
    return ClearAppendMessage(
      SubscribeInfo(this, group, unsubscribe)
    )
  }

  /**
   * Parse current value to replace message
   */
  private fun String.toReplace(group: String = "test", unsubscribe: String = ""): ReplaceMessage {
    return ReplaceMessage(
      SubscribeInfo(this, group, unsubscribe)
    )
  }

  /**
   * Parse current value to clear replace message
   */
  private fun String.toClearReplace(group: String = "test", unsubscribe: String = ""): ClearReplaceMessage {
    return ClearReplaceMessage(
      SubscribeInfo(this, group, unsubscribe)
    )
  }

  /**
   * Parse current value to strict message
   */
  private fun String.toStrict(group: String = "test", unsubscribe: String = ""): ReplaceMessage {
    return ReplaceMessage(
      SubscribeInfo(this, group, unsubscribe),
      strict = true
    )
  }

  /**
   * Parse current value to clear strict message
   */
  private fun String.toClearStrict(group: String = "test", unsubscribe: String = ""): ClearReplaceMessage {
    return ClearReplaceMessage(
      SubscribeInfo(this, group, unsubscribe),
      strict = true
    )
  }

  /**
   * Parse current value to queue message
   */
  private fun String.toQueue(): QueueMessage {
    return QueueMessage(this)
  }

  /**
   * Check message
   */
  private fun SubscribeMessage.checkMessage(message: String) {
    assert(info.subscribe == message)
  }

  /**
   * Check message
   */
  private fun BatchSendMessage.checkBatchMessage(vararg message: String) {
    assert(items.size == message.size)

    items.forEachIndexed { i, msg ->
      msg as SubscribeMessage
      assert(msg.info.subscribe == message[i])
    }
  }

  /**
   * Check strict
   */
  private fun ReplaceMessage.checkStrict(message: String) {
    checkMessage(message)
    assert(strict)
  }

  /**
   * Check strict
   */
  private fun ClearReplaceMessage.checkStrict(message: String) {
    checkMessage(message)
    assert(strict)
  }

  /**
   * Check queue
   */
  private fun QueueMessage.checkQueue(message: String) {
    assert(this.message == message)
  }

  /**
   * Check message
   */
  private fun BatchSendMessage.checkBatchQueue(vararg message: String) {
    assert(items.size == message.size)

    items.forEachIndexed { i, msg ->
      msg as QueueMessage
      assert(msg.message == message[i])
    }
  }

  /**
   * Parse message items to batch message
   */
  private fun batchOf(vararg message: SendMessage): BatchSendMessage {
    return BatchSendMessage(message.toList())
  }

  /* -- message extensions :end -- */

  companion object {

    private var cachedSystem: ActorSystem? = null

    private val system: ActorSystem
      get() = cachedSystem ?: ActorSystem.create().also {
        cachedSystem = it
      }

    @BeforeClass
    @JvmStatic
    fun setup() {
      cachedSystem = ActorSystem.create()
    }

    @AfterClass
    @JvmStatic
    fun teardown() {
      cachedSystem?.terminate()
      cachedSystem = null
    }
  }
}