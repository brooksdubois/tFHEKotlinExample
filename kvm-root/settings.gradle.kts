pluginManagement {
    repositories {
        gradlePluginPortal();
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

rootProject.name = "kvm-root"
include("backend:core", "backend:crypto", "backend:ktor-app", "backend:cli")