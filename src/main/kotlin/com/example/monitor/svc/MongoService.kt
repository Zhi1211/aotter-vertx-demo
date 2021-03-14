package com.example.monitor.svc

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import com.mongodb.client.model.*
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts.descending
import com.mongodb.client.model.Sorts.orderBy
import com.mongodb.client.model.Updates.*
import com.mongodb.reactivestreams.client.MongoClients
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistries.fromRegistries
import org.bson.codecs.pojo.PojoCodecProvider
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.conversions.Bson
import org.bson.types.ObjectId
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

  suspend fun insertMonitorData(monitorData: MonitorData): MonitorData {
    val (_, cameraId, hour, city, brand, color) = monitorData
    val updates = mutableListOf<Bson>()
    updates.add(setOnInsert("hour", hour))
    updates.add(setOnInsert("city", city))
    updates.add(setOnInsert("brand", brand))
    updates.add(setOnInsert("color", color))
    updates.add(setOnInsert("cameraId", cameraId))
    return col.findOneAndUpdate(
      eq("_id", ObjectId()),
      combine(updates),
      FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).awaitFirst()
  }

  // get data as flow
  fun getCarCountsMonthly(): Flow<MonitorData> {
    val date = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(30)
    return col.find(gte("hour", date)).asFlow()
  }

  // get current count of a specific group
  suspend fun getSpecificCount(monitorData: MonitorData): Long{
    val (_, _, hour, city, brand, color) = monitorData
    return col.find(and(
      eq("hour", hour),
      eq("city", city),
      eq("brand", brand),
      eq("color", color)
    )).sort(orderBy(descending("count"))).awaitFirst().count ?: 0
  }

  suspend fun updateCarCountById(monitorData: MonitorData){
    val count = getSpecificCount(monitorData)
    col.findOneAndUpdate(
      eq("_id", monitorData.id),
      set("count", count + 1),
      FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).awaitFirst()
  }
}

data class MonitorData(
  @BsonId var id: ObjectId? = null,
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
        null,
        jsonObject.getString("cameraId"),
        hour,
        jsonObject.getString("city"),
        jsonObject.getString("brand"),
        jsonObject.getString("color")
      )
    }
  }
}

