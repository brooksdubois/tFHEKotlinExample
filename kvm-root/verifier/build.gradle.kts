plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":kvm-blockchain")) // if main project is named this
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

application {
    mainClass.set("VerifierKt")

    applicationDefaultJvmArgs = listOf(
        "-Djava.library.path=../kvm-blockchain/libs"
    )
}
