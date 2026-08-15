package com.amaxoniaerp

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    configureFrameworks()
    configureHTTP()
    configureSecurity()
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureRouting()
}
