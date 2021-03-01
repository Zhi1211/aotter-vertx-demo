package com.example.monitor

import com.example.monitor.svc.MongoService
import com.google.api.services.storage.StorageScopes
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.*
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

class TaskVerticle: CoroutineVerticle() {

  private lateinit var storage: Storage

  private lateinit var googleCredentials: GoogleCredentials

  private lateinit var mongoService: MongoService

  private val bucket = System.getenv("BUCKET") ?: "monitor_report_monthly"

  override suspend fun start(){
    mongoService = MongoService()
    storage = StorageOptions.newBuilder().setProjectId("for-test-304513").build().service
    googleCredentials = googleCredentialStorageScope()

    // setup a periodically execute task
    vertx.setPeriodic(TimeUnit.SECONDS.toMillis(3)) {
      // run a coroutine
      GlobalScope.launch(vertx.dispatcher()){
        val fileName = mongoexportCsv()
        uploadObjectAndDeleteTmp(fileName)
      }
    }
  }

  // get google credential from file
  private fun googleCredentialStorageScope(): GoogleCredentials {
    val gcpCredentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS") ?: "/tmp/for-test.json"
    val serviceAccount = Files.newInputStream(Paths.get(gcpCredentialsPath))
    return GoogleCredentials.fromStream(serviceAccount).createScoped(StorageScopes.all())
  }

  // export car counts
  private fun mongoexportCsv(): String{
    val now = DateTimeFormatter.ofPattern("yyyy-MM").format(Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
    val fileName = "/tmp/$now.csv"
    mongoService.exportCarCountsMonthly(fileName)
    return fileName
  }

  // upload GCS
  private fun uploadObjectAndDeleteTmp(fileName: String) {
    val inputStream = File(fileName).inputStream()
    val blobId = BlobId.of(bucket, fileName)
    val blobInfo = BlobInfo.newBuilder(blobId).build()
    try{
      val blob = storage.create(blobInfo, inputStream)
      if(blob.exists()){
        Files.delete(Paths.get(fileName))
      }
    } catch (e: StorageException){
      e.printStackTrace()
    }
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
      // set TaskVerticle as worker verticle
      vertx.deployVerticle(TaskVerticle::class.java, DeploymentOptions().setWorker(true))
      println("task verticle deploy succeed")
    } else {
      println("task verticle deploy failed")
    }
  }
}
