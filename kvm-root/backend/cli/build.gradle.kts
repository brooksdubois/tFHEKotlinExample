plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.1.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":backend:crypto")) // JNI wrappers + TFHE bridge
    implementation(project(":backend:core"))   // models/utils (include if CLI uses them)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

application {
    mainClass.set("VerifierKt")
    
    applicationDefaultJvmArgs += listOf(
        "-Djava.library.path=${project(":backend:crypto").projectDir}/tfhe-bridge/target/release"
    )
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

//THIS IS A TEST COMMENT
