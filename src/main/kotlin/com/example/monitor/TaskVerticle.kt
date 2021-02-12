package com.example.monitor

import com.google.api.services.storage.StorageScopes
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.*
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

class TaskVerticle: CoroutineVerticle() {

  private lateinit var storage: Storage

  private lateinit var googleCredentials: GoogleCredentials

  private val bucket = "monitor_report_monthly"

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
        uploadObject(inputStream, fileName)
        Files.delete(filePath)
      }
    }
  }

  // get google credential from file
  private fun googleCredentialStorageScope(): GoogleCredentials {
    val gcpCredentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    val serviceAccount = Files.newInputStream(Paths.get(gcpCredentialsPath))
    return GoogleCredentials.fromStream(serviceAccount).createScoped(StorageScopes.all())
  }

  // use mongoexport command export mongodb collection
  private fun mongoexportCsv(): String{
    val now = DateTimeFormatter.ofPattern("yyyy-MM").format(Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
    val fileName = "$now.csv"
    val run = Runtime.getRuntime()
    run.exec("mongoexport --host localhost --port 27017 --db cars -c carHourCounts --out $fileName").waitFor()
    return fileName
  }

  // upload GCS
  private fun uploadObject(stream: InputStream, fileName: String): Boolean {
    val blobId = BlobId.of(bucket, fileName)
    val blobInfo = BlobInfo.newBuilder(blobId).build()
    try{
      val blob = storage.create(blobInfo, stream)
      return blob.exists()
    } catch (e: StorageException){
      e.printStackTrace()
    }
  }
}
