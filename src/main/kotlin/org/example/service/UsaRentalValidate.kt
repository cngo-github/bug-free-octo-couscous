package org.example.org.example.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.option
import arrow.core.recover
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import org.example.org.example.persistence.cache.RentalPriceCacheDao
import org.example.org.example.persistence.cache.ToolCacheDao
import org.example.org.example.persistence.data.RentalPrice
import org.example.org.example.persistence.data.Tool
import org.example.org.example.persistence.db.domain.ToolDBDao
import org.example.org.example.persistence.enums.ToolCode
import org.example.org.example.persistence.enums.ToolType
import org.example.org.example.service.domain.RentalValidate
import org.example.persistence.db.ToolSqliteDbDao

class UsaRentalValidate
private constructor(
    private val toolCacheDao: ToolCacheDao,
    private val rentalPriceCacheDao: RentalPriceCacheDao,
    private val toolDBDao: ToolDBDao
) : RentalValidate {
  private val toolCacheLoader: CacheLoader<ToolCode, Tool> =
      CacheLoader.from { k: ToolCode ->
        val tools =
            toolCacheDao
                .get(k)
                .onNone { LOGGER.trace { "Failed to get the tool $k from the cache." } }
                .recover { getFromDB(k).bind() }
                .toList()

        if (tools.size != 1) {
          throw ToolUnavailable(k)
        }

        tools.first()
      }

  private val toolMap =
      CacheBuilder.newBuilder()
          .expireAfterWrite(DEFAULT_LOCAL_CACHE_EVICTION_TIME.toJavaDuration())
          .build(toolCacheLoader)

  private val rentalPriceCacheLoader: CacheLoader<ToolType, RentalPrice> =
      CacheLoader.from { k: ToolType ->
        val rentalPrices =
            rentalPriceCacheDao
                .get(k)
                .onNone { LOGGER.trace { "Failed to get the rental price for $k from the cache." } }
                .recover { getFromDB(k).bind() }
                .toList()

        if (rentalPrices.size != 1) {
          throw RentalPriceUnavailable(k)
        }

        rentalPrices.first()
      }

  private val rentalPriceMap =
      CacheBuilder.newBuilder()
          .expireAfterWrite(DEFAULT_LOCAL_CACHE_EVICTION_TIME.toJavaDuration())
          .build(rentalPriceCacheLoader)

  private fun getFromDB(toolCode: ToolCode): Option<Tool> =
      toolDBDao.get(toolCode).onSome {
        LOGGER.trace { "Got the tool $toolCode from the database." }

        toolCacheDao.store(it)
      }

  private fun getFromDB(toolType: ToolType): Option<RentalPrice> =
      toolDBDao.get(toolType).onSome {
        LOGGER.trace { "Got the rental price for $toolType from the database." }

        rentalPriceCacheDao.store(it)
      }

  override fun get(toolCode: ToolCode): Option<Tool> =
      Either.catch { toolMap.get(toolCode) }
          .onLeft { LOGGER.error(it) { "Failed to get the tool $toolCode." } }
          .getOrNone()

  override fun get(toolType: ToolType): Option<RentalPrice> =
      Either.catch { rentalPriceMap.get(toolType) }
          .onLeft { LOGGER.error(it) { "Failed to get the rental price for $toolType." } }
          .getOrNone()

  override fun isValid(tool: Tool): Boolean =
      Either.catch { toolMap.get(tool.toolCode) }.isRight { tool == it }

  companion object {
    val DEFAULT_LOCAL_CACHE_EVICTION_TIME = Duration.parse("1h")

    private val LOGGER = KotlinLogging.logger {}

    fun mk(redisUri: URI, sqliteDbUri: URI): Option<UsaRentalValidate> =
        option {
              val toolCacheDao = ToolCacheDao.mkRedisDao(redisUri).bind()
              val rentalPriceCacheDao = RentalPriceCacheDao.mkRedisDao(redisUri).bind()
              val dbDao = ToolSqliteDbDao.mk(sqliteDbUri).bind()

              Triple(toolCacheDao, rentalPriceCacheDao, dbDao)
            }
            .map { UsaRentalValidate(it.first, it.second, it.third) }
            .onNone {
              LOGGER.error { "Failed to create the ${UsaRentalValidate::class.java} object." }
            }
  }
}

data class ToolUnavailable(val toolCode: ToolCode) : Throwable("Tool $toolCode is unavailable.")

data class RentalPriceUnavailable(val toolType: ToolType) :
    Throwable("The rental price for $toolType is unavailable.")
