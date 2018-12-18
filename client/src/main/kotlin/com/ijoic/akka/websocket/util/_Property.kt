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
package com.ijoic.akka.websocket.util

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Returns generated delegate property which would update it's value
 * automatically while related value of [getVersion] changed
 */
internal fun<R, T> R.bindVersion(getVersion: () -> Int, getValue: () -> T): ReadOnlyProperty<R, T> {
  return object : ReadOnlyProperty<R, T> {
    private var currVersion: Int? = null
    private var currValue: T? = null

    override fun getValue(thisRef: R, property: KProperty<*>): T {
      val oldValue = currValue
      val oldVersion = currVersion

      if (oldValue == null || oldVersion == null) {
        return upgradeValue(getVersion())
      }
      val newVersion = getVersion()

      return if (oldValue != newVersion) {
        upgradeValue(newVersion)
      } else {
        oldValue
      }
    }

    private fun upgradeValue(version: Int): T {
      currVersion = version
      return getValue().also {
        currValue = it
      }
    }
  }
}