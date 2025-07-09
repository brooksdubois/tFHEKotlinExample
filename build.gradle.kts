plugins {
    application
    kotlin("jvm") version "2.1.10"
}

group = "org.brooksdubois"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    application {
        mainClass.set("kvm.MainKt") // adjust if your main file is elsewhere
    }
    jvmToolchain(21)
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf("-Djava.library.path=${projectDir}/libs")
}
