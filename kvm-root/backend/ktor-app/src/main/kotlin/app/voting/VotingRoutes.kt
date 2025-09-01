package app.voting

import app.api.ErrorOut
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kvm.voting.BlockSummary
import kvm.voting.VoteOut

fun Application.votingRoutes(useCase: VotingUseCase = VotingUseCase()) {
    routing {
        get("/server-key") {
            call.respondBytes(bytes = useCase.serverKey(), contentType = ContentType.Application.OctetStream)
        }

        get("/blocks") {
            call.respond<List<BlockSummary>>(useCase.blocks())
        }

        post("/vote") {
            val out = useCase.vote(call.receive())
            val rec = useCase.findRecordById(out.recordId)
                ?: return@post call.respond(HttpStatusCode.NotFound)

            // derive a cryptographic receipt
            val receipt = receiptForRecord(rec, electionId = "election-1") // or from config
            out.recieptB64 = receipt.toString()
            call.respond<VoteOut>(HttpStatusCode.OK, out)
        }

        post("/vote/raw") {
            val out = useCase.voteRaw(call.receive(), call.request.queryParameters)
            call.respond<Map<String, Any>>(HttpStatusCode.OK, out)
        }

        get("/user-votes") {
            call.respond<List<List<String>>>(useCase.userVotes())
        }

        get("/receipt/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val rec = useCase.findRecordById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            val receipt = receiptForRecord(rec, electionId = "election-1")
            call.respond(receipt)
        }
    }
}
