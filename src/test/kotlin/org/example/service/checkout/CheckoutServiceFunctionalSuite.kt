package org.example.service.checkout

import arrow.core.Option
import arrow.core.flatMap
import arrow.core.nonEmptyListOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.sql.DriverManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import org.example.persistence.cache.RedisCacheDao
import org.example.persistence.db.SqliteDbDao
import org.example.persistence.enums.ToolBrand
import org.example.persistence.enums.ToolCode
import org.example.persistence.enums.ToolType
import org.example.service.formatter.currencyFormat
import org.example.service.formatter.shortDate
import org.example.service.data.UnknownError
import redis.embedded.RedisServer

class CheckoutServiceFunctionalSuite :
    FunSpec({
      val redisPort = 12345
      val redisUri: URI = URI.create("redis://localhost:$redisPort")
      val databaseUri: URI = URI.create("jdbc:sqlite:build/sample.db")
      val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/[yyyy][yy]")

      lateinit var redisServer: RedisServer
      lateinit var maybeRedisCacheDao: Option<RedisCacheDao>
      lateinit var maybeSqliteDbDao: Option<SqliteDbDao>
      lateinit var maybeCheckoutService: Option<CheckoutService>

      beforeSpec {
        redisServer = RedisServer(redisPort)
        redisServer.start()
      }

      afterSpec { redisServer.stop() }

      beforeEach {
        maybeRedisCacheDao = RedisCacheDao(redisUri)

        maybeSqliteDbDao =
            Option.catch { SqliteDbDao(DriverManager.getConnection(databaseUri.toString())) }
                .onSome {
                  it.update("DROP TABLE IF EXISTS prices")
                  it.update(
                      "CREATE TABLE prices (id integer, type string, dailyCharge string, weekdayCharge integer, weekendCharge integer, holidayCharge integer)")
                  it.update("INSERT INTO prices values(1, 'Ladder', 'USD 1.99', TRUE, TRUE, FALSE)")
                  it.update(
                      "INSERT INTO prices values(2, 'Chainsaw', 'USD 1.49', TRUE, FALSE, TRUE)")
                  it.update(
                      "INSERT INTO prices values(3, 'Jackhammer', 'USD 2.99', TRUE, FALSE, FALSE)")

                  it.update("DROP TABLE IF EXISTS tools")
                  it.update(
                      "CREATE TABLE tools (id integer, brand string, code string, type string, reservedBy string, reservedAt datetime, available integer)")
                  it.update(
                      "INSERT INTO tools values(1, 'Stihl', 'CHNS', 'Chainsaw', null, null, TRUE)")
                  it.update(
                      "INSERT INTO tools values(2, 'Werner', 'LADW', 'Ladder', null, null, TRUE)")
                  it.update(
                      "INSERT INTO tools values(3, 'DeWalt', 'JAKD', 'Jackhammer', null, null, TRUE)")
                  it.update(
                      "INSERT INTO tools values(4, 'Ridgid', 'JAKR', 'Jackhammer', null, null, TRUE)")
                }

        maybeCheckoutService = CheckoutService.mk(redisUri, databaseUri)
      }

      afterEach {
        maybeRedisCacheDao.onSome { it.cleanup() }
        maybeSqliteDbDao.onSome { it.cleanup() }
      }

      test("test 1") {
        val checkout = LocalDate.parse("9/3/15", dateFormatter)

        maybeCheckoutService
            .flatMap { it.reserve(ToolCode.JAKR) }
            .toEither { nonEmptyListOf(UnknownError) }
            .flatMap { r ->
              maybeCheckoutService
                  .toEither { nonEmptyListOf(UnknownError) }
                  .flatMap { it.checkout(r, checkout, Duration.parse("5d"), 101) }
            }
            .mapLeft { l -> l.map { it.message } }
            .isLeft {
              it.size == 1 && it.contains("The rental discount must be between 0 and 100.")
            } shouldBe true
      }

      test("test 2") {
        val checkout = LocalDate.parse("7/2/20", dateFormatter)

        maybeCheckoutService
            .flatMap { it.reserve(ToolCode.LADW) }
            .toEither { nonEmptyListOf(UnknownError) }
            .flatMap { r ->
              maybeCheckoutService
                  .toEither { nonEmptyListOf(UnknownError) }
                  .flatMap { it.checkout(r, checkout, Duration.parse("3d"), 10) }
            }
            .isRight {
              it.toolBrand == ToolBrand.Werner &&
                  it.toolCode == ToolCode.LADW &&
                  it.toolType == ToolType.Ladder &&
                  it.rentalDays == 3 &&
                  it.checkout.shortDate() == "07/02/20" &&
                  it.due.shortDate() == "07/05/20" &&
                  it.dailyCharge.currencyFormat() == "$1.99" &&
                  it.chargeDays == 2 &&
                  it.prediscountCharge.currencyFormat() == "$3.98" &&
                  it.discount == 10 &&
                  it.discountAmount.currencyFormat() == "$0.40" &&
                  it.finalCharge.currencyFormat() == "$3.58" &&
                  it.toString() ==
                      """Tool code: LADW
              |Tool type: Ladder
              |Tool brand: Werner
              |Rental days: 3
              |Checkout date: 07/02/20
              |Due date: 07/05/20
              |Daily charge: $1.99
              |Charge days: 2
              |Pre-discount charge: $3.98
              |Discount percent: 10%
              |Discount amount: $0.40
              |Final charge: $3.58
          """
                          .trimMargin()
            } shouldBe true
      }

      test("test 3") {
        val checkout = LocalDate.parse("7/2/15", dateFormatter)

        maybeCheckoutService
            .flatMap { it.reserve(ToolCode.CHNS) }
            .toEither { nonEmptyListOf(UnknownError) }
            .flatMap { r ->
              maybeCheckoutService
                  .toEither { nonEmptyListOf(UnknownError) }
                  .flatMap { it.checkout(r, checkout, Duration.parse("5d"), 25) }
            }
            .isRight {
              it.toolBrand == ToolBrand.Stihl &&
                  it.toolCode == ToolCode.CHNS &&
                  it.toolType == ToolType.Chainsaw &&
                  it.rentalDays == 5 &&
                  it.checkout.shortDate() == "07/02/15" &&
                  it.due.shortDate() == "07/07/15" &&
                  it.dailyCharge.currencyFormat() == "$1.49" &&
                  it.chargeDays == 3 &&
                  it.prediscountCharge.currencyFormat() == "$4.47" &&
                  it.discount == 25 &&
                  it.discountAmount.currencyFormat() == "$1.12" &&
                  it.finalCharge.currencyFormat() == "$3.35" &&
                  it.toString() ==
                      """Tool code: CHNS
              |Tool type: Chainsaw
              |Tool brand: Stihl
              |Rental days: 5
              |Checkout date: 07/02/15
              |Due date: 07/07/15
              |Daily charge: $1.49
              |Charge days: 3
              |Pre-discount charge: $4.47
              |Discount percent: 25%
              |Discount amount: $1.12
              |Final charge: $3.35
          """
                          .trimMargin()
            } shouldBe true
      }

      test("test 4") {
        val checkout = LocalDate.parse("9/3/15", dateFormatter)

        maybeCheckoutService
            .flatMap { it.reserve(ToolCode.JAKD) }
            .toEither { nonEmptyListOf(UnknownError) }
            .flatMap { r ->
              maybeCheckoutService
                  .toEither { nonEmptyListOf(UnknownError) }
                  .flatMap { it.checkout(r, checkout, Duration.parse("6d"), 0) }
            }
            .isRight {
              it.toolBrand == ToolBrand.DeWalt &&
                  it.toolCode == ToolCode.JAKD &&
                  it.toolType == ToolType.Jackhammer &&
                  it.rentalDays == 6 &&
                  it.checkout.shortDate() == "09/03/15" &&
                  it.due.shortDate() == "09/09/15" &&
                  it.dailyCharge.currencyFormat() == "$2.99" &&
                  it.chargeDays == 3 &&
                  it.prediscountCharge.currencyFormat() == "$8.97" &&
                  it.discount == 0 &&
                  it.discountAmount.currencyFormat() == "$0.00" &&
                  it.finalCharge.currencyFormat() == "$8.97" &&
                  it.toString() ==
                      """Tool code: JAKD
              |Tool type: Jackhammer
              |Tool brand: DeWalt
              |Rental days: 6
              |Checkout date: 09/03/15
              |Due date: 09/09/15
              |Daily charge: $2.99
              |Charge days: 3
              |Pre-discount charge: $8.97
              |Discount percent: 0%
              |Discount amount: $0.00
              |Final charge: $8.97
          """
                          .trimMargin()
            } shouldBe true
      }

      test("test 5") {
        val checkout = LocalDate.parse("7/2/15", dateFormatter)

        maybeCheckoutService
            .flatMap { it.reserve(ToolCode.JAKR) }
            .toEither { nonEmptyListOf(UnknownError) }
            .flatMap { r ->
              maybeCheckoutService
                  .toEither { nonEmptyListOf(UnknownError) }
                  .flatMap { it.checkout(r, checkout, Duration.parse("9d"), 0) }
            }
            .isRight {
              it.toolBrand == ToolBrand.Ridgid &&
                  it.toolCode == ToolCode.JAKR &&
                  it.toolType == ToolType.Jackhammer &&
                  it.rentalDays == 9 &&
                  it.checkout.shortDate() == "07/02/15" &&
                  it.due.shortDate() == "07/11/15" &&
                  it.dailyCharge.currencyFormat() == "$2.99" &&
                  it.chargeDays == 5 &&
                  it.prediscountCharge.currencyFormat() == "$14.95" &&
                  it.discount == 0 &&
                  it.discountAmount.currencyFormat() == "$0.00" &&
                  it.finalCharge.currencyFormat() == "$14.95" &&
                  it.toString() ==
                      """Tool code: JAKR
              |Tool type: Jackhammer
              |Tool brand: Ridgid
              |Rental days: 9
              |Checkout date: 07/02/15
              |Due date: 07/11/15
              |Daily charge: $2.99
              |Charge days: 5
              |Pre-discount charge: $14.95
              |Discount percent: 0%
              |Discount amount: $0.00
              |Final charge: $14.95
          """
                          .trimMargin()
            } shouldBe true
      }

      test("test 6") {
        val checkout = LocalDate.parse("7/2/20", dateFormatter)

        maybeCheckoutService
            .flatMap { it.reserve(ToolCode.JAKR) }
            .toEither { nonEmptyListOf(UnknownError) }
            .flatMap { r ->
              maybeCheckoutService
                  .toEither { nonEmptyListOf(UnknownError) }
                  .flatMap { it.checkout(r, checkout, Duration.parse("4d"), 50) }
            }
            .isRight {
              it.toolBrand == ToolBrand.Ridgid &&
                  it.toolCode == ToolCode.JAKR &&
                  it.toolType == ToolType.Jackhammer &&
                  it.rentalDays == 4 &&
                  it.checkout.shortDate() == "07/02/20" &&
                  it.due.shortDate() == "07/06/20" &&
                  it.dailyCharge.currencyFormat() == "$2.99" &&
                  it.chargeDays == 1 &&
                  it.prediscountCharge.currencyFormat() == "$2.99" &&
                  it.discount == 50 &&
                  it.discountAmount.currencyFormat() == "$1.50" &&
                  it.finalCharge.currencyFormat() == "$1.49" &&
                  it.toString() ==
                      """Tool code: JAKR
              |Tool type: Jackhammer
              |Tool brand: Ridgid
              |Rental days: 4
              |Checkout date: 07/02/20
              |Due date: 07/06/20
              |Daily charge: $2.99
              |Charge days: 1
              |Pre-discount charge: $2.99
              |Discount percent: 50%
              |Discount amount: $1.50
              |Final charge: $1.49
          """
                          .trimMargin()
            } shouldBe true
      }
    })
