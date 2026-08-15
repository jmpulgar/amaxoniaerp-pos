package com.amaxoniaerp

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.path
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.*
import org.slf4j.event.*

fun Application.configureHTTP() {
    routing {
        swaggerUI(path = "openapi")
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("MyCustomHeader")
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }
}
