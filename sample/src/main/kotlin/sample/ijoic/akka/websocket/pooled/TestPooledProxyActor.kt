package sample.ijoic.akka.websocket.pooled

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.ijoic.akka.websocket.OkHttpSocketClient
import com.ijoic.akka.websocket.message.*
import com.ijoic.akka.websocket.options.DefaultSocketOptions
import com.ijoic.akka.websocket.pooled.PooledConfig
import com.ijoic.akka.websocket.pooled.PooledSocketManager
import com.ijoic.akka.websocket.pooled.proxy.ProxyConfig
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
        minIdle = 0,
        assignMessageEnabled = true,
        proxyConfig = ProxyConfig(
          hosts = listOf(
            ProxyConfig.HostInfo("127.0.0.1", 8888)
          ),
          minActive = 2,
          maxActive = 4,
          allowEmptyProxy = true
        )
      )
    ),
    "pooled-manager"
  )

  manager.tell(
    BatchSendMessage(
      items = listOf(
        ReplaceMessage(SubscribeInfo("h1", "test", ""), strict = true),
        ReplaceMessage(SubscribeInfo("h2", "test", ""), strict = true),
        ReplaceMessage(SubscribeInfo("h3", "test", ""), strict = true),
        ReplaceMessage(SubscribeInfo("h4", "test", ""), strict = true)
      )
    ),
    ActorRef.noSender()
  )

}