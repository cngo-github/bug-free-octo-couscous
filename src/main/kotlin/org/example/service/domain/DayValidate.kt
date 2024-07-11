package org.example.service.domain

import java.time.LocalDate

interface DayValidate {
  fun isWeekend(date: LocalDate): Boolean

  fun isHoliday(date: LocalDate): Boolean
}
