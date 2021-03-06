package com.example.monitor.svc

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import com.mongodb.client.model.Accumulators.sum
import com.mongodb.client.model.Aggregates.group
import com.mongodb.client.model.Aggregates.match
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.reactivestreams.client.MongoClients
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistries.fromRegistries
import org.bson.codecs.pojo.PojoCodecProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit

class MongoService {

  private val uri = System.getenv("MONGO_URI") ?: "mongodb://localhost:27017"

  private val dbName = "cars"

  private val colName = "carHourCounts"

  private val pojoCodecRegistry = fromRegistries(
    MongoClientSettings.getDefaultCodecRegistry(),
    fromProviders(PojoCodecProvider.builder().automatic(true).build()))

  private val settings = MongoClientSettings.builder()
    .applyConnectionString(ConnectionString(uri))
    .codecRegistry(pojoCodecRegistry)
    .build()

  private val col = MongoClients.create(settings)
    .getDatabase(dbName)
    .getCollection(colName, MonitorData::class.java)
    .withWriteConcern(WriteConcern.ACKNOWLEDGED)

  suspend fun createIndexes(){
    col.createIndex(Document("hour", 1), IndexOptions().expireAfter(30, TimeUnit.DAYS)).awaitFirst()
    col.createIndex(Indexes.compoundIndex(
      Indexes.descending("hour"),
      Indexes.descending("city"),
      Indexes.descending("brand"),
      Indexes.descending("color")
    )).awaitFirst()
  }

  suspend fun insertMonitorData(monitorData: MonitorData): Boolean{
    val result = col.insertOne(monitorData).awaitFirst()
    return !result.insertedId.isNull
  }

  // get data as flow
  fun getCarCountsMonthly(): Flow<MonitorData>{
    val date = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(30)
    return col.find(gte("hour", date)).asFlow()
  }

  // get current count of a specific group
  suspend fun getSpecificCount(monitorData: MonitorData): Long{
    val (_, hour, city, brand, color) = monitorData
    val doc = col.aggregate(listOf(
      group(
        Document("hour", "\$hour")
          .append("city", "\$city")
          .append("brand", "\$brand")
          .append("color", "\$color"),
        sum("count", 1)
      ),
      match(and(
        eq("_id.hour", hour),
        eq("_id.city", city),
        eq("_id.brand", brand),
        eq("_id.color", color)
      ))
    )).awaitFirstOrNull()
    return doc?.count ?: 0
  }
}

data class MonitorData(
  var cameraId: String? = null,
  var hour: Date? = null,
  var city: String? = null,
  var brand: String? = null,
  var color: String? = null,
  var count: Long? = null
){
  companion object {
    fun mapJsonObjectToData(jsonObject: JsonObject): MonitorData{
      val hourStr = jsonObject.getString("hour")
      val ldt = LocalDateTime.parse(hourStr)
      val hour = Date.from(ldt.atZone(ZoneId.of("Asia/Shanghai")).toInstant())
      return MonitorData(
        jsonObject.getString("cameraId"),
        hour,
        jsonObject.getString("city"),
        jsonObject.getString("brand"),
        jsonObject.getString("color")
      )
    }
  }
}

