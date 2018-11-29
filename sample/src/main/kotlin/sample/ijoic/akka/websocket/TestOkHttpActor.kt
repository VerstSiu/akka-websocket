package sample.ijoic.akka.websocket

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.ijoic.akka.websocket.OkHttpSocketClient
import com.ijoic.akka.websocket.client.SocketManager
import com.ijoic.akka.websocket.message.QueueMessage
import com.ijoic.akka.websocket.options.DefaultSocketOptions
import java.time.Duration

fun main() {
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

  manager.tell(QueueMessage("Hello world!"), ActorRef.noSender())
}