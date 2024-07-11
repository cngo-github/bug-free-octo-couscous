package org.example.org.example.service.checkout

import arrow.core.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.time.LocalDate
import kotlin.time.Duration
import org.example.org.example.persistence.db.domain.ToolDBDao
import org.example.org.example.persistence.enums.ToolCode
import org.example.org.example.service.UsaRentalValidate
import org.example.org.example.service.data.FailedToCheckout
import org.example.org.example.service.data.RentalAgreement
import org.example.org.example.service.data.RentalAgreementValidationError
import org.example.org.example.service.data.Reservation
import org.example.org.example.service.domain.RentalValidate
import org.example.persistence.db.ToolSqliteDbDao

class CheckoutService(
    private val toolDbDao: ToolDBDao,
    private val rentalValidate: RentalValidate,
    private val cacheUri: URI,
    private val dbUri: URI
) {
  fun reserve(toolCode: ToolCode): Option<Reservation> {
    val maybeTool = rentalValidate.get(toolCode)

    return toolDbDao
        .reserve(toolCode)
        .flatMap { id -> maybeTool.map { Pair(id, it) } }
        .flatMap { (reservationId, tool) ->
          maybeTool
              .flatMap { t -> rentalValidate.get(t.toolType) }
              .map { Reservation(reservationId, tool, it) }
        }
        .onNone { LOGGER.error { "Failed to reserve ${toolCode.name}." } }
  }

  fun checkout(
      reservation: Reservation,
      checkout: LocalDate,
      rentalDuration: Duration,
      discount: Int
  ): Either<NonEmptyList<RentalAgreementValidationError>, RentalAgreement> =
      RentalAgreement.mkUsa(cacheUri, dbUri, reservation.tool, checkout, rentalDuration, discount)
          .flatMap { r ->
            toolDbDao
                .checkout(reservation.id, reservation.tool.toolType)
                .toEither {
                  nonEmptyListOf(FailedToCheckout(reservation.id, reservation.tool.toolType))
                }
                .map { r }
          }

  companion object {
    private val LOGGER = KotlinLogging.logger {}

    fun mk(cacheUri: URI, dbUri: URI): Option<CheckoutService> =
        ToolSqliteDbDao.mk(dbUri)
            .flatMap { db ->
              UsaRentalValidate.mk(cacheUri, dbUri).map { CheckoutService(db, it, cacheUri, dbUri) }
            }
            .onNone {
              LOGGER.error { "Failed to create the ${CheckoutService::class.java} object." }
            }
  }
}
