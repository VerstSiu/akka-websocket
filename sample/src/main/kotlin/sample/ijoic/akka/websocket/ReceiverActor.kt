package sample.ijoic.akka.websocket

import akka.actor.AbstractLoggingActor
import akka.actor.Props
import com.ijoic.akka.websocket.client.SocketMessage
import com.ijoic.akka.websocket.state.SocketState

class ReceiverActor: AbstractLoggingActor() {
  override fun createReceive(): Receive {
    return receiveBuilder()
      .match(SocketState::class.java) {
        log().info("state changed: $it")
      }
      .match(SocketMessage::class.java) {
        log().info(it.toString())
      }
      .build()
  }

  companion object {
    /**
     * Returns receiver actor props instance with config
     */
    @JvmStatic
    fun props(): Props {
      return Props.create(ReceiverActor::class.java)
    }
  }
}