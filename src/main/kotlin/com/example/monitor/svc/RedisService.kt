package com.example.monitor.svc

import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.RedisAPI

class RedisService(redisClient: RedisAPI) {

  private val redisClient = redisClient

  private val thirtyDays = 30 * 24 * 60 * 60

  /* update redis, use  hourToMilli:city:brand:color as key, count as value
   *  and return updated count
   */
  suspend fun incrRedisCountAndSetTTL(monitorData: MonitorData): Long{
    val (_, hour, city, brand, color) = monitorData
    val hourToMilliStr = hour.time.toString()
    val key = "$hourToMilliStr:$city:$brand:$color"
    val count = redisClient.incr(key).await().toLong()
    redisClient.expire(key, thirtyDays.toString())
    return count
  }

  // get all data from redis
  suspend fun getAllFromRedis(): List<Map<String, Long>>{
    var cursor = "0"
    val list = mutableListOf<Map<String, Long>>()
    do {
        val cursorResult = redisClient.scan(listOf(cursor)).await().toList()
        cursor = cursorResult[0].toString()
        val keys = cursorResult[1]
        keys.forEach{
          val key = it.toString()
          val count = redisClient.get(key).await().toLong()
          list.add(mapOf(key to count))
        }
    }while (cursor != "0")
    return list
  }
}
