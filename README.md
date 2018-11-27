
# Akka Websocket

[![](https://jitpack.io/v/VerstSiu/akka-websocket.svg)](https://jitpack.io/#VerstSiu/akka-websocket)

Akka web socket library project.

## Get Start

1. Add the JitPack repository to your build file:

    ```gradle
    allprojects {
        repositories {
            ...
            maven { url 'https://jitpack.io' }
        }
    }
    ```

 2. Add the dependency:

    ```gradle
    dependencies {
        implementation 'com.github.VerstSiu.akka-websocket:client:master'
        implementation 'com.github.VerstSiu.akka-websocket:client-okhttp:master'
    }
    ```

## Usage

1. Create custom client actor:

    ```kotlin
    class ClientActor(config: SocketConfig): AbstractActor() {
      private val socketManager = context.actorOf(SocketManager.props(config, self), "socket-manager")

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
         * Returns client actor props instance with [config]
         */
        @JvmStatic
        fun props(config: SocketConfig): Props {
          return Props.create(ClientActor::class.java, config)
        }
      }
    }
    ```

2. Start actor and send messages:

    ```kotlin
    val system = ActorSystem.create()
    val config = SocketConfig("wss://echo.websocket.org")

    val client = system.actorOf(ClientActor.props(config))
    client.tell(QueueMessage("Hello world!"))
    ```

## Reference

* [akka](https://akka.io/)
* [okhttp](https://square.github.io/okhttp/)

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