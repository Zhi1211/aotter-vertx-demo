package com.example.monitor.svc

import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.RedisAPI

class RedisService(redisClient: RedisAPI) {

  private val redisClient = redisClient

  private val thirtyDays = 30 * 24 * 60 * 60

  /* update redis hash, use hour as key, city:brand:color as attribute, count as value
  *  and return updated count
  */
  suspend fun incrRedisCountAndSetTTL(monitorData: MonitorData): Long{
    val (_, hour, city, brand, color) = monitorData
    val hourToMilliStr = hour.time.toString()
    val count = redisClient.hincrby(hourToMilliStr, "$city:$brand:$color", "1").await().toLong()
    redisClient.expire(hourToMilliStr, thirtyDays.toString())
    return count
  }

  // get all hash data from redis
  suspend fun getAllFromRedis(): Map<String, Map<String, Long>>{
    val keysResponse = redisClient.keys("*").await()
    val map = mutableMapOf<String, Map<String, Long>>()
    keysResponse.forEach { keyRes ->
      val keyHour = keyRes.toString()
      val attrMap = mutableMapOf<String, Long>()

      val hashKeysRes = redisClient.hkeys(keyHour).await()
      hashKeysRes.forEach { hashKeyRes ->
        val hashKey = hashKeyRes.toString()
        val value = redisClient.hget(keyHour, hashKey).await()
        attrMap.put(hashKey, value.toLong())
      }
      map.put(keyHour, attrMap)
    }
    return map
  }
}
