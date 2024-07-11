package org.example.persistence.data

import arrow.core.*
import com.google.gson.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Type
import java.time.LocalDate

private val LOGGER = KotlinLogging.logger {}

data class Holiday(val name: String, val observedOn: LocalDate) {

  override fun toString(): String {
    return "Holiday(name = $name, observedOn = $observedOn)"
  }

  companion object {
    fun from(name: String, observedOn: String): Option<Holiday> {
      return when {
        name.isNotBlank() ->
            Either.catch<LocalDate> { LocalDate.parse(observedOn) }
                .mapLeft {
                  LOGGER.error(it) { "Invalid holiday, name: $name, observed on: $observedOn" }
                }
                .getOrNone()
                .map { Holiday(name, it) }
        else -> {
          LOGGER.error { "Invalid holiday, name: $name, observed on: $observedOn" }
          None
        }
      }
    }
  }
}

class HolidayDeserializer : JsonDeserializer<Holiday> {
  override fun deserialize(p0: JsonElement, p1: Type?, p2: JsonDeserializationContext?): Holiday? {
    LOGGER.trace { "Deserializing ${Holiday::class.java} object" }

    val o = p0.asJsonObject

    return Holiday.from(o.get("name").asString, o.get("observedOn").asString).getOrNull()
  }
}

class HolidaySerializer : JsonSerializer<Holiday> {
  override fun serialize(p0: Holiday, p1: Type?, p2: JsonSerializationContext?): JsonElement {
    LOGGER.trace { "Serializing ${Holiday::class.java} object" }

    val o = JsonObject()

    o.addProperty("name", p0.name)
    o.addProperty("observedOn", p0.observedOn.toString())

    return o
  }
}
