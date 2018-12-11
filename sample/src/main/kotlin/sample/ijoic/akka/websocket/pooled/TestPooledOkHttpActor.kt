package sample.ijoic.akka.websocket.pooled

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.ijoic.akka.websocket.OkHttpSocketClient
import com.ijoic.akka.websocket.message.AppendMessage
import com.ijoic.akka.websocket.message.BatchSendMessage
import com.ijoic.akka.websocket.message.QueueMessage
import com.ijoic.akka.websocket.message.SubscribeInfo
import com.ijoic.akka.websocket.options.DefaultSocketOptions
import com.ijoic.akka.websocket.pooled.PooledConfig
import com.ijoic.akka.websocket.pooled.PooledSocketManager
import sample.ijoic.akka.websocket.ReceiverActor
import java.time.Duration

fun main() {
//  MetricsConfig.traceEnabled = true

  val system = ActorSystem.create()
  val options = DefaultSocketOptions(
    url = "wss://echo.websocket.org",
    pingMessage = "ping",
    pingDuration = Duration.ofSeconds(5),
    disconnectWhenIdle = true,
    disconnectWhenIdleDelay = Duration.ofSeconds(20)
  )

  val receiver = system.actorOf(ReceiverActor.props(), "msg-receiver")
  val manager = system.actorOf(
    PooledSocketManager.props(
      options,
      receiver,
      { OkHttpSocketClient() },
      PooledConfig(
        idleConnectionSize = 0
      )
    ),
    "pooled-manager"
  )

//  manager.tell(
//    QueueMessage("Hello world!"),
//    ActorRef.noSender()
//  )
  manager.tell(
    BatchSendMessage(
      items = listOf(
        QueueMessage("Hello world!"),
        QueueMessage("I'm here!")
      )
    ),
    ActorRef.noSender()
  )

  // tick delay at 10s
  system.scheduler
    .scheduleOnce(
      Duration.ofSeconds(10),
      {
        println("----- tick delay -----")
        manager.tell(
          BatchSendMessage(
            items = listOf(
              AppendMessage(SubscribeInfo("t1", "test", "")),
              AppendMessage(SubscribeInfo("t2", "test", "")),
              AppendMessage(SubscribeInfo("t3", "test", ""))
            )
          ),
          ActorRef.noSender()
        )
      },
      system.dispatcher
    )

}