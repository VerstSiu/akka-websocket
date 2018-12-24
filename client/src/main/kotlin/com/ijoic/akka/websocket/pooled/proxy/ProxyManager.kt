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

  private val proxyToItemCountMap = mutableMapOf<String, Int>()
  private val itemToHostIdMap = mutableMapOf<T, String>()

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
      val itemCount = proxyToItemCountMap[it]
      itemCount == null || itemCount < minActive
    }
  }

  private fun obtainActiveProxyIdOrNull(): String? {
    return proxyIdList.firstOrNull {
      val itemCount = proxyToItemCountMap[it]
      itemCount != null && itemCount >= minActive && itemCount < maxActive
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
    itemToHostIdMap[item] = proxyId
    increaseItemCount(proxyId)
  }

  /**
   * Release proxy id for [item] reference
   */
  fun releaseProxyId(item: T) {
    val proxyId = itemToHostIdMap[item]
    itemToHostIdMap.remove(item)

    if (proxyId != null) {
      decreaseItemCount(proxyId)
    }
  }

  private fun increaseItemCount(proxyId: String) {
    val itemCount = proxyToItemCountMap[proxyId]

    if (itemCount == null) {
      proxyToItemCountMap[proxyId] = 0
    } else {
      proxyToItemCountMap[proxyId] = itemCount + 1
    }
  }

  private fun decreaseItemCount(proxyId: String) {
    val itemCount = proxyToItemCountMap[proxyId] ?: return

    if (itemCount > 0) {
      proxyToItemCountMap[proxyId] = itemCount - 1
    } else {
      proxyToItemCountMap.remove(proxyId)
    }
  }

  /**
   * Reset manager status
   */
  fun reset() {
    itemToHostIdMap.clear()
    proxyToItemCountMap.clear()
  }
}