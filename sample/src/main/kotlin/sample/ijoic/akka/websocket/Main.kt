package sample.ijoic.akka.websocket

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.ijoic.akka.websocket.client.SocketConfig
import com.ijoic.akka.websocket.client.SocketManager
import com.ijoic.akka.websocket.message.QueueMessage
import java.time.Duration

fun main() {
  val system = ActorSystem.create()
  val config = SocketConfig(
    url = "wss://echo.websocket.org",
    pingMessage = "ping",
    pingDuration = Duration.ofSeconds(5)
  )

  val receiver = system.actorOf(ReceiverActor.props())
  val manager = system.actorOf(SocketManager.props(config, receiver))

  manager.tell(QueueMessage("Hello world!"), ActorRef.noSender())

  try {
    println("Press ENTER to exit")
    System.`in`.read()
  } finally {
    system.terminate()
  }
}