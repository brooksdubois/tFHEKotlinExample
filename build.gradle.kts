// Root build.gradle.kts — for multi-project setup
plugins {
    // optional: declare plugin versions globally
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.serialization") version "2.1.10" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}
