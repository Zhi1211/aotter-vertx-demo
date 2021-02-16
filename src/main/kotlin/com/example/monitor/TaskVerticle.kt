package com.example.monitor

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
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

class TaskVerticle: CoroutineVerticle() {

  private lateinit var storage: Storage

  private lateinit var googleCredentials: GoogleCredentials

  private val bucket = System.getenv("BUCKET") ?: "monitor_report_monthly"

  override suspend fun start(){
    storage = StorageOptions.newBuilder().setProjectId("for-test-304513").build().service
    googleCredentials = googleCredentialStorageScope()

    // setup a periodically execute task
    vertx.setPeriodic(TimeUnit.SECONDS.toMillis(3)) {
      // run a coroutine
      GlobalScope.launch(vertx.dispatcher()){
        val fileName = mongoexportCsv()
        val filePath = Paths.get(fileName)
        val inputStream: InputStream = Files.newInputStream(filePath)
        uploadObjectAndDeleteTmp(inputStream, fileName, filePath)
      }
    }
  }

  // get google credential from file
  private fun googleCredentialStorageScope(): GoogleCredentials {
    val gcpCredentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS") ?: "for-test.json"
    val serviceAccount = Files.newInputStream(Paths.get(gcpCredentialsPath))
    return GoogleCredentials.fromStream(serviceAccount).createScoped(StorageScopes.all())
  }

  // use mongoexport command export mongodb collection
  private fun mongoexportCsv(): String{
    val now = DateTimeFormatter.ofPattern("yyyy-MM").format(Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
    val uri = System.getenv("MONGO_URI") ?: "mongodb://localhost:27017"
    val fileName = "$now.csv"
    val run = Runtime.getRuntime()
    run.exec("mongoexport --$uri --db cars -c carHourCounts").waitFor()
    return fileName
  }

  // upload GCS
  private fun uploadObjectAndDeleteTmp(stream: InputStream, fileName: String, filePath: Path) {
    val blobId = BlobId.of(bucket, fileName)
    val blobInfo = BlobInfo.newBuilder(blobId).build()
    try{
      val blob = storage.create(blobInfo, stream)
      if(blob.exists()){
        Files.delete(filePath)
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
