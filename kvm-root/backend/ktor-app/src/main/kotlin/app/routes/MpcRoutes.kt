package app.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kvm.mpc.StartSessionReq
import kvm.mpc.StartSessionRes
import kvm.mpc.startMpcSession
import java.io.File
import java.nio.file.Path

/** Mount MPC routes here; no domain logic in this module. */
fun Route.installMpcRoutes(
    mpcBaseDir: Path,
    foldFromLedger: () -> Pair<List<String>, Int>
) {
    route("/mpc") {

        // POST /mpc/sessions/start
        route("/sessions") {
            post("/start") {
                val req = call.receive<StartSessionReq>()
                val res: StartSessionRes = startMpcSession(req, foldFromLedger, mpcBaseDir)
                call.respond(res)
            }
        }

        // GET /mpc/artifacts/{path...}  (read-only, safe)
        get("/artifacts/{path...}") {
            val segs = call.parameters.getAll("path") ?: return@get call.respond(HttpStatusCode.BadRequest)
            val target = segs.fold(mpcBaseDir) { acc, s -> acc.resolve(s) }.normalize()
            if (!target.startsWith(mpcBaseDir)) return@get call.respond(HttpStatusCode.Forbidden)
            val f = target.toFile()
            if (!f.exists() || f.isDirectory) return@get call.respond(HttpStatusCode.NotFound)
            call.respondFile(f)
        }
    }
}
