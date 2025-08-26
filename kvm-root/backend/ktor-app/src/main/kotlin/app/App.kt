package app

import app.api.ErrorOut
import app.voting.votingRoutes
import app.mpc.mpcRoutes
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import kvm.native.NativeLoader

fun Application.module() {
    NativeLoader.load()

    install(CORS) {
        allowHost("localhost:3000", schemes = listOf("http"))
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true
        allowCredentials = true
        maxAgeInSeconds = 86400
    }
    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorOut(cause.message ?: "invalid input"))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorOut("server error"))
            throw cause
        }
    }

    // Routes split into modules
    votingRoutes()
    mpcRoutes()
}

/** Block-body main avoids the “main() should return Unit” false positive in some IDE states. */
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}
