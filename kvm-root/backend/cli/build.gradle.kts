plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}
dependencies {
    implementation(project(":backend:crypto"))
    implementation(project(":backend:core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

kotlin { jvmToolchain(17) }

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
