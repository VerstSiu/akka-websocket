package sample.ijoic.akka.websocket

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.ijoic.akka.websocket.client.SocketConfig
import com.ijoic.akka.websocket.client.SocketManager
import com.ijoic.akka.websocket.message.AppendMessage
import com.ijoic.akka.websocket.message.ClearAppendMessage
import com.ijoic.akka.websocket.message.QueueMessage

fun main() {
  val system = ActorSystem.create()
  val config = SocketConfig("wss://echo.websocket.org")

  val receiver = system.actorOf(ReceiverActor.props())
  val manager = system.actorOf(SocketManager.props(config, receiver))

//  manager.tell(AppendMessage("O", "test"), ActorRef.noSender())
//  manager.tell(AppendMessage("I", "test"), ActorRef.noSender())
//  manager.tell(AppendMessage("C", "test"), ActorRef.noSender())
//  manager.tell(AppendMessage("U", "test"), ActorRef.noSender())
//  manager.tell(ClearAppendMessage("dismiss [U]", "test", "U"), ActorRef.noSender())
  manager.tell(QueueMessage("Hello world!"), ActorRef.noSender())

  try {
    println("Press ENTER to exit")
    System.`in`.read()
  } finally {
    system.terminate()
  }
}