package com.example.monitor

import com.mongodb.WriteConcern
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import io.vertx.core.Vertx
import io.vertx.core.Vertx.vertx
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class TaskVerticle: CoroutineVerticle() {

  override suspend fun start(){
    vertx.setPeriodic(TimeUnit.SECONDS.toMillis(5)) {
      GlobalScope.launch(vertx.dispatcher()){
        println("execute ${Date()}")
      }
    }
  }
}
