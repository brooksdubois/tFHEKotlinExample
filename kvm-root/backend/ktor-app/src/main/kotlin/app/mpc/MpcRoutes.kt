package app.mpc

import app.routes.resolveMpcDir
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kvm.mpc.StartSessionReq
import java.io.File

/** Mount MPC routes; no domain logic here. */
fun Application.mpcRoutes() {
    val mpcBaseDir = resolveMpcDir(environment)
    val mpc = MpcUseCase(mpcBaseDir)

    routing {
        route("/mpc") {
            route("/sessions") {
                // POST /mpc/sessions/start
                post("/start") {
                    val req = call.receive<StartSessionReq>()
                    val res = mpc.start(req)
                    call.respond(res)
                }

                // GET /mpc/artifacts/{path...} (read-only file server for artifacts)
                get("/artifacts/{path...}") {
                    val segs = call.parameters.getAll("path") ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val target = segs.fold(mpcBaseDir) { acc, s -> acc.resolve(s) }.normalize()
                    if (!target.startsWith(mpcBaseDir)) return@get call.respond(HttpStatusCode.Forbidden)
                    val f = target.toFile()
                    if (!f.exists() || f.isDirectory) return@get call.respond(HttpStatusCode.NotFound)
                    call.respondFile(f)
                }

                // POST /mpc/sessions/{id}/mask:server
                post("/{id}/mask:server") {
                    val id = call.parameters["id"]!!
                    val req = call.receive<kvm.mpc.MaskServerReq>()
                    val res = mpc.maskServer(id, req)
                    call.respond(res)
                }

                // POST /mpc/sessions/{id}/decrypt
                post("/{id}/decrypt") {
                    val id = call.parameters["id"]!!
                    val req = call.receive<kvm.mpc.DecryptReq>()
                    val res = mpc.decrypt(id, req)
                    call.respond(res)
                }

                // POST /mpc/sessions/{id}/reveal
                post("/{id}/reveal") {
                    val id = call.parameters["id"]!!
                    val req = call.receive<kvm.mpc.RevealReq>()
                    val res = mpc.reveal(id, req)
                    call.respond(res)
                }

                // GET /mpc/sessions/{id}  (lightweight status)
                get("/{id}") {
                    val id = call.parameters["id"]!!
                    call.respond(mpc.status(id))
                }

                // GET /mpc/sessions/{id}/zip
                get("/{id}/zip") {
                    val id = call.parameters["id"]!!
                    val zipBytes = mpc.zip(id)
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"session-$id.zip\""
                    )
                    call.respondBytes(zipBytes, ContentType.Application.Zip)
                }
            }
        }
    }
}
