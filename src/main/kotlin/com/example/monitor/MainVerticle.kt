package com.example.monitor

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.WriteConcern
import com.mongodb.client.model.*
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.collect
import org.bson.Document
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class MainVerticle : CoroutineVerticle() {

  //  private val uri = "mongodb://localhost:27017"
  private val uri = "mongodb://mongo:27017"

  private val dbName = "cars"

  private val colName = "carHourCounts"

  private lateinit var col: MongoCollection<Document>

  override suspend fun start() {
    col = MongoClients.create(uri)
      .getDatabase(dbName)
      .getCollection(colName)
      .withWriteConcern(WriteConcern.ACKNOWLEDGED)
    col.createIndex(Document("hour", 1), IndexOptions().expireAfter(30, TimeUnit.DAYS)).awaitFirst()

    vertx
      .createHttpServer()
      .requestHandler(router())
      .listen(8080)
      .await()
  }

  private fun router(): Router {
    val router = Router.router(vertx)
    router.route().handler(BodyHandler.create())
    router.get("/").handler { ctx-> ctx.response().end("hello") }
    router.post("/monitor").handler { ctx ->
//    run a coroutine
      GlobalScope.launch(ctx.vertx().dispatcher()){
        accumulateCarCount(ctx)
      }
    }
    return router
  }

//  process data send from monitors and return all current records
  private suspend fun accumulateCarCount(ctx: RoutingContext){
    val jsonObj = ctx.bodyAsJson
    upsertCarHourCount(jsonObj)
    val records = getAllRecords()
    ctx.response().end(ObjectMapper().writeValueAsString(records))
  }

// if record with same conditions exists, increase count 1;
// if not, insert a new record
  private suspend fun upsertCarHourCount(jsonObj: JsonObject): Document?{
    val hour = LocalDateTime.parse(jsonObj.getString("hour"))
    return col.findOneAndUpdate(
      Filters.and(
        Filters.eq("brand", jsonObj.getString("brand")),
        Filters.eq("color", jsonObj.getString("color")),
        Filters.eq("city", jsonObj.getString("city")),
        Filters.eq("hour", hour)
      ),
      Updates.inc("count", 1),
      FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).awaitFirst()
  }

  private suspend fun getAllRecords(): List<Document>{
    val records = mutableListOf<Document>()
    col.find().collect { records.add(it) }
    return records
  }
}

// deploy as clustered verticle
fun main(){
  val vertxOptions = VertxOptions()
  val manager = HazelcastClusterManager()
  vertxOptions.clusterManager = manager
  Vertx.clusteredVertx(vertxOptions) { res ->
    if (res.succeeded()) {
      val vertx = res.result()
      vertx.deployVerticle(MainVerticle::class.java, DeploymentOptions())
      println("main verticle deploy succeed")
    } else {
      println("main verticle deploy failed")
    }
  }
}

