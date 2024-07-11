package org.example.service

import arrow.core.*
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.gson.JsonParser
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.time.LocalDate
import java.time.Year
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.use
import org.example.org.example.persistence.cache.HolidaysCacheDao
import org.example.org.example.persistence.data.Holiday
import org.example.org.example.service.domain.Holidays

class UsaHolidays private constructor(private val holidaysCacheDao: HolidaysCacheDao) : Holidays {
  private val cacheLoader: CacheLoader<Int, List<Holiday>> =
      CacheLoader.from { k: Int ->
        val year: Option<Year> =
            Either.catch { Year.of(k) }
                .onLeft { LOGGER.error(it) { "Invalid year $k" } }
                .getOrNone()

        year
            .flatMap { holidaysCacheDao.get(it) }
            .recover { year.flatMap { getFromApi(it) }.bind() }
            .getOrElse { emptyList() }
      }
  private val holidays: LoadingCache<Int, List<Holiday>> =
      CacheBuilder.newBuilder()
          .expireAfterWrite(DEFAULT_LOCAL_CACHE_EVICTION_TIME.toJavaDuration())
          .build(cacheLoader)

  override fun isHoliday(date: LocalDate): Boolean =
      Either.catch { holidays.get(date.year) }
          .map { it.any { h -> h.observedOn == date } }
          .onLeft { LOGGER.error(it) { "Failed to verify the holiday $date" } }
          .getOrElse { false }

  private fun getFromApi(year: Year): Option<List<Holiday>> {
    val uriString = "$HOLIDAY_API/${year.value}/$COUNTRY_CODE"

    return Either.catch { URI.create(uriString) }
        .onLeft { LOGGER.error { "Invalid URI $uriString" } }
        .map { Request.Builder().url(it.toURL()).build() }
        .map { OkHttpClient.Builder().build().newCall(it).execute() }
        .flatMap {
          it.use { r ->
                when (r.code) {
                  200 ->
                      Either.catch { r.body?.string() }
                          .map { json -> JsonParser.parseString(json) }
                          .map { e ->
                            e.asJsonArray.filter { elem ->
                              HOLIDAY_NAMES.contains(elem.asJsonObject.get("localName").asString)
                            }
                          }
                          .map { l -> l.map { e -> e.asJsonObject } }
                          .map { l ->
                            l.map { o ->
                              Holiday(
                                  o.get("localName").asString,
                                  LocalDate.parse(o.get("date").asString))
                            }
                          }
                  else ->
                      Either.Left(
                          FailedToGetFromApi(
                              "Failed to get the holidays for ${year.value} from the API."))
                }
              }
              .onLeft { e ->
                LOGGER.error(e) { "Failed to get the holidays for ${year.value} from the API." }
              }
        }
        .getOrNone()
  }

  companion object {
    val HOLIDAY_API: URI = URI.create("https://date.nager.at/api/v3/PublicHolidays")
    val DEFAULT_LOCAL_CACHE_EVICTION_TIME = Duration.parse("1h")
    val HOLIDAY_NAMES = listOf("Independence Day", "Labour Day")

    const val COUNTRY_CODE = "US"

    private val LOGGER = KotlinLogging.logger {}

    fun mkRedisService(uri: URI): Option<UsaHolidays> =
        HolidaysCacheDao.mkRedisDao(uri)
            .map { UsaHolidays(it) }
            .onNone {
              LOGGER.error { "Failed to make the Redis-backed ${UsaHolidays::class.java} object." }
            }
  }
}

data class FailedToGetFromApi(override val message: String) : Throwable(message)
