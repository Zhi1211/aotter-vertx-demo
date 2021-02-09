package com.example.monitor

import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await

class MainVerticle : CoroutineVerticle() {

  override suspend fun start() {
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
