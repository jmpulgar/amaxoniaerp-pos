package com.amaxoniaerp

import com.amaxoniaerp.composition.buildAppDependencies
import io.ktor.server.application.Application

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    configureHTTP()
    configureSecurity()
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureRouting(buildAppDependencies(this))
}
