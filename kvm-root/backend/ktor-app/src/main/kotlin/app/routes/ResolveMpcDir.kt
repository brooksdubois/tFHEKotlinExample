package app.routes

import io.ktor.server.application.*
import java.nio.file.Path
import java.nio.file.Paths

fun resolveMpcDir(env: ApplicationEnvironment): Path {
    val cfg = env.config.propertyOrNull("mpc.baseDir")?.getString()
        ?: System.getenv("MPC_BASE_DIR")
        ?: "data/mpc"
    return Paths.get(cfg)
}
