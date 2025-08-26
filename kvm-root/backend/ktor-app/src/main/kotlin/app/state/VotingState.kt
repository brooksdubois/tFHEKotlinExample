package app.state

import java.io.File
import kvm.core.Blockchain
import kvm.native.U16
import kvm.native.Keypair

object VotingState {
    val chain = Blockchain().apply { mineGenesis() }

    private val publicDir = File("public").also { it.mkdirs() }
    val u16Keys = U16.generateKeypair() // election keypair (dev)

    // Write public artifacts on boot
    val compressedServerKey: ByteArray = U16.exportCompressedServerKey(u16Keys).also {
        File(publicDir, "u16_server_key.bin").writeBytes(it)
        // DEV-ONLY: write client key for local decrypt
        File(publicDir, "u16_client_key.bin").writeBytes(U16.exportClientKey(u16Keys))
    }
}
