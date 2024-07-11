package org.example.service.data

import org.example.persistence.data.RentalPrice
import org.example.persistence.data.ReservationId
import org.example.persistence.data.Tool

data class Reservation(val id: ReservationId, val tool: Tool, val rentalPrice: RentalPrice)
