package org.example.org.example.service.data

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.time.LocalDate
import kotlin.time.Duration
import org.example.org.example.service.UsaDayValidate

data class RentalDates(
    val checkout: LocalDate,
    val due: LocalDate,
    val weekdays: List<LocalDate>,
    val weekends: List<LocalDate>,
    val holidays: List<LocalDate>
) {
  val totalDays = weekdays.size + weekends.size + holidays.size

  fun chargeDays(
      includeWeekdays: Boolean,
      includeWeekends: Boolean,
      includeHolidays: Boolean
  ): Int {
    return if (includeWeekdays) weekdays.size
    else 0 + if (includeWeekends) weekends.size else 0 + if (includeHolidays) holidays.size else 0
  }

  companion object {
    private val LOGGER = KotlinLogging.logger {}

    fun mkUsa(
        uri: URI,
        checkout: LocalDate,
        duration: Duration,
    ): Option<RentalDates> {
      val dayValidate = UsaDayValidate.mkRedisService(uri)
      val allDays: List<LocalDate> =
          checkout.plusDays(1).datesUntil(checkout.plusDays(duration.inWholeDays + 1)).toList()
      val weekdays =
          dayValidate.map { d -> allDays.filter { !d.isWeekend(it) } }.getOrElse { emptyList() }
      val weekends =
          dayValidate.map { d -> allDays.filter { d.isWeekend(it) } }.getOrElse { emptyList() }
      val holidays =
          dayValidate.map { d -> allDays.filter { d.isHoliday(it) } }.getOrElse { emptyList() }

      return when (allDays.isNotEmpty()) {
        true -> Some(RentalDates(checkout, allDays.last(), weekdays, weekends, holidays))
        false -> None
      }.onNone { LOGGER.error { "Failed to make the ${RentalDates::class.java} object." } }
    }
  }
}
