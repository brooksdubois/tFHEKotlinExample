plugins {
  kotlin("jvm") version "2.0.21"
  kotlin("plugin.serialization") version "2.0.21"
  application
}

dependencies {
  implementation(project(":backend:core"))
  implementation(project(":backend:crypto"))
  implementation("io.ktor:ktor-server-netty:2.3.12")
  implementation("io.ktor:ktor-server-status-pages:2.3.12")
  implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
  implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("io.ktor:ktor-server-cors:2.3.12")
  implementation("ch.qos.logback:logback-classic:1.5.12")
}

application {
    mainClass.set("app.AppKt")
    applicationDefaultJvmArgs += listOf(
        "-Djava.library.path=${project(":backend:crypto").projectDir}/tfhe-bridge/target/release"
    )
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
