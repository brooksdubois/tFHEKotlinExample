plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

dependencies {
    implementation(kotlin("stdlib"))

    // Ktor client for test script
    implementation("io.ktor:ktor-client-java:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")

    //for normal verifier cli
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


// Optional: allow running the E2E script directly with Gradle
tasks.register<JavaExec>("runE2E") {
    group = "application"
    description = "Runs the MPC live end-to-end verifier script"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("mpc_live_e2e_testKt") // generated for mpc_live_e2e_test.kts
    args = listOf() // pass --args="..." if needed
    environment("MPC_URL", "http://localhost:8080")
    environment("MPC_WHO", "verifier-1")
    environment("MPC_SEED", "1337")
    environment("MPC_SOURCE", "live")
}


//THIS IS A TEST COMMENT
