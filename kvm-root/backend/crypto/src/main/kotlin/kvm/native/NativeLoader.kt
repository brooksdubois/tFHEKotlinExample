// src/main/kotlin/kvm/native/NativeLoader.kt
package kvm.native

import java.io.File
import java.net.URLDecoder

object NativeLoader {
    @Volatile private var loaded = false

    fun load() {
        if (loaded) return

        val os = System.getProperty("os.name").lowercase()
        val libName = when {
            os.contains("mac")  -> "libtfhe_bridge.dylib"
            os.contains("win")  -> "tfhe_bridge.dll"
            else                -> "libtfhe_bridge.so"
        }

        // 0) Explicit override first
        System.getProperty("tfhe.bridge.path")?.let { p ->
            System.load(File(p).absolutePath); loaded = true; return
        }
        System.getenv("TFHE_BRIDGE_PATH")?.let { p ->
            System.load(File(p).absolutePath); loaded = true; return
        }

        // 1) Try standard lookup
        runCatching { System.loadLibrary("tfhe_bridge"); loaded = true; return }.onFailure { /* fall through */ }

        // 2) Look in common dev locations, preferring <repo>/libs
        val userDir = File(System.getProperty("user.dir"))
        val codeDir = File(URLDecoder.decode(
            NativeLoader::class.java.protectionDomain.codeSource.location.path, "UTF-8"
        )).parentFile

        val searchDirs = listOfNotNull(
            // Most likely: <repo>/libs
            File(userDir, "backend/crypto/tfhe-bridge/target/release"),
            File(userDir, "kvm-blockchain/libs"),
            // also try current dir and near classes
            userDir,
            File(codeDir, "backend/crypto/tfhe-bridge/target/release"),
            codeDir.parentFile?.let { File(it, "backend/crypto/tfhe-bridge/target/releases") }
        ).distinct()

        val found = searchDirs
            .asSequence()
            .map { File(it, libName) }
            .firstOrNull { it.exists() }

        if (found != null) {
            System.load(found.absolutePath)
            loaded = true
            return
        }

        throw UnsatisfiedLinkError(buildString {
            appendLine("tfhe_bridge native library not found.")
            appendLine("Expected name: $libName")
            appendLine("Searched:")
            searchDirs.forEach { appendLine("  - ${it.absolutePath}") }
            appendLine("Fix: place the lib at <repo>/libs/$libName, or set one of:")
            appendLine("  -Dtfhe.bridge.path=/abs/path/to/$libName")
            appendLine("  TFHE_BRIDGE_PATH=/abs/path/to/$libName")
        })
    }
}
