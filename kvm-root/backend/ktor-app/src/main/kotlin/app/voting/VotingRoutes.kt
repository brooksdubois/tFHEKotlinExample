package app.voting

import app.api.ErrorOut
import app.routes.installMpcRoutes
import app.routes.resolveMpcDir
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kvm.voting.BlockSummary
import kvm.voting.VoteIn
import kvm.voting.VoteInCompat
import kvm.voting.VoteOut
import kvm.voting.VoteReceiptUi

fun Application.votingRoutes(useCase: VotingUseCase = VotingUseCase()) {
    routing {
        get("/server-key") {
            call.respondBytes(bytes = useCase.serverKey(), contentType = ContentType.Application.OctetStream)
        }

        get("/blocks") {
            call.respond<List<BlockSummary>>(useCase.blocks())
        }

        post("/vote") {
            // If you kept the compat wrapper, use that; otherwise receive your VoteIn directly
            val inDto = call.receive<VoteIn>() // or VoteInCompat().normalize()

            // Persist as you already do (this writes the record and returns ids)
            val out = useCase.vote(inDto)

            // Load the record so we can compute bits for the receipt
            val rec = useCase.findRecordById(out.recordId)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, ErrorOut("record not found"))

            val b64 = java.util.Base64.getEncoder()
            val bits = rec.u16OneHot.map { bytes -> b64.encodeToString(bytes) }

            val ack = CastAckOut(
                ok = true,
                candidate = inDto.candidate,  // or rec.candidate if you store it
                recordId = rec.id,
                receiptBitsB64 = bits
            )

            call.respond(HttpStatusCode.OK, ack)
        }


        post("/vote/raw") {
            val out = useCase.voteRaw(call.receive(), call.request.queryParameters)
            call.respond<Map<String, Any>>(HttpStatusCode.OK, out)
        }

        get("/user-votes") {
            call.respond<List<List<String>>>(useCase.userVotes())
        }

        get("/receipt/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorOut("missing id"))
            val rec = useCase.findRecordById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorOut("not found"))

            val b64 = java.util.Base64.getEncoder()
            val ui = VoteReceiptUi(
                id = rec.id,
                commitment = rec.commitment,
                receiptBitsB64 = rec.u16OneHot.map { b64.encodeToString(it) }
            )

            call.respond(ui)
        }
    }
}
