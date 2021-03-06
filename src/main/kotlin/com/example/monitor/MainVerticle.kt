package com.example.monitor

import com.example.monitor.svc.MongoService
import com.example.monitor.svc.MonitorData
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainVerticle : CoroutineVerticle() {

  private lateinit var mongoService: MongoService

  override suspend fun start() {
    mongoService = MongoService()
    mongoService.createIndexes()

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
      GlobalScope.launch(ctx.vertx().dispatcher()){
        val jsonObject = ctx.bodyAsJson ?: ctx.response().setStatusCode(400).end("invalid request body")
        val monitorData = MonitorData.mapJsonObjectToData(jsonObject as JsonObject)
        val success = handleMonitorRequest(monitorData)
        if(!success) ctx.response().statusCode = 500
        ctx.response().end()
      }
    }
    return router
  }

  private suspend fun handleMonitorRequest(monitorData: MonitorData): Boolean{
    val currentCount = mongoService.getSpecificCount(monitorData)
    monitorData.count = currentCount + 1
    return mongoService.insertMonitorData(monitorData)
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

