package org.example.persistence.cache

import arrow.core.Option
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import kotlin.time.Duration
import org.example.persistence.enums.ToolBrand
import org.example.persistence.enums.ToolCode
import org.example.persistence.enums.ToolType
import org.example.persistence.data.Tool
import redis.embedded.RedisServer

class ToolCacheDaoSuite :
    FunSpec({
      lateinit var redisServer: RedisServer
      val redisPort = 12345
      val redisUri: URI = URI.create("redis://localhost:$redisPort")

      lateinit var maybeToolCacheDao: Option<ToolCacheDao>

      beforeSpec {
        redisServer = RedisServer(redisPort)
        redisServer.start()
      }

      afterSpec { redisServer.stop() }

      beforeEach {
        maybeToolCacheDao =
            RedisCacheDao(redisUri)
                .onSome {
                  it.set(
                      "tool-CHNS",
                      "{\"toolBrand\":\"Stihl\",\"toolCode\":\"CHNS\",\"toolType\":\"Chainsaw\"}",
                      Duration.parse("5m"))
                }
                .flatMap { ToolCacheDao.mkRedisDao(redisUri) }
      }

      afterEach { maybeToolCacheDao.onSome { it.cleanup() } }

      test("mkRedisDao with a valid URI gives Some of ToolCacheDao") {
        ToolCacheDao.mkRedisDao(redisUri).isSome() shouldBe true
      }

      test("mkRedisDao with an invalid URI gives None") {
        ToolCacheDao.mkRedisDao(URI.create("redis://notReal")).isNone() shouldBe true
      }

      test("Get populated tool returns Some of Tool") {
        maybeToolCacheDao
            .flatMap { it.get(ToolCode.CHNS) }
            .isSome { p ->
              p.toolType == ToolType.Chainsaw &&
                  p.toolBrand == ToolBrand.Stihl &&
                  p.toolCode == ToolCode.CHNS
            } shouldBe true
      }

      test("Get unpopulated tool returns None") {
        maybeToolCacheDao.flatMap { it.get(ToolCode.LADW) }.isNone() shouldBe true
      }

      test("Store new tool") {
        maybeToolCacheDao
            .onSome { it.store(Tool(ToolBrand.DeWalt, ToolCode.JAKR, ToolType.Jackhammer)) }
            .flatMap { it.get(ToolCode.JAKR) }
            .isSome { p ->
              p.toolType == ToolType.Jackhammer &&
                  p.toolBrand == ToolBrand.DeWalt &&
                  p.toolCode == ToolCode.JAKR
            } shouldBe true
      }
    })
