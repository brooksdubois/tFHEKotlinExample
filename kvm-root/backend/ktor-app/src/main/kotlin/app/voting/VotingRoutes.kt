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
            val id = call.parameters["id"]!!
            val receipt = useCase.receiptFor(id)
            if (receipt == null)
                return@get call.respond(HttpStatusCode.NotFound, ErrorOut("not found"))
            call.respond<Map<String, String>>(mapOf("id" to id, "receiptCtB64" to receipt))
        }
    }
}
