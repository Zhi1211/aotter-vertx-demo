package com.example.monitor.svc

import com.example.monitor.WriterSubscriber
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import com.mongodb.client.model.Filters.gte
import com.mongodb.client.model.IndexOptions
import com.mongodb.reactivestreams.client.MongoClients
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.reactive.awaitFirst
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistries.fromRegistries
import org.bson.codecs.pojo.PojoCodecProvider
import java.io.File
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

  suspend fun createExpireIndex(){
    col.createIndex(Document("hour", 1), IndexOptions().expireAfter(30, TimeUnit.DAYS)).awaitFirst()
  }

  suspend fun insertMonitorData(monitorData: MonitorData): Boolean{
    val result = col.insertOne(monitorData).awaitFirst()
    return !result.insertedId.isNull
  }

  fun exportCarCountsMonthly(target: String) {
    val file = File(target)
    if(!file.exists()) {
      file.parentFile.mkdirs()
      file.createNewFile()
    }
    val date = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(30)
    val publisher = col.find(gte("hour", date))
    val subscriber = WriterSubscriber(target)
    publisher.subscribe(subscriber)
    subscriber.await()
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

