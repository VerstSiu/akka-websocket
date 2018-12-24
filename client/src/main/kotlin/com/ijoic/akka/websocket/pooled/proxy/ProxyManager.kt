/*
 *
 *  Copyright(c) 2018 VerstSiu
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.ijoic.akka.websocket.pooled.proxy

/**
 * Proxy manager
 *
 * @author verstsiu created at 2018-12-24 16:22
 */
internal class ProxyManager<T>(config: ProxyConfig?) {

  private val proxyIdToHostInfoMap: Map<String, ProxyConfig.HostInfo> = mutableMapOf<String, ProxyConfig.HostInfo>().apply {
    config?.hosts?.forEach {
      this["${it.host}:${it.port}"] = it
    }
  }

  private val proxyIdList: List<String> = mutableListOf<String>()
    .also {
      if (config?.allowEmptyProxy == true) {
        it.add("")
      }

      it.addAll(proxyIdToHostInfoMap.map { (proxyId, _) -> proxyId })
    }
  private val minActive = config?.minActive ?: 0
  private val maxActive = Math.max(config?.maxActive ?: 0, minActive)

  /**
   * proxyId -> itemCount
   */
  private val itemCountMap = mutableMapOf<String, Int>()

  /**
   * proxyId -> errorCount
   */
  private val errorCountMap = mutableMapOf<String, Int>()

  /**
   * item -> proxyId
   */
  private val proxyIdMap = mutableMapOf<T, String>()

  /**
   * Proxy enabled status
   */
  private val proxyEnabled = config != null && config.minActive > 0

  /**
   * Returns available proxy id or null
   */
  fun obtainProxyId(): String? {
    if (!proxyEnabled) {
      return null
    }

    return obtainIdleProxyIdOrNull() ?: obtainActiveProxyIdOrNull()
  }

  private fun obtainIdleProxyIdOrNull(): String? {
    return proxyIdList.firstOrNull {
      val itemCount = itemCountMap[it] ?: 0
      val errorCount = errorCountMap[it] ?: 0

      (itemCount + errorCount) < minActive
    }
  }

  private fun obtainActiveProxyIdOrNull(): String? {
    return proxyIdList.firstOrNull {
      val itemCount = itemCountMap[it] ?: 0
      val errorCount = errorCountMap[it] ?: 0

      itemCount >= minActive && (itemCount + errorCount) < maxActive
    }
  }

  /**
   * Wrap client options with proxy info related to [proxyId]
   *
   * Returns null if all proxies exceed the max-active connection size
   */
  fun getHostInfo(proxyId: String): ProxyConfig.HostInfo? {
    return proxyIdToHostInfoMap[proxyId]
  }

  /**
   * Assign [proxyId] with [item] reference
   */
  fun assignProxyId(proxyId: String, item: T) {
    proxyIdMap[item] = proxyId
    itemCountMap.increase(proxyId, maxActive)
  }

  /**
   * Release proxy id for [item] reference
   */
  fun releaseProxyId(item: T) {
    val proxyId = proxyIdMap[item]
    proxyIdMap.remove(item)

    if (proxyId != null) {
      itemCountMap.decrease(proxyId)
    }
  }

  /**
   * Notify connection complete
   */
  fun notifyConnectionComplete(item: T) {
    val proxyId = proxyIdMap[item] ?: return
    errorCountMap.decrease(proxyId)
  }

  /**
   * Notify connection error
   */
  fun notifyConnectionError(item: T) {
    val proxyId = proxyIdMap[item] ?: return
    errorCountMap.increase(proxyId, maxActive)
  }

  /**
   * Returns connection unreachable status
   */
  fun isConnectionUneachable(item: T): Boolean {
    val proxyId = proxyIdMap[item]

    if (!proxyEnabled || proxyId == null) {
      return false
    }
    return errorCountMap[proxyId] == maxActive
  }

  /**
   * Reset manager status
   */
  fun reset() {
    proxyIdMap.clear()
    itemCountMap.clear()
  }

  /**
   * Increase count value with [itemKey] and count [max]
   */
  private fun MutableMap<String, Int>.increase(itemKey: String, max: Int) {
    val itemCount = this[itemKey]

    if (itemCount == null) {
      this[itemKey] = 0
    } else if (itemCount < max) {
      this[itemKey] = itemCount + 1
    }
  }

  /**
   * Increase count value with [itemKey] and count [max]
   */
  private fun MutableMap<String, Int>.decrease(itemKey: String) {
    val itemCount = this[itemKey] ?: return

    if (itemCount > 0) {
      this[itemKey] = itemCount - 1
    } else {
      this.remove(itemKey)
    }
  }
}