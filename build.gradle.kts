import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin ("jvm") version "1.4.21"
  application
  id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "com.example"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
  jcenter()
}

val vertxVersion = "4.0.2"
val junitJupiterVersion = "5.7.0"

var mainVerticleName = "MainVerticle"
if(project.hasProperty("args")){
  mainVerticleName = "com.example.monitor.${project.property("args")}"
}
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
  mainClassName = launcherClassName
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-lang-kotlin-coroutines")
  implementation("io.vertx:vertx-lang-kotlin")
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("org.mongodb:mongodb-driver-reactivestreams:4.2.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.4.2")
  implementation(kotlin("stdlib-jdk8"))
  implementation("com.fasterxml.jackson.core:jackson-databind:2.12.1")
  implementation("com.fasterxml.jackson.core:jackson-annotations:2.12.1")
  implementation("com.google.api-client:google-api-client:1.30.10")
  implementation("com.google.auth:google-auth-library-oauth2-http:0.11.0")
  implementation("com.google.cloud:google-cloud-storage:1.43.0")
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "11"

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf("run", mainVerticleName, "--redeploy=$watchForChange", "--launcher-class=$launcherClassName", "--on-redeploy=$doOnChange")
}
