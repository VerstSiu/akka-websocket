
# Akka Websocket

[![](https://jitpack.io/v/VerstSiu/akka-websocket.svg)](https://jitpack.io/#VerstSiu/akka-websocket)

Akka web socket library project.

## Get Start

1. Add the JitPack repository to your build file:

    ```gradle
    allprojects {
        repositories {
            // ..
            maven { url 'https://jitpack.io' }
        }
    }
    ```

 2. Add the dependency:

    ```gradle
    dependencies {
        implementation 'com.github.VerstSiu.akka-websocket:client:master'
        implementation 'com.github.VerstSiu.akka-websocket:client-okhttp:master'

        // pusher
        implementation 'com.pusher:pusher-java-client:1.8.2'
        implementation 'com.github.VerstSiu.akka-websocket:client-pusher:master'
    }
    ```

## Usage

1. Create custom actor:

    ```kotlin
    class ClientActor(options: ClientOptions): AbstractActor() {
      private val socketManager = context.actorOf(SocketManager.props(options, self), "socket-manager")

      override fun createReceive(): Receive {
        return receiveBuilder()
          .match(SendMessage::class.java) { socketManager.forward(it, context) }
          .match(SocketMessage::class.java) {
            println(it.toString())
          }
          .build()
      }

      companion object {
        /**
         * Returns client actor props instance with [options]
         */
        @JvmStatic
        fun props(options: ClientOptions): Props {
          return Props.create(ClientActor::class.java, options)
        }
      }
    }
    ```

2. Start actor and send messages:

    ```kotlin
    val system = ActorSystem.create()
    val options = DefaultSocketOptions("wss://echo.websocket.org")

    val client = system.actorOf(ClientActor.props(options))
    client.tell(QueueMessage("Hello world!"), ActorRef.noSender())
    ```

## Reference

* [akka](https://akka.io/)
* [okhttp](https://square.github.io/okhttp/)
* [pusher](https://pusher.com/docs)

## License

```

   Copyright(c) 2018 VerstSiu

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

```