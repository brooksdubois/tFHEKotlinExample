package app.voting

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object ReceiptSigning {
    private var _kp: KeyPair? = null
    val keyPair: KeyPair get() = _kp ?: error("ReceiptSigning not initialized")
    val pubKeyB64: String get() = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    /**
     * Dev-friendly: generate a keypair if none exists; optionally persist to disk.
     * In prod you’d load from a configured, protected path.
     */
    fun init(persistDir: Path? = null) {
        _kp = tryLoad(persistDir) ?: generate().also { kp ->
            if (persistDir != null) persist(persistDir, kp)
        }
    }

    fun sign(bytes: ByteArray): ByteArray {
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(keyPair.private)
        sig.update(bytes)
        return sig.sign()
    }

    // ---- helpers ----
    private fun generate(): KeyPair =
        KeyPairGenerator.getInstance("Ed25519").genKeyPair()

    private fun tryLoad(dir: Path?): KeyPair? {
        if (dir == null || !Files.isDirectory(dir)) return null
        val privPath = dir.resolve("ed25519.pk8")
        val pubPath = dir.resolve("ed25519.pub")
        if (!Files.exists(privPath) || !Files.exists(pubPath)) return null

        val kf = KeyFactory.getInstance("Ed25519")
        val priv: PrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(Files.readAllBytes(privPath)))
        val pub: PublicKey = kf.generatePublic(X509EncodedKeySpec(Files.readAllBytes(pubPath)))
        return KeyPair(pub, priv)
    }

    private fun persist(dir: Path, kp: KeyPair) {
        Files.createDirectories(dir)
        Files.write(dir.resolve("ed25519.pk8"), kp.private.encoded) // PKCS#8
        Files.write(dir.resolve("ed25519.pub"), kp.public.encoded)   // X.509
    }
}
