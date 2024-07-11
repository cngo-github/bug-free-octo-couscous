package org.example.persistence.cache

import arrow.core.Option
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.time.Year
import kotlin.time.Duration
import org.example.org.example.persistence.cache.HolidaysCacheDao
import org.example.org.example.persistence.cache.RedisCacheDao
import org.example.org.example.persistence.data.Holiday
import redis.embedded.RedisServer

class HolidayCacheDaoSuite :
    FunSpec({
      lateinit var redisServer: RedisServer
      val redisPort = 12345
      val redisUri: URI = URI.create("redis://localhost:$redisPort")

      lateinit var maybeHolidaysCacheDao: Option<HolidaysCacheDao>

      beforeSpec {
        redisServer = RedisServer(redisPort)
        redisServer.start()
      }

      afterSpec { redisServer.stop() }

      beforeEach {
        maybeHolidaysCacheDao =
            RedisCacheDao(redisUri)
                .onSome {
                  it.set(
                      "holidays-2021",
                      "[{\"name\": \"test\", \"observedOn\": \"2021-01-01\"}]",
                      Duration.parse("5m"))
                }
                .flatMap { HolidaysCacheDao.mkRedisDao(redisUri) }
      }

      afterEach { maybeHolidaysCacheDao.onSome { it.cleanup() } }

      test("mkRedisDao with valid URI gives Some of HolidayCacheDao") {
        HolidaysCacheDao.mkRedisDao(redisUri).isSome() shouldBe true
      }

      test("mkRedisDao with invalid URI gives None") {
        HolidaysCacheDao.mkRedisDao(URI.create("redis://notReal")).isNone() shouldBe true
      }

      test("Get populated holidays returns Some of List of Holiday") {
        maybeHolidaysCacheDao.flatMap { it.get(Year.of(2021)) }.isSome { l -> l.size == 1 } shouldBe
            true
      }

      test("Get unpopulated holidays returns None") {
        maybeHolidaysCacheDao.flatMap { it.get(Year.of(2022)) }.isNone() shouldBe true
      }

      test("Store new holidays") {
        maybeHolidaysCacheDao
            .onSome { it.store(Year.of(2023), Holiday.from("new", "2023-01-01").toList()) }
            .flatMap { it.get(Year.of(2023)) }
            .isSome { l ->
              l.size == 1 &&
                  l.first().name == "new" &&
                  l.first().observedOn.toString() == "2023-01-01"
            } shouldBe true
      }
    })
