package org.example.org.example.persistence.cache

import arrow.core.Option
import com.google.gson.GsonBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import kotlin.time.Duration
import org.example.org.example.persistence.enums.ToolCode
import org.example.persistence.data.Tool
import org.example.persistence.data.ToolDeserializer
import org.example.persistence.data.ToolSerializer

class ToolCacheDao private constructor(private val cacheDao: CacheDao) {
  fun get(toolCode: ToolCode): Option<Tool> {
    LOGGER.trace { "Getting the rental price for $toolCode." }

    return cacheDao.get(key(toolCode)).map { GSON.fromJson(it, Tool::class.java) }
  }

  fun store(tool: Tool) {
    LOGGER.trace { "Storing the tool ${tool.toolCode}." }

    cacheDao.set(key(tool.toolCode), GSON.toJson(tool), DEFAULT_TIMEOUT)
  }

  fun cleanup() = cacheDao.cleanup()

  companion object {
    val DEFAULT_TIMEOUT = Duration.parse("5m")

    private val LOGGER = KotlinLogging.logger {}
    private val GSON =
        GsonBuilder()
            .registerTypeAdapter(Tool::class.java, ToolDeserializer())
            .registerTypeAdapter(Tool::class.java, ToolSerializer())
            .create()

    fun mkRedisDao(uri: URI): Option<ToolCacheDao> =
        RedisCacheDao(uri)
            .map { ToolCacheDao(it) }
            .onNone { LOGGER.error { "Failed to create the ${ToolCacheDao::class.java} object." } }

    private fun key(toolCode: ToolCode): String = "tool-$toolCode"
  }
}
