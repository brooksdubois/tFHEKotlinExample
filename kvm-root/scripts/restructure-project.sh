#!/usr/bin/env bash
set -euo pipefail

branch="refactor/modular-backend"
echo "→ Creating branch $branch"
git checkout -b "$branch"

# --- Helpers ---------------------------------------------------------------
mv_if_exists() {
  local src="$1" dst="$2"
  if [ -e "$src" ]; then
    mkdir -p "$(dirname "$dst")"
    git mv "$src" "$dst"
    echo "moved: $src -> $dst"
  else
    echo "skip: $src (not found)"
  fi
}

write_if_absent() {
  local path="$1" content="$2"
  if [ -e "$path" ]; then
    echo "keep: $path (exists)"
  else
    mkdir -p "$(dirname "$path")"
    printf "%s" "$content" > "$path"
    echo "wrote: $path"
  fi
}

# --- Target layout ---------------------------------------------------------
echo "→ Creating target layout"
mkdir -p backend/{core,crypto,ktor-app,cli}/src/main/kotlin
mkdir -p artifacts/.keep
touch artifacts/.gitkeep

# --- Move Kotlin/Rust sources (adjusts for your repo automatically) --------
# Core models/logic
mv_if_exists kvm-blockchain/src/main/kotlin/kvm/core backend/core/src/main/kotlin/kvm/core
mv_if_exists kvm-blockchain/src/main/kotlin/kvm/model backend/core/src/main/kotlin/kvm/model

# JNI / native wrappers + Rust bridge
mv_if_exists kvm-blockchain/src/main/kotlin/kvm/native backend/crypto/src/main/kotlin/kvm/native
mv_if_exists kvm-blockchain/tfhe-bridge backend/crypto/tfhe-bridge

# Ktor App.kt (find it and relocate as app.AppKt)
echo "→ Searching for App.kt"
app_src="$(git ls-files | grep -E '(^|/)App\.kt$' | grep kvm-blockchain | head -n1 || true)"
if [ -n "$app_src" ]; then
  mkdir -p backend/ktor-app/src/main/kotlin/app
  git mv "$app_src" backend/ktor-app/src/main/kotlin/app/App.kt
  # Normalize package to `package app`
  perl -0777 -pe 's/^package\s+.*$/package app/m' -i backend/ktor-app/src/main/kotlin/app/App.kt || true
  echo "moved & patched package: $app_src -> backend/ktor-app/src/main/kotlin/app/App.kt"
else
  echo "warn: App.kt not found under kvm-blockchain; add your entrypoint to backend/ktor-app later."
fi

# CLI (verifier) into backend/cli
mv_if_exists verifier/src/main/kotlin backend/cli/src/main/kotlin
mv_if_exists verifier/build.gradle.kts backend/cli/build.gradle.kts

# --- Gradle wiring ---------------------------------------------------------
echo "→ Writing Gradle module files (if missing)"

write_if_absent backend/core/build.gradle.kts $'plugins {\n  kotlin("jvm") version "2.0.21"\n  kotlin("plugin.serialization") version "2.0.21"\n}\n\ndependencies {\n  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")\n}\n'

write_if_absent backend/crypto/build.gradle.kts $'plugins {\n  kotlin("jvm") version "2.0.21"\n}\n'

write_if_absent backend/ktor-app/build.gradle.kts $'plugins {\n  kotlin("jvm") version "2.0.21"\n  application\n}\n\ndependencies {\n  implementation(project(":backend:core"))\n  implementation(project(":backend:crypto"))\n  implementation("io.ktor:ktor-server-netty:2.3.12")\n  implementation("io.ktor:ktor-server-content-negotiation:2.3.12")\n  implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")\n  implementation("io.ktor:ktor-server-cors:2.3.12")\n  implementation("ch.qos.logback:logback-classic:1.5.12")\n}\n\napplication { mainClass.set("app.AppKt") }\n'

# If CLI gradle wasn’t moved above, create a minimal one
if [ ! -f backend/cli/build.gradle.kts ]; then
  write_if_absent backend/cli/build.gradle.kts $'plugins {\n  kotlin("jvm") version "2.0.21"\n  application\n}\n\ndependencies {\n  implementation(project(":backend:crypto"))\n  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")\n}\n\napplication { mainClass.set("VerifierKt") }\n'
fi

# settings.gradle.kts
if grep -q 'include(' settings.gradle.kts 2>/dev/null; then
  echo "→ Patching settings.gradle.kts"
  cp settings.gradle.kts settings.gradle.kts.bak
  # Replace include line(s) with just our four modules
  awk '
    BEGIN{done=0}
    /include\(/ && done==0 { print "include(\"backend:core\", \"backend:crypto\", \"backend:ktor-app\", \"backend:cli\")"; done=1; next }
    { print }
  ' settings.gradle.kts.bak > settings.gradle.kts
  # Ensure rootProject name exists
  if ! grep -q 'rootProject.name' settings.gradle.kts; then
    printf '\nrootProject.name = "kvm-root"\n' >> settings.gradle.kts
  fi
else
  write_if_absent settings.gradle.kts $'rootProject.name = "kvm-root"\ninclude("backend:core", "backend:crypto", "backend:ktor-app", "backend:cli")\n'
fi

# --- .gitignore for artifacts ---------------------------------------------
grep -qxF 'artifacts/' .gitignore 2>/dev/null || echo 'artifacts/' >> .gitignore

echo "→ Done. Try building:"
echo "   ./gradlew :backend:ktor-app:run   # server"
echo "   ./gradlew :backend:cli:run --args=\"--help\""

echo "→ If all good: git commit -am \"refactor: modular backend layout\""
