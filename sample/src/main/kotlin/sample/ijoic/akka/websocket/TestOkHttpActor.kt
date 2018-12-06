package sample.ijoic.akka.websocket

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.ijoic.akka.websocket.OkHttpSocketClient
import com.ijoic.akka.websocket.client.SocketManager
import com.ijoic.akka.websocket.message.QueueMessage
import com.ijoic.akka.websocket.options.DefaultSocketOptions
import com.ijoic.metrics.MetricsConfig
import com.ijoic.metrics.MetricsHandler
import com.ijoic.metrics.MetricsMessage
import java.time.Duration

fun main() {
  MetricsConfig.traceEnabled = true
//  MetricsConfig.handler = object: MetricsHandler {
//    override fun dispatchMetricsDelay(message: MetricsMessage, delay: Long) {
//      println("delay: $delay ms")
//    }
//  }

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