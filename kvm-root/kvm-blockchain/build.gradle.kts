plugins {
    application
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
}

group = "org.brooksdubois"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.github.alibaba-edu:mpc4j:v1.1.3-beta")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-server-cors:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.5.6")
}

tasks.test {
    useJUnitPlatform()
}

val libDir = "${rootProject.rootDir}/libs"

application {
    // Main file with `fun main() = embeddedServer(...)`
    mainClass.set("server.AppKt")
    applicationDefaultJvmArgs = listOf("-Djava.library.path=$libDir")
}


// Optional: separate run tasks if you want both entrypoints
tasks.register<JavaExec>("runServer") {
    group = "application"
    mainClass.set("server.AppKt")
    classpath = sourceSets["main"].runtimeClasspath
}
tasks.register<JavaExec>("runCli") {
    group = "application"
    mainClass.set("kvm.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

kotlin {
    application {
        mainClass.set("kvm.MainKt") // adjust if your main file is elsewhere
    }
    jvmToolchain(21)
}

tasks.matching { it.name in listOf("runServer", "runCli") }.configureEach {
    (this as JavaExec).jvmArgs("-Djava.library.path=$libDir")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}