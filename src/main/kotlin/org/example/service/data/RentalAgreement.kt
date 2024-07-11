package org.example.org.example.service.data

import arrow.core.*
import arrow.core.raise.*
import java.math.RoundingMode
import java.net.URI
import java.time.LocalDate
import javax.money.MonetaryContextBuilder
import kotlin.time.Duration
import org.example.org.example.persistence.data.RentalPrice
import org.example.org.example.persistence.data.ReservationId
import org.example.org.example.persistence.data.Tool
import org.example.org.example.persistence.enums.ToolType
import org.example.org.example.service.UsaRentalValidate
import org.example.org.example.service.enums.CurrencyDenomination
import org.example.org.example.service.formatter.currencyFormat
import org.example.org.example.service.formatter.shortDate
import org.javamoney.moneta.Money

data class RentalAgreement
private constructor(
    private val tool: Tool,
    val checkout: LocalDate,
    val due: LocalDate,
    private val weekdays: List<LocalDate>,
    private val weekends: List<LocalDate>,
    private val holidays: List<LocalDate>,
    private val rentalPrice: RentalPrice,
    val discount: Int
) {
  private val chargeableWeekdays = if (rentalPrice.weekdayCharge) weekdays else emptyList()
  private val chargeableWeekends = if (rentalPrice.weekendCharge) weekends else emptyList()
  private val unchargeableHolidays = if (rentalPrice.holidayCharge) emptyList() else holidays

  val toolBrand = tool.toolBrand
  val toolCode = tool.toolCode
  val toolType = tool.toolType

  val rentalDays: Int = weekdays.size + weekends.size
  val chargeDays: Int =
      (chargeableWeekdays + chargeableWeekends).filter { !unchargeableHolidays.contains(it) }.size

  val dailyCharge: Money = rentalPrice.dailyPrice
  val prediscountCharge: Money = dailyCharge.multiply(chargeDays)
  val discountAmount: Money =
      REDUCED_PRECISION_MONEY.add(prediscountCharge).multiply(discount).divide(100)
  val finalCharge: Money =
      REDUCED_PRECISION_MONEY.add(discountAmount).negate().add(prediscountCharge)

  override fun toString(): String {
    return """Tool code: ${tool.toolCode}
        |Tool type: ${tool.toolType}
        |Tool brand: ${tool.toolBrand}
        |Rental days: $rentalDays
        |Checkout date: ${checkout.shortDate()}
        |Due date: ${due.shortDate()}
        |Daily charge: ${dailyCharge.currencyFormat()}
        |Charge days: $chargeDays
        |Pre-discount charge: ${prediscountCharge.currencyFormat()}
        |Discount percent: $discount%
        |Discount amount: ${discountAmount.currencyFormat()}
        |Final charge: ${finalCharge.currencyFormat()}"""
        .trimMargin()
  }

  companion object {
    val REDUCED_PRECISION_MONEY =
        Money.of(
            0,
            CurrencyDenomination.USD.name,
            MonetaryContextBuilder.of(Money::class.java)
                .set("java.lang.class", Money::class.java)
                .setPrecision(3)
                .set("java.math.RoundingMode", RoundingMode.HALF_UP)
                .build())

    fun mkUsa(
        redisUri: URI,
        sqliteUri: URI,
        tool: Tool,
        checkout: LocalDate,
        rentalDuration: Duration,
        rentalDiscount: Int
    ): Either<NonEmptyList<RentalAgreementValidationError>, RentalAgreement> {
      fun Raise<RentalAgreementValidationError>.validateDates(): RentalDates {
        ensure(rentalDuration.inWholeDays >= 1) { InvalidRentalDuration }

        val dates = RentalDates.mkUsa(redisUri, checkout, rentalDuration).getOrNull()

        ensureNotNull(dates) { FailedToGetRentalDates(checkout, rentalDuration) }
        ensure(
            rentalDuration.inWholeDays.compareTo(dates.weekdays.size + dates.weekends.size) == 0) {
              InvalidRentalDuration
            }

        return dates
      }

      fun Raise<InvalidTool>.validateTool(): Tool {
        ensure(
            UsaRentalValidate.mk(redisUri, sqliteUri)
                .map { it.isValid(tool) }
                .getOrElse { false }) {
              InvalidTool
            }

        return tool
      }

      fun Raise<FailedToGetRentalPrice>.validatePrice(): RentalPrice {
        val price =
            UsaRentalValidate.mk(redisUri, sqliteUri).flatMap { it.get(tool.toolType) }.getOrNull()

        ensureNotNull(price) { FailedToGetRentalPrice(tool.toolType) }

        return price
      }

      fun Raise<InvalidRentalDiscount>.validateDiscount(): Int {
        ensure(rentalDiscount in 0..100) { InvalidRentalDiscount }

        return rentalDiscount
      }

      return either {
        zipOrAccumulate(
            { validatePrice() }, { validateDates() }, { validateTool() }, { validateDiscount() }) {
                price,
                dates,
                tool,
                discount ->
              RentalAgreement(
                  tool,
                  checkout,
                  dates.due,
                  dates.weekdays,
                  dates.weekends,
                  dates.holidays,
                  price,
                  discount)
            }
      }
    }
  }
}

sealed interface RentalAgreementValidationError {
  val message: String
}

data class FailedToGetRentalDates(val checkout: LocalDate, val duration: Duration) :
    RentalAgreementValidationError {
  override val message =
      "Failed to get the rental dates for ${duration.inWholeDays} days from $checkout."
}

data class FailedToGetRentalPrice(val toolType: ToolType) : RentalAgreementValidationError {
  override val message = "Failed to get the rental price for ${toolType.name}."
}

data object InvalidRentalDiscount : RentalAgreementValidationError {
  override val message = "The rental discount must be between 0 and 100."
}

data object InvalidRentalDuration : RentalAgreementValidationError {
  override val message = "The rental duration must be 1 day or greater."
}

data object InvalidTool : RentalAgreementValidationError {
  override val message = "The tool being rented is invalid."
}

data class FailedToCheckout(val reservationId: ReservationId, val toolType: ToolType) :
    RentalAgreementValidationError {
  override val message = "Failed to checkout reservation $reservationId for ${toolType.name}."
}

data object UnknownError : RentalAgreementValidationError {
  override val message = "There was an unknown error."
}
