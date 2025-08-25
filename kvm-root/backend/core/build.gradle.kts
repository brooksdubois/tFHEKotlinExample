plugins {
  kotlin("jvm") version "2.0.21"
  kotlin("plugin.serialization") version "2.0.21"
}

dependencies {
    implementation(project(":backend:crypto")) // JNI wrappers + TFHE bridge
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
