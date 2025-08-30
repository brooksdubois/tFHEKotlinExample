package app.mpc

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kvm.mpc.ArtifactMeta
import kvm.mpc.DecryptOut
import kvm.mpc.MaskOut
import kvm.mpc.MpcSession
import kvm.mpc.RevealOut
import kvm.mpc.StartOut

fun Application.mpcRoutes(useCase: MpcUseCase = MpcUseCase()) {
    routing {
        route("/mpc/sessions") {
            post("/start") {
                val out = useCase.start(call.receive())
                call.respond<StartOut>(out)
            }
            post("/{id}/mask:server") {
                val id = call.parameters["id"]!!
                val out = useCase.maskServer(id, call.receive())
                call.respond<MaskOut>(out)
            }
            post("/{id}/mask") {
                val id = call.parameters["id"]!!
                val out = useCase.mask(id, call.receive())
                call.respond<MaskOut>(out)
            }
            post("/{id}/decrypt") {
                val id = call.parameters["id"]!!
                val out = useCase.decrypt(id, call.receive())
                call.respond<DecryptOut>(out)
            }
            post("/{id}/reveal") {
                val id = call.parameters["id"]!!
                val out = useCase.reveal(id, call.receive())
                call.respond<RevealOut>(out)
            }
            get("/{id}") {
                val id = call.parameters["id"]!!
                call.respond<MpcSession>(useCase.read(id))
            }
            get("/{id}/artifacts") {
                val id = call.parameters["id"]!!
                call.respond<List<ArtifactMeta>>(useCase.listArtifacts(id))
            }
            get("/{id}/zip") {
                val id = call.parameters["id"]!!
                val zip = useCase.zipSession(id)
                val headerValue = "attachment; filename=\"${zip.fileName}\""
                call.response.header(HttpHeaders.ContentDisposition, headerValue)
                call.respondFile(file = zip.toFile())
            }
        }
    }
}
