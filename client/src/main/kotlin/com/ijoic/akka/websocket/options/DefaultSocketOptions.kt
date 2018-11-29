package com.ijoic.akka.websocket.options

import com.ijoic.akka.websocket.client.ClientOptions
import java.time.Duration

/**
 * Default socket options
 *
 * @author verstsiu created at 2018-11-29 11:57
 */
data class DefaultSocketOptions(
  val url: String,
  val pingMessage: String = "",
  val pingDuration: Duration = Duration.ZERO,
  val disconnectWhenIdle: Boolean = false,
  val disconnectWhenIdleDelay: Duration = Duration.ofSeconds(30)
): ClientOptions