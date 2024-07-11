package org.example.org.example.service

import arrow.core.Option
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import org.example.org.example.service.domain.DayValidate
import org.example.org.example.service.domain.Holidays
import org.example.service.UsaHolidays

class UsaDayValidate private constructor(private val holidays: Holidays) : DayValidate {
  override fun isWeekend(date: LocalDate): Boolean {
    val day = date.dayOfWeek

    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
  }

  override fun isHoliday(date: LocalDate): Boolean = holidays.isHoliday(date)

  companion object {
    private val LOGGER = KotlinLogging.logger {}

    fun mkRedisService(uri: URI): Option<UsaDayValidate> =
        UsaHolidays.mkRedisService(uri)
            .map { UsaDayValidate(it) }
            .onNone {
              LOGGER.error { "Failed to create the ${UsaDayValidate::class.java} object." }
            }
  }
}
