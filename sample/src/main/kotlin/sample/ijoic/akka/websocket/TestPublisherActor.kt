package sample.ijoic.akka.websocket

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.ijoic.akka.websocket.PusherSocketClient
import com.ijoic.akka.websocket.client.SocketManager
import com.ijoic.akka.websocket.message.AppendMessage
import com.ijoic.akka.websocket.options.WrapPusherOptions
import com.ijoic.akka.websocket.pusher.AddSubscribe

fun main() {
  val system = ActorSystem.create()
  val options = WrapPusherOptions(
    appKey = "de504dc5763aeef9ff52"
  )

  val receiver = system.actorOf(ReceiverActor.props())
  val manager = system.actorOf(SocketManager.props(options, receiver, PusherSocketClient()))

  manager.tell(
    AppendMessage(
      AddSubscribe(channel = "live_trades", event = "trade"),
      "ticker"
    ),
    ActorRef.noSender()
  )
}