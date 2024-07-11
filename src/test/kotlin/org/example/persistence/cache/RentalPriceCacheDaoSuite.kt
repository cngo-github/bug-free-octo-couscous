package org.example.persistence.cache

import arrow.core.Option
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import kotlin.time.Duration
import org.example.org.example.persistence.cache.RedisCacheDao
import org.example.org.example.persistence.cache.RentalPriceCacheDao
import org.example.org.example.persistence.enums.ToolType
import org.example.persistence.data.RentalPrice
import redis.embedded.RedisServer

class RentalPriceCacheDaoSuite :
    FunSpec({
      lateinit var redisServer: RedisServer
      val redisPort = 12345
      val redisUri: URI = URI.create("redis://localhost:$redisPort")

      lateinit var maybeRentalPriceCacheDao: Option<RentalPriceCacheDao>

      beforeSpec {
        redisServer = RedisServer(redisPort)
        redisServer.start()
      }

      afterSpec { redisServer.stop() }

      beforeEach {
        maybeRentalPriceCacheDao =
            RedisCacheDao(redisUri)
                .onSome {
                  it.set(
                      "rentalPrice-Chainsaw",
                      "{\"type\": \"Chainsaw\", \"dailyPrice\": \"USD 2\", \"weekdayCharge\": \"true\", \"weekendCharge\": \"false\", \"holidayCharge\": \"true\"}",
                      Duration.parse("5m"))
                }
                .flatMap { RentalPriceCacheDao.mkRedisDao(redisUri) }
      }

      afterEach { maybeRentalPriceCacheDao.onSome { it.cleanup() } }

      test("mkRedisDao with a valid URI gives Some of RentalCacheDao") {
        RentalPriceCacheDao.mkRedisDao(redisUri).isSome() shouldBe true
      }

      test("mkRedisDao with an invalid URI gives None") {
        RentalPriceCacheDao.mkRedisDao(URI.create("redis://notReal")).isNone() shouldBe true
      }

      test("Get populated rental price returns Some of RentalPrice") {
        maybeRentalPriceCacheDao
            .flatMap { it.get(ToolType.Chainsaw) }
            .isSome { p ->
              p.type == ToolType.Chainsaw &&
                  p.dailyPrice.toString() == "USD 2" &&
                  p.weekdayCharge &&
                  !p.weekendCharge &&
                  p.holidayCharge
            } shouldBe true
      }

      test("Get unpopulated rental price returns None") {
        maybeRentalPriceCacheDao.flatMap { it.get(ToolType.Ladder) }.isNone() shouldBe true
      }

      test("Store new RentalPrice") {
        RentalPrice.from(
                "Jackhammer",
                "USD 2",
                weekdayCharge = false,
                weekendCharge = true,
                holidayCharge = false)
            .onSome { p -> maybeRentalPriceCacheDao.onSome { it.store(p) } }
            .flatMap { maybeRentalPriceCacheDao }
            .flatMap { it.get(ToolType.Jackhammer) }
            .isSome { p ->
              p.type == ToolType.Jackhammer &&
                  p.dailyPrice.toString() == "USD 2" &&
                  !p.weekdayCharge &&
                  p.weekendCharge &&
                  !p.holidayCharge
            } shouldBe true
      }
    })
