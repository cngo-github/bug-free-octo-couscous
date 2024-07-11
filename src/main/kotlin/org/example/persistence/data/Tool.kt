package org.example.org.example.persistence.data

import arrow.core.*
import com.google.gson.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Type
import org.example.org.example.persistence.enums.ToolBrand
import org.example.org.example.persistence.enums.ToolCode
import org.example.org.example.persistence.enums.ToolType

private val LOGGER = KotlinLogging.logger {}

data class Tool(val toolBrand: ToolBrand, val toolCode: ToolCode, val toolType: ToolType) {
  override fun equals(other: Any?): Boolean {
    if (other != null && other is Tool) {
      return toolBrand == other.toolBrand &&
          toolCode == other.toolCode &&
          toolType == other.toolType
    }

    return false
  }

  override fun toString(): String {
    return "Tool(toolBrand = $toolBrand, toolCode = $toolCode, toolType = $toolType)"
  }

  override fun hashCode(): Int {
    var result = toolBrand.hashCode()
    result = 31 * result + toolCode.hashCode()
    result = 31 * result + toolType.hashCode()
    return result
  }

  companion object {
    fun from(toolBrand: String, toolCode: String, toolType: String): Option<Tool> {
      return Either.catch {
            Tool(
                ToolBrand.valueOf(toolBrand),
                ToolCode.valueOf(toolCode),
                ToolType.valueOf(toolType),
            )
          }
          .onLeft {
            LOGGER.error(it) {
              "Invalid tool, toolBrand = $toolBrand, toolCode = $toolCode, toolType = $toolType"
            }
          }
          .getOrNone()
    }
  }
}

class ToolDeserializer : JsonDeserializer<Tool> {
  override fun deserialize(p0: JsonElement, p1: Type?, p2: JsonDeserializationContext?): Tool? {
    LOGGER.trace { "Deserializing ${Tool::class.java} object" }

    val o = p0.asJsonObject

    return Tool.from(
            o.get("toolBrand").asString, o.get("toolCode").asString, o.get("toolType").asString)
        .getOrNull()
  }
}

class ToolSerializer : JsonSerializer<Tool> {
  override fun serialize(p0: Tool, p1: Type?, p2: JsonSerializationContext?): JsonElement {
    LOGGER.trace { "Serializing ${Tool::class.java} object" }

    val o = JsonObject()

    o.addProperty("toolBrand", p0.toolBrand.name)
    o.addProperty("toolCode", p0.toolCode.name)
    o.addProperty("toolType", p0.toolType.name)

    return o
  }
}
