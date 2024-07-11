package org.example.service

import arrow.core.Option
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.sql.DriverManager
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import org.example.org.example.persistence.cache.RedisCacheDao
import org.example.org.example.persistence.db.SqliteDbDao
import org.example.org.example.persistence.enums.ToolBrand
import org.example.org.example.persistence.enums.ToolCode
import org.example.org.example.persistence.enums.ToolType
import org.example.org.example.service.UsaRentalValidate
import org.example.persistence.data.Tool
import redis.embedded.RedisServer

class UsaRentalValidateSuite :
    FunSpec({
      val redisPort = 12345
      val redisUri: URI = URI.create("redis://localhost:$redisPort")
      val databaseUri: URI = URI.create("jdbc:sqlite:build/sample.db")

      lateinit var redisServer: RedisServer
      lateinit var maybeRedisCacheDao: Option<RedisCacheDao>
      lateinit var maybeSqliteDbDao: Option<SqliteDbDao>
      lateinit var maybeUsaRentalValidate: Option<UsaRentalValidate>

      beforeSpec {
        redisServer = RedisServer(redisPort)
        redisServer.start()
      }

      afterSpec { redisServer.stop() }

      beforeEach {
        maybeRedisCacheDao =
            RedisCacheDao(redisUri).onSome {
              it.set(
                  "tool-CHNS",
                  "{\"toolBrand\":\"Stihl\",\"toolCode\":\"CHNS\",\"toolType\":\"Chainsaw\"}",
                  Duration.parse("5s"))
              it.set(
                  "rentalPrice-Chainsaw",
                  "{\"type\": \"Chainsaw\", \"dailyPrice\": \"USD 2\", \"weekdayCharge\": \"true\", \"weekendCharge\": \"false\", \"holidayCharge\": \"true\"}",
                  Duration.parse("5s"))
            }

        maybeSqliteDbDao =
            Option.catch { SqliteDbDao(DriverManager.getConnection(databaseUri.toString())) }
                .onSome {
                  it.update("DROP TABLE IF EXISTS prices")
                  it.update(
                      "CREATE TABLE prices (id integer, type string, dailyCharge string, weekdayCharge integer, weekendCharge integer, holidayCharge integer)")
                  it.update("INSERT INTO prices values(1, 'Ladder', 'USD 1.99', TRUE, TRUE, FALSE)")

                  it.update("DROP TABLE IF EXISTS tools")
                  it.update(
                      "CREATE TABLE tools (id integer, brand string, code string, type string, reservedBy string, reservedAt datetime, available integer)")
                  it.update(
                      "INSERT INTO tools values(1, 'Werner', 'JAKR', 'Jackhammer', null, null, TRUE)")
                }

        maybeUsaRentalValidate = UsaRentalValidate.mk(redisUri, databaseUri)
      }

      afterEach {
        maybeRedisCacheDao.onSome { it.cleanup() }
        maybeSqliteDbDao.onSome { it.cleanup() }
      }

      test("valid URIs gives Some of UsaRentalValidate") {
        UsaRentalValidate.mk(redisUri, databaseUri).isSome() shouldBe true
      }

      test("invalid Redis URI gives None") {
        UsaRentalValidate.mk(URI.create("redis://notReal"), databaseUri).isNone() shouldBe true
      }

      test("invalid database URI gives None") {
        UsaRentalValidate.mk(redisUri, URI.create("jdbc:sqlite:notReal/sample.db"))
            .isNone() shouldBe true
      }

      test("get cached tool gives Some of Tool") {
        maybeUsaRentalValidate
            .flatMap { it.get(ToolCode.CHNS) }
            .isSome {
              it.toolBrand == ToolBrand.Stihl &&
                  it.toolCode == ToolCode.CHNS &&
                  it.toolType == ToolType.Chainsaw
            } shouldBe true
      }

      test("get un-cached tool in database gives Some of Tool") {
        maybeUsaRentalValidate
            .flatMap { it.get(ToolCode.JAKR) }
            .isSome {
              it.toolBrand == ToolBrand.Werner &&
                  it.toolCode == ToolCode.JAKR &&
                  it.toolType == ToolType.Jackhammer
            } shouldBe true
      }

      test("get un-cached tool not in database gives None") {
        maybeUsaRentalValidate.flatMap { it.get(ToolCode.JAKD) }.isNone() shouldBe true
      }

      test("get expired cached tool gives None") {
        Thread.sleep(Duration.parse("7s").toJavaDuration())

        maybeUsaRentalValidate.flatMap { it.get(ToolCode.CHNS) }.isNone() shouldBe true
      }

      test("get cached rental price gives Some of RentalPrice") {
        maybeUsaRentalValidate
            .flatMap { it.get(ToolType.Chainsaw) }
            .isSome {
              it.type == ToolType.Chainsaw &&
                  it.dailyPrice.toString() == "USD 2" &&
                  it.weekdayCharge &&
                  !it.weekendCharge &&
                  it.holidayCharge
            } shouldBe true
      }

      test("get un-cached rental price in the database gives Some of RentalPrice") {
        maybeUsaRentalValidate
            .flatMap { it.get(ToolType.Ladder) }
            .isSome {
              it.type == ToolType.Ladder &&
                  it.dailyPrice.toString() == "USD 1.99" &&
                  it.weekdayCharge &&
                  it.weekendCharge &&
                  !it.holidayCharge
            } shouldBe true
      }

      test("get un-cached rental price not in the database gives None") {
        maybeUsaRentalValidate.flatMap { it.get(ToolType.Jackhammer) }.isNone() shouldBe true
      }

      test("get expired cached rental price gives None") {
        Thread.sleep(Duration.parse("7s").toJavaDuration())

        maybeUsaRentalValidate.flatMap { it.get(ToolType.Chainsaw) }.isNone() shouldBe true
      }

      test("valid cached tool returns true") {
        val t = Tool(ToolBrand.Stihl, ToolCode.CHNS, ToolType.Chainsaw)

        maybeUsaRentalValidate.map { it.isValid(t) }.isSome { it } shouldBe true
      }

      test("valid un-cached tool in database returns true") {
        val t = Tool(ToolBrand.Werner, ToolCode.JAKR, ToolType.Jackhammer)

        maybeUsaRentalValidate.map { it.isValid(t) }.isSome { it } shouldBe true
      }

      test("invalid tool returns false") {
        val t = Tool(ToolBrand.DeWalt, ToolCode.JAKR, ToolType.Jackhammer)

        maybeUsaRentalValidate.map { it.isValid(t) }.isSome { it } shouldBe false
      }
    })
