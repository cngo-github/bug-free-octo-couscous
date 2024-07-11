package org.example.org.example.service.domain

import java.time.LocalDate

interface Holidays {
  fun isHoliday(date: LocalDate): Boolean
}
