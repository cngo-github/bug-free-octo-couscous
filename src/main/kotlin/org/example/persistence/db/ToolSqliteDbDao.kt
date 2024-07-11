package org.example.persistence.db

import arrow.core.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.time.LocalDateTime
import org.example.org.example.persistence.data.RentalPrice
import org.example.org.example.persistence.data.ReservationId
import org.example.org.example.persistence.data.Tool
import org.example.org.example.persistence.db.SqliteDbDao
import org.example.org.example.persistence.db.domain.ToolDBDao
import org.example.org.example.persistence.enums.ToolCode
import org.example.org.example.persistence.enums.ToolType

class ToolSqliteDbDao private constructor(private val sqliteDbDao: SqliteDbDao) : ToolDBDao {
  override fun get(toolCode: ToolCode): Option<Tool> {
    val query = "SELECT brand, code, type FROM tools WHERE code = '${toolCode.name}'"
    LOGGER.trace { "Getting the tool ${toolCode.name}" }

    return sqliteDbDao
        .query(query)
        .map {
          generateSequence {
            if (it.next()) {
              Tool.from(it.getString("brand"), it.getString("code"), it.getString("type"))
            } else null
          }
        }
        .map { s -> s.map { it.getOrNull() }.filterNotNull().toList() }
        .flatMap {
          when {
            it.size == 1 -> Some(it.first())
            else -> None
          }
        }
        .onNone { LOGGER.error { "Failed to get the tool ${toolCode.name}" } }
  }

  override fun get(toolType: ToolType): Option<RentalPrice> {
    val query =
        "SELECT type, dailyCharge, weekdayCharge, weekendCharge, holidayCharge FROM prices WHERE type = '${toolType.name}'"
    LOGGER.trace { "Getting the rental price for tool ${toolType.name}" }

    return sqliteDbDao
        .query(query)
        .map {
          generateSequence {
            if (it.next()) {
              RentalPrice.from(
                  it.getString("type"),
                  it.getString("dailyCharge"),
                  it.getBoolean("weekdayCharge"),
                  it.getBoolean("weekendCharge"),
                  it.getBoolean("holidayCharge"))
            } else null
          }
        }
        .map { s -> s.map { it.getOrNull() }.filterNotNull().toList() }
        .flatMap {
          when {
            it.size == 1 -> Some(it.first())
            else -> None
          }
        }
        .onNone { LOGGER.error { "Failed to get the rental price for tool ${toolType.name}" } }
  }

  override fun reserve(toolCode: ToolCode): Option<ReservationId> {
    val reservationId = ReservationId.generate()
    val query =
        "UPDATE tools SET reservedBy = '${reservationId.id}', reservedAt = '${LocalDateTime.now()}' WHERE id = (SELECT id FROM tools WHERE (reservedBy is null AND `code` = '$toolCode' AND `available` = 1) LIMIT 1)"
    LOGGER.trace { "Reserving tool ${toolCode.name}" }

    return sqliteDbDao
        .update(query)
        .toEither { UnknownError }
        .flatMap {
          when {
            it == 1 -> Either.Right(reservationId)
            it == 0 -> Either.Left(ToolReservationFailed("Failed to reserve the tool $toolCode"))
            else ->
                Either.Left(
                    InvalidDatabaseState(
                        "The database may be in an invalid state after reserving tool $toolCode."))
          }
        }
        .onLeft { LOGGER.error(it) { "Failed to reserve the tool $toolCode" } }
        .getOrNone()
  }

  override fun checkout(reservationId: ReservationId, toolType: ToolType): Option<ReservationId> {
    val query =
        "UPDATE tools SET available = FALSE WHERE id = (SELECT id FROM tools WHERE (reservedBy LIKE '${reservationId.id}' AND `type` LIKE '${toolType.name}'))"

    return sqliteDbDao
        .update(query)
        .toEither { UnknownError }
        .flatMap {
          when {
            it == 1 -> Either.Right(reservationId)
            it == 0 ->
                Either.Left(
                    ToolCheckoutFailed(
                        "Failed to checkout reservation ${reservationId.id} for tool ${toolType.name}."))
            else ->
                Either.Left(
                    InvalidDatabaseState(
                        "The database may be in an invalid state after checking out reservation ${reservationId.id} for tool ${toolType.name}."))
          }
        }
        .onLeft {
          LOGGER.error(it) {
            "Failed to checkout reservation ${reservationId.id} for tool ${toolType.name}."
          }
        }
        .getOrNone()
  }

  override fun cleanup() = sqliteDbDao.cleanup()

  companion object {
    private val LOGGER = KotlinLogging.logger {}

    fun mk(uri: URI): Option<ToolSqliteDbDao> {
      return SqliteDbDao.mk(uri)
          .map { ToolSqliteDbDao(it) }
          .onNone { "Failed to create the ${ToolSqliteDbDao::class.java} object." }
    }
  }
}

data class InvalidDatabaseState(override val message: String) : Throwable(message)

data class ToolCheckoutFailed(override val message: String) : Throwable(message)

data class ToolReservationFailed(override val message: String) : Throwable(message)

object UnknownError : Throwable("There was an unknown error.") {
  private fun readResolve(): Any = UnknownError
}
