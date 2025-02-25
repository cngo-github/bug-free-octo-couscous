package org.example.persistence.cache

import arrow.core.Option
import com.google.gson.GsonBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import kotlin.time.Duration
import org.example.persistence.enums.ToolType
import org.example.persistence.data.RentalPrice
import org.example.persistence.data.RentalPriceDeserializer
import org.example.persistence.data.RentalPriceSerializer

class RentalPriceCacheDao private constructor(private val cacheDao: CacheDao) {
  fun get(type: ToolType): Option<RentalPrice> {
    LOGGER.trace { "Getting the rental price for $type." }

    return cacheDao.get(key(type)).map { GSON.fromJson(it, RentalPrice::class.java) }
  }

  fun store(rentalPrice: RentalPrice): Option<String> {
    LOGGER.trace { "Storing the rental price for ${rentalPrice.type}." }

    return cacheDao.set(key(rentalPrice.type), GSON.toJson(rentalPrice), DEFAULT_TIMEOUT)
  }

  fun cleanup() = cacheDao.cleanup()

  companion object {
    val DEFAULT_TIMEOUT = Duration.parse("5m")

    private val LOGGER = KotlinLogging.logger {}
    private val GSON =
        GsonBuilder()
            .registerTypeAdapter(RentalPrice::class.java, RentalPriceDeserializer())
            .registerTypeAdapter(RentalPrice::class.java, RentalPriceSerializer())
            .create()

    fun mkRedisDao(uri: URI): Option<RentalPriceCacheDao> =
        RedisCacheDao(uri)
            .map { RentalPriceCacheDao(it) }
            .onNone {
              LOGGER.error { "Failed to make the ${RentalPriceCacheDao::cacheDao} object." }
            }

    private fun key(type: ToolType): String = "rentalPrice-$type"
  }
}
