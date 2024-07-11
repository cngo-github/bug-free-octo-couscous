package org.example.persistence.data

import java.time.LocalDateTime
import java.util.*

class ReservationId private constructor(val id: String) {
  companion object {
    fun generate(): ReservationId {
      val uuid = UUID.randomUUID().toString().replace("-", "")
      val now = LocalDateTime.now()

      return ReservationId("$uuid||$now")
    }
  }
}
