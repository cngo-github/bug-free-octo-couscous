package org.example.service.data

import arrow.core.Option
import arrow.core.nonEmptyListOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.time.Duration
import org.example.org.example.persistence.cache.RedisCacheDao
import org.example.org.example.persistence.data.Tool
import org.example.org.example.persistence.db.SqliteDbDao
import org.example.org.example.persistence.enums.ToolBrand
import org.example.org.example.persistence.enums.ToolCode
import org.example.org.example.persistence.enums.ToolType
import org.example.org.example.service.data.*
import redis.embedded.RedisServer

class RentalAgreementSuite :
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
                      "INSERT INTO tools values(1, 'Werner', 'LADW', 'Ladder', null, null, TRUE)")
                }
      }

      afterEach {
        maybeRedisCacheDao.onSome { it.cleanup() }
        maybeSqliteDbDao.onSome { it.cleanup() }
      }

      test("valid rental agreement gives Right of Rental Agreement") {
        RentalAgreement.mkUsa(
                redisUri,
                databaseUri,
                Tool(ToolBrand.Werner, ToolCode.LADW, ToolType.Ladder),
                LocalDate.now(),
                Duration.parse("3d"),
                1)
            .isRight { it.rentalDays == 3 && it.discount == 1 } shouldBe true
      }

      test(
          "invalid Redis URI gives Left of NonEmptyList of InvalidRentalDuration, FailedToGetRentalPrice, InvalidTool") {
            RentalAgreement.mkUsa(
                    URI.create("redis://notReal"),
                    databaseUri,
                    Tool(ToolBrand.Werner, ToolCode.LADW, ToolType.Ladder),
                    LocalDate.now(),
                    Duration.parse("3d"),
                    1)
                .isLeft {
                  it.size == 3 &&
                      it.containsAll(
                          listOf(
                              InvalidRentalDuration,
                              FailedToGetRentalPrice(ToolType.Ladder),
                              InvalidTool))
                } shouldBe true
          }

      test("invalid Sqlite URI gives Left of NonEmptyList of FailedToGetRentalPrice, InvalidTool") {
        RentalAgreement.mkUsa(
                redisUri,
                URI.create("jdbc:sqlite:notReal/sample.db"),
                Tool(ToolBrand.Werner, ToolCode.LADW, ToolType.Ladder),
                LocalDate.now(),
                Duration.parse("3d"),
                1)
            .isLeft {
              it.size == 2 &&
                  it.containsAll(listOf(FailedToGetRentalPrice(ToolType.Ladder), InvalidTool))
            } shouldBe true
      }

      test("invalid tool gives Left of NonEmptyList of InvalidTool") {
        RentalAgreement.mkUsa(
                redisUri,
                databaseUri,
                Tool(ToolBrand.Werner, ToolCode.LADW, ToolType.Chainsaw),
                LocalDate.now(),
                Duration.parse("3d"),
                1)
            .isLeft { it.size == 1 && it.containsAll(listOf(InvalidTool)) } shouldBe true
      }

      test("invalid rental duration gives Left of NonEmptyList of InvalidRentalDuration") {
        RentalAgreement.mkUsa(
                redisUri,
                databaseUri,
                Tool(ToolBrand.Werner, ToolCode.LADW, ToolType.Ladder),
                LocalDate.now(),
                Duration.parse("3m"),
                1)
            .isLeft { it.size == 1 && it.containsAll(listOf(InvalidRentalDuration)) } shouldBe true
      }

      test("invalid discount gives Left of NonEmptyList of InvalidRentalDiscount") {
        RentalAgreement.mkUsa(
                redisUri,
                databaseUri,
                Tool(ToolBrand.Werner, ToolCode.LADW, ToolType.Ladder),
                LocalDate.now(),
                Duration.parse("3d"),
                -1)
            .isLeft { it.size == 1 && it.containsAll(listOf(InvalidRentalDiscount)) } shouldBe true
      }

      test(
          "multiple validation errors gives Left of NonEmptyList of RentalAgreementValidationError") {
            RentalAgreement.mkUsa(
                    redisUri,
                    databaseUri,
                    Tool(ToolBrand.Werner, ToolCode.LADW, ToolType.Ladder),
                    LocalDate.now(),
                    Duration.parse("3m"),
                    -1)
                .isLeft { it.size == 2 } shouldBe true
          }

      test("validation errors have user-friendly messages") {
        RentalAgreement.mkUsa(
                redisUri,
                databaseUri,
                Tool(ToolBrand.Werner, ToolCode.LADW, ToolType.Ladder),
                LocalDate.now(),
                Duration.parse("3d"),
                -1)
            .mapLeft { l -> l.map { it.message } }
            .isLeft {
              it == nonEmptyListOf("The rental discount must be between 0 and 100.")
            } shouldBe true
      }
    })
