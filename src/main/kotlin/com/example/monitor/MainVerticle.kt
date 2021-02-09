package com.example.monitor

import com.mongodb.WriteConcern
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.bson.Document

class MainVerticle : CoroutineVerticle() {

  private val uri = "mongodb://localhost:27017"

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
    router.get("/hello")
    router.post("/monitor")
    return router
  }
}
