package org.example.org.example.persistence.cache

import arrow.core.Option
import kotlin.time.Duration

interface CacheDao {
  fun set(key: String, value: String, timeout: Duration): Option<String>

  fun get(key: String): Option<String>

  fun cleanup()
}
