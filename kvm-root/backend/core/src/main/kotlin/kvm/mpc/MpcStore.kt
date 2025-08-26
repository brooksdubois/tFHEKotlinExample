package kvm.mpc

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MpcStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val root: Path = Path.of("verifier", "sessions").also { Files.createDirectories(it) }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "")
    fun dirOf(id: String): Path = root.resolve(id).also { Files.createDirectories(it) }
    private fun fileOf(id: String): Path = dirOf(id).resolve("session.json")

    fun write(session: MpcSession) {
        Files.writeString(fileOf(session.id), json.encodeToString(MpcSession.serializer(), session))
    }

    fun read(id: String): MpcSession =
        json.decodeFromString(MpcSession.serializer(), Files.readString(fileOf(id)))

    /* ── Artifact helpers ───────────────────────────────────────────────────── */

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

    fun writeBytes(id: String, name: String, bytes: ByteArray): ArtifactMeta {
        val p = dirOf(id).resolve(name)
        Files.write(p, bytes)
        return ArtifactMeta(
            name = name,
            relPath = root.relativize(p).toString(), // verifier/sessions/<id>/...
            bytes = bytes.size.toLong(),
            sha256 = sha256Hex(bytes),
            createdAtMs = Instant.now().toEpochMilli()
        )
    }

    fun writeText(id: String, name: String, text: String): ArtifactMeta =
        writeBytes(id, name, text.toByteArray())

    fun listArtifacts(id: String): List<ArtifactMeta> =
        Files.list(dirOf(id)).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .map { p ->
                    val b = Files.readAllBytes(p)
                    ArtifactMeta(
                        name = p.fileName.toString(),
                        relPath = root.relativize(p).toString(),
                        bytes = b.size.toLong(),
                        sha256 = sha256Hex(b),
                        createdAtMs = Files.getLastModifiedTime(p).toMillis()
                    )
                }.toList()
        }

    fun zipSession(id: String): Path {
        val dir = dirOf(id)
        val zip = dir.resolve("session.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { zos ->
            Files.list(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString() != "session.zip" }
                    .forEach { p ->
                        zos.putNextEntry(ZipEntry(p.fileName.toString()))
                        Files.newInputStream(p).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
            }
        }
        return zip
    }
}
