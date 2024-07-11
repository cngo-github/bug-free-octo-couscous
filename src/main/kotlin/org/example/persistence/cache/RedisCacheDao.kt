package org.example.persistence.cache

import arrow.core.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import kotlin.time.Duration
import redis.clients.jedis.JedisPool

private val LOGGER = KotlinLogging.logger() {}

class RedisCacheDao private constructor(private val pool: JedisPool) : CacheDao {
  override fun set(key: String, value: String, timeout: Duration): Option<String> {
    LOGGER.trace { "Set the value for $key with a timeout ${timeout.inWholeSeconds} seconds." }

    return Either.catch { pool.resource }
        .onLeft { LOGGER.error(it) { "Unable to communicate with the Redis server." } }
        .map { Pair(it, it.setex(key, timeout.inWholeSeconds, value)) }
        .onRight { pool.returnResource(it.first) }
        .getOrNone()
        .flatMap {
          when {
            !it.second.isNullOrBlank() -> Some(it.second)
            else -> None
          }
        }
  }

  override fun get(key: String): Option<String> {
    LOGGER.trace { "Get the value for $key." }

    return Either.catch { pool.resource }
        .onLeft { LOGGER.error(it) { "Unable to communicate with the Redis server." } }
        .map { Pair(it, it.get(key)) }
        .onRight { pool.returnResource(it.first) }
        .getOrNone()
        .flatMap {
          when {
            !it.second.isNullOrBlank() -> Some(it.second)
            else -> None
          }
        }
  }

  override fun cleanup() = pool.close()

  companion object {
    operator fun invoke(uri: URI): Option<RedisCacheDao> =
        Either.catch { JedisPool(uri) }
            .map { RedisCacheDao(it) }
            .onLeft { LOGGER.error(it) { "Unable to connect to the Redis cache at $uri." } }
            .getOrNone()
  }
}
