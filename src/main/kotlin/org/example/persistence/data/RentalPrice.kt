package org.example.org.example.persistence.data

import arrow.core.*
import com.google.gson.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Type
import org.example.org.example.persistence.enums.ToolType
import org.javamoney.moneta.Money

private val LOGGER = KotlinLogging.logger {}

data class RentalPrice(
    val type: ToolType,
    val dailyPrice: Money,
    val weekdayCharge: Boolean,
    val weekendCharge: Boolean,
    val holidayCharge: Boolean
) {
  override fun toString(): String {
    return "RentalPrice(type = $type, dailyPrice = $dailyPrice, weekdayCharge = $weekdayCharge, weekendCharge = $weekendCharge, holidayCharge = $holidayCharge)"
  }

  companion object {
    fun from(
        type: String,
        dailyPrice: String,
        weekdayCharge: Boolean,
        weekendCharge: Boolean,
        holidayCharge: Boolean
    ): Option<RentalPrice> {
      return Either.catch {
            RentalPrice(
                ToolType.valueOf(type),
                Money.parse(dailyPrice),
                weekdayCharge,
                weekendCharge,
                holidayCharge)
          }
          .onLeft {
            LOGGER.error(it) {
              "Invalid rental price, type = $type, dailyPrice = $dailyPrice, weekdayCharge = $weekdayCharge, weekendCharge = $weekendCharge, holidayCharge = $holidayCharge"
            }
          }
          .getOrNone()
    }
  }
}

class RentalPriceDeserializer : JsonDeserializer<RentalPrice> {
  override fun deserialize(
      p0: JsonElement,
      p1: Type?,
      p2: JsonDeserializationContext?
  ): RentalPrice? {
    LOGGER.trace { "Deserializing ${RentalPrice::class.java} object" }

    val o = p0.asJsonObject

    return RentalPrice.from(
            o.get("type").asString,
            o.get("dailyPrice").asString,
            o.get("weekdayCharge").asBoolean,
            o.get("weekendCharge").asBoolean,
            o.get("holidayCharge").asBoolean)
        .getOrNull()
  }
}

class RentalPriceSerializer : JsonSerializer<RentalPrice> {
  override fun serialize(p0: RentalPrice, p1: Type?, p2: JsonSerializationContext?): JsonElement {
    LOGGER.trace { "Serializing ${RentalPrice::class.java} object" }

    val o = JsonObject()

    o.addProperty("type", p0.type.name)
    o.addProperty("dailyPrice", p0.dailyPrice.toString())
    o.addProperty("weekdayCharge", p0.weekdayCharge)
    o.addProperty("weekendCharge", p0.weekendCharge)
    o.addProperty("holidayCharge", p0.holidayCharge)

    return o
  }
}
