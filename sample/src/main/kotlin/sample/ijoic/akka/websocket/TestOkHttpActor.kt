package sample.ijoic.akka.websocket

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.ijoic.akka.websocket.OkHttpSocketClient
import com.ijoic.akka.websocket.client.SocketManager
import com.ijoic.akka.websocket.message.AppendMessage
import com.ijoic.akka.websocket.message.BatchSendMessage
import com.ijoic.akka.websocket.message.QueueMessage
import com.ijoic.akka.websocket.options.DefaultSocketOptions
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

  val receiver = system.actorOf(ReceiverActor.props())
  val manager = system.actorOf(SocketManager.props(options, receiver, OkHttpSocketClient()))

  // subscribe at 0s
  println("----- connect -----")
  manager.tell(
    BatchSendMessage(
      items = listOf(
        AppendMessage("Hello world!", "test"),
        QueueMessage("I'm here!")
      )
    ),
    ActorRef.noSender()
  )

  // disconnect at 10s
  system.scheduler
    .scheduleOnce(
      Duration.ofSeconds(10),
      {
        println("----- disconnect -----")
        manager.tell(SocketManager.RequestDisconnect(), ActorRef.noSender())
      },
      system.dispatcher
    )

  // reconnect at 20s
  system.scheduler
    .scheduleOnce(
      Duration.ofSeconds(20),
      {
        println("----- connect -----")
        manager.tell(SocketManager.RequestConnect(), ActorRef.noSender())
      },
      system.dispatcher
    )

  // clear subscribe at 30s
  system.scheduler
    .scheduleOnce(
      Duration.ofSeconds(30),
      {
        println("----- clear subscribe -----")
        manager.tell(SocketManager.RequestClearSubscribe(), ActorRef.noSender())
      },
      system.dispatcher
    )
}