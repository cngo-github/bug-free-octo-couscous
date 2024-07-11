package org.example.service

import arrow.core.Option
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import org.example.org.example.persistence.cache.RedisCacheDao
import redis.embedded.RedisServer

class UsaHolidaysSuite :
    FunSpec({
      lateinit var redisServer: RedisServer
      val redisPort = 12345
      val redisUri: URI = URI.create("redis://localhost:$redisPort")

      lateinit var redisCacheDao: Option<RedisCacheDao>
      lateinit var maybeUsaHolidays: Option<UsaHolidays>

      beforeSpec {
        redisServer = RedisServer(redisPort)
        redisServer.start()
      }

      afterSpec { redisServer.stop() }

      beforeEach {
        redisCacheDao =
            RedisCacheDao(redisUri).onSome {
              it.set(
                  "holidays-2021",
                  "[{\"name\": \"Independence Day\", \"observedOn\": \"2021-01-01\"}]",
                  Duration.parse("5s"))
            }

        maybeUsaHolidays = redisCacheDao.flatMap { UsaHolidays.mkRedisService(redisUri) }
      }

      afterEach { redisCacheDao.onSome { it.cleanup() } }

      test("mkRedisService with a valid URI gives Some of UsaHolidays") {
        UsaHolidays.mkRedisService(redisUri).isSome() shouldBe true
      }

      test("mkRedisService with an invalid URI gives None") {
        UsaHolidays.mkRedisService(URI.create("redis://notReal")).isNone() shouldBe true
      }

      test("A valid holiday gives true") {
        maybeUsaHolidays.map { it.isHoliday(LocalDate.parse("2024-07-04")) }.isSome { it } shouldBe
            true
      }

      test("An invalid holiday gives false") {
        maybeUsaHolidays.map { it.isHoliday(LocalDate.parse("2021-07-04")) }.isSome { !it } shouldBe
            true
      }

      test("A cached holiday gives true") {
        maybeUsaHolidays.map { it.isHoliday(LocalDate.parse("2021-01-01")) }.isSome { it } shouldBe
            true
      }

      test("An expired cached holiday gives false") {
        Thread.sleep(Duration.parse("7s").toJavaDuration())

        maybeUsaHolidays.map { it.isHoliday(LocalDate.parse("2021-01-01")) }.isSome { it } shouldBe
            false
      }
    })
