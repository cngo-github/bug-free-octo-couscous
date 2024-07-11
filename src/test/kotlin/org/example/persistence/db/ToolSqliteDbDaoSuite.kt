package org.example.persistence.db

import arrow.core.Option
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.sql.DriverManager
import org.example.persistence.enums.ToolBrand
import org.example.persistence.enums.ToolCode
import org.example.persistence.enums.ToolType

class ToolSqliteDbDaoSuite :
    FunSpec({
      val databaseUri: URI = URI.create("jdbc:sqlite:build/sample.db")
      lateinit var maybeToolSqliteDbDao: Option<ToolSqliteDbDao>

      beforeEach {
        maybeToolSqliteDbDao =
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
                      "INSERT INTO tools values(1, 'Stihl', 'CHNS', 'Chainsaw', null, null, TRUE)")
                }
                .flatMap { ToolSqliteDbDao.mk(databaseUri) }
      }

      afterEach { maybeToolSqliteDbDao.onSome { it.cleanup() } }

      test("mk with valid URI gives Some of ToolSqliteDbDao") {
        ToolSqliteDbDao.mk(databaseUri).isSome() shouldBe true
      }

      test("mk with a invalid URI gives None") {
        ToolSqliteDbDao.mk((URI.create("jdbc:sqlite:notReal/sample.db"))).isNone() shouldBe true
      }

      test("get valid tool code gives Some of Tool") {
        maybeToolSqliteDbDao
            .flatMap { it.get(ToolCode.CHNS) }
            .isSome {
              it.toolCode == ToolCode.CHNS &&
                  it.toolBrand == ToolBrand.Stihl &&
                  it.toolType == ToolType.Chainsaw
            } shouldBe true
      }

      test("get invalid tool code gives None") {
        maybeToolSqliteDbDao.flatMap { it.get(ToolCode.JAKR) }.isNone() shouldBe true
      }

      test("get valid tool type gives Some of RentalPrice") {
        maybeToolSqliteDbDao
            .flatMap { it.get(ToolType.Ladder) }
            .isSome {
              it.type == ToolType.Ladder &&
                  it.dailyPrice.toString() == "USD 1.99" &&
                  it.weekdayCharge &&
                  it.weekendCharge &&
                  !it.holidayCharge
            } shouldBe true
      }

      test("reserve valid tool code gives Some of ReservationId") {
        maybeToolSqliteDbDao
            .flatMap { it.reserve(ToolCode.CHNS) }
            .isSome { it.id.isNotBlank() } shouldBe true
      }

      test("reserve invalid tool code gives None") {
        maybeToolSqliteDbDao.flatMap { it.reserve(ToolCode.JAKR) }.isNone() shouldBe true
      }

      test("checkout valid reservation gives Some of ReservationId") {
        maybeToolSqliteDbDao
            .flatMap { it.reserve(ToolCode.CHNS) }
            .flatMap { r -> maybeToolSqliteDbDao.flatMap { it.checkout(r, ToolType.Chainsaw) } }
            .isSome { it.id.isNotBlank() } shouldBe true
      }

      test("checkout unreserved tool type gives None") {
        maybeToolSqliteDbDao
            .flatMap { it.reserve(ToolCode.CHNS) }
            .flatMap { r -> maybeToolSqliteDbDao.flatMap { it.checkout(r, ToolType.Ladder) } }
            .isNone() shouldBe true
      }

      test("checkout invalid reservation gives None") {
        maybeToolSqliteDbDao
            .flatMap { it.reserve(ToolCode.LADW) }
            .flatMap { r -> maybeToolSqliteDbDao.flatMap { it.checkout(r, ToolType.Chainsaw) } }
            .isNone() shouldBe true
      }
    })
