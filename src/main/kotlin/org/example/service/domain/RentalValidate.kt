package org.example.org.example.service.domain

import arrow.core.Option
import org.example.org.example.persistence.data.RentalPrice
import org.example.org.example.persistence.data.Tool
import org.example.org.example.persistence.enums.ToolCode
import org.example.org.example.persistence.enums.ToolType

interface RentalValidate {
  fun get(toolCode: ToolCode): Option<Tool>

  fun get(toolType: ToolType): Option<RentalPrice>

  fun isValid(tool: Tool): Boolean
}
