package org.example.org.example.persistence.data

import java.time.LocalDateTime
import java.util.*

data class ReservationId(val id: String) {
  companion object {
    fun generate(): ReservationId {
      val uuid = UUID.randomUUID().toString().replace("-", "")
      val now = LocalDateTime.now()

      return ReservationId("$uuid||$now")
    }
  }
}
