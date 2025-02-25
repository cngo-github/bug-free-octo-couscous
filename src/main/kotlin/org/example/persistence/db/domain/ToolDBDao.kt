package org.example.persistence.db.domain

import arrow.core.Option
import org.example.persistence.enums.ToolCode
import org.example.persistence.enums.ToolType
import org.example.persistence.data.RentalPrice
import org.example.persistence.data.ReservationId
import org.example.persistence.data.Tool

interface ToolDBDao {
  fun get(toolCode: ToolCode): Option<Tool>

  fun get(toolType: ToolType): Option<RentalPrice>

  fun reserve(toolCode: ToolCode): Option<ReservationId>

  fun checkout(reservationId: ReservationId, toolType: ToolType): Option<ReservationId>

  fun cleanup()
}
