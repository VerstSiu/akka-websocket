package sample.ijoic.akka.websocket

import akka.actor.AbstractLoggingActor
import akka.actor.Props
import com.ijoic.akka.websocket.client.ConnectionCompleted
import com.ijoic.akka.websocket.client.SocketError
import com.ijoic.akka.websocket.client.SocketMessage
import com.ijoic.akka.websocket.pooled.PooledSocketManager
import com.ijoic.akka.websocket.state.SocketState

class ReceiverActor: AbstractLoggingActor() {
  override fun createReceive(): Receive {
    return receiveBuilder()
      .match(SocketState::class.java) {
        log().info("state changed: $it")
      }
      .match(SocketMessage::class.java) {
        when(it) {
          is ConnectionCompleted -> println("connection completed")
          is SocketError -> {
            val msgName = it.javaClass.simpleName
            val msgCode = it.code?.let { code -> "code - $code, " } ?: ""
            val msgReason = it.message?.let { message -> "message - $message, " } ?: ""
            val msgErrorInfo = it.cause?.let { error -> error.message ?: error.javaClass.name } ?: "unknown error"

            println("$msgName: $msgCode$msgReason$msgErrorInfo")
          }
          else -> println("from - ${sender.path()}, msg - $it")
        }
      }
      .match(PooledSocketManager.AssignWarning::class.java) {
        println("${it.tag} - connections: ${it.activeConnectionSize}/${it.allConnectionSize}, idle message size: ${it.idleMessages.size}")
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