package com.example.monitor.svc

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import com.mongodb.client.model.IndexOptions
import com.mongodb.reactivestreams.client.MongoClients
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.reactive.awaitFirst
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistries.fromRegistries
import org.bson.codecs.pojo.PojoCodecProvider
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

}

data class MonitorData(
  val cameraId: String,
  val hour: Date,
  val city: String,
  val brand: String,
  val color: String,
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

