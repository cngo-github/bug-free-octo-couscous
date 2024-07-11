package org.example.persistence.cache

import arrow.core.Option
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.time.Year
import kotlin.time.Duration
import org.example.persistence.data.Holiday
import org.example.persistence.data.HolidayDeserializer
import org.example.persistence.data.HolidaySerializer

class HolidaysCacheDao private constructor(private val cacheDao: CacheDao) {
  fun get(year: Year): Option<List<Holiday>> {
    LOGGER.trace { "Getting the holidays for $year." }

    return cacheDao.get(key(year)).map {
      GSON.fromJson(it, object : TypeToken<List<Holiday>>() {}.type)
    }
  }

  fun store(year: Year, holidays: List<Holiday>) {
    LOGGER.trace { "Storing the holidays for $year." }

    cacheDao.set(key(year), GSON.toJson(holidays), DEFAULT_TIMEOUT)
  }

  fun cleanup() = cacheDao.cleanup()

  companion object {
    val DEFAULT_TIMEOUT = Duration.parse("5m")

    private val LOGGER = KotlinLogging.logger {}
    private val GSON =
        GsonBuilder()
            .registerTypeAdapter(Holiday::class.java, HolidayDeserializer())
            .registerTypeAdapter(Holiday::class.java, HolidaySerializer())
            .create()

    fun mkRedisDao(uri: URI): Option<HolidaysCacheDao> =
        RedisCacheDao(uri)
            .map { HolidaysCacheDao(it) }
            .onNone {
              LOGGER.error { "Failed to create the ${HolidaysCacheDao::class.java} object." }
            }

    private fun key(year: Year): String = "holidays-${year.value}"
  }
}
