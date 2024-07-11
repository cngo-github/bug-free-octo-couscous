package org.example.service.checkout

import arrow.core.Option
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.time.Duration
import org.example.persistence.cache.RedisCacheDao
import org.example.persistence.db.SqliteDbDao
import org.example.persistence.enums.ToolBrand
import org.example.persistence.enums.ToolCode
import org.example.persistence.enums.ToolType
import org.example.persistence.data.RentalPrice
import org.example.persistence.data.ReservationId
import org.example.persistence.data.Tool
import org.example.service.data.Reservation
import org.javamoney.moneta.Money
import redis.embedded.RedisServer

class CheckoutServiceSuite :
    FunSpec({
      val redisPort = 12345
      val redisUri: URI = URI.create("redis://localhost:$redisPort")
      val databaseUri: URI = URI.create("jdbc:sqlite:build/sample.db")

      lateinit var redisServer: RedisServer
      lateinit var maybeRedisCacheDao: Option<RedisCacheDao>
      lateinit var maybeSqliteDbDao: Option<SqliteDbDao>

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
                      "INSERT INTO tools values(1, 'Werner', 'JAKR', 'Ladder', null, null, TRUE)")
                  it.update(
                      "INSERT INTO tools values(2, 'Stihl', 'JAKD', 'Jackhammer', null, null, TRUE)")
                }
      }

      afterEach {
        maybeRedisCacheDao.onSome { it.cleanup() }
        maybeSqliteDbDao.onSome { it.cleanup() }
      }

      test("invalid cache URI gives None") {
        CheckoutService.mk(URI.create("redis://notReal"), databaseUri).isNone() shouldBe true
      }

      test("invalid database URI gives None") {
        CheckoutService.mk(redisUri, URI.create("jdbc:sqlite:notReal/sample.db")).isNone() shouldBe
            true
      }

      test("valid URIs gives a Some of CheckoutService") {
        CheckoutService.mk(redisUri, databaseUri).isSome() shouldBe true
      }

      test("successful reservation gives Some of ReservationId") {
        CheckoutService.mk(redisUri, databaseUri)
            .flatMap { it.reserve(ToolCode.JAKR) }
            .isSome {
              it.tool.toolBrand == ToolBrand.Werner &&
                  it.tool.toolCode == ToolCode.JAKR &&
                  it.tool.toolType == ToolType.Ladder &&
                  it.id.id.isNotBlank() &&
                  it.rentalPrice.dailyPrice.toString() == "USD 1.99"
            } shouldBe true
      }

      test("no rental price fails reservation and gives None") {
        CheckoutService.mk(redisUri, databaseUri)
            .flatMap { it.reserve(ToolCode.JAKD) }
            .isNone() shouldBe true
      }

      test("no tool fails reservation and gives None") {
        CheckoutService.mk(redisUri, databaseUri)
            .flatMap { it.reserve(ToolCode.LADW) }
            .isNone() shouldBe true
      }

      test("invalid reservation fails checkout and gives None") {
        val service = CheckoutService.mk(redisUri, databaseUri)

        service
            .flatMap { it.reserve(ToolCode.JAKR) }
            .flatMap {
              service.flatMap {
                it.checkout(
                        Reservation(
                            ReservationId.generate(),
                            Tool(ToolBrand.Werner, ToolCode.JAKR, ToolType.Ladder),
                            RentalPrice(
                                ToolType.Ladder,
                                Money.parse("USD 1.99"),
                                weekdayCharge = true,
                                weekendCharge = true,
                                holidayCharge = false)),
                        LocalDate.now(),
                        Duration.parse("3d"),
                        1)
                    .getOrNone()
              }
            }
            .isNone() shouldBe true
      }

      test("successful checkout gives Some of RentalAgreement") {
        val service = CheckoutService.mk(redisUri, databaseUri)

        service
            .flatMap { it.reserve(ToolCode.JAKR) }
            .flatMap { r ->
              service.flatMap {
                it.checkout(r, LocalDate.now(), Duration.parse("3d"), 1).getOrNone()
              }
            }
            .isSome() shouldBe true
      }

      test("invalid RentalAgreement fails checkout and gives None") {
        val service = CheckoutService.mk(redisUri, databaseUri)

        service
            .flatMap { it.reserve(ToolCode.JAKR) }
            .flatMap { r ->
              service.flatMap {
                it.checkout(r, LocalDate.now(), Duration.parse("3d"), -1).getOrNone()
              }
            }
            .isNone() shouldBe true
      }
    })
