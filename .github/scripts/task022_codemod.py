from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/mesas/CuentaMesaRouting.kt"

text = TARGET.read_text(encoding="utf-8")

replacements = {
    " * - `POST   .../sesiones/{sesionId}/cuenta?cajaId=`                                     crear cuenta (completa o división).":
        " * - `POST   .../sesiones/{sesionId}/cuenta?cajaId=` crear cuenta (completa o división).",
    "CuentasMesaListResponse(success = true, sesionMesaId = tri.sesionId, data = result.cuentas),": """CuentasMesaListResponse(
                                        success = true,
                                        sesionMesaId = tri.sesionId,
                                        data = result.cuentas,
                                    ),""",
    "CuentaCreadaResponse(success = true, sesionMesaId = tri.sesionId, data = result.cuenta),": """CuentaCreadaResponse(
                                            success = true,
                                            sesionMesaId = tri.sesionId,
                                            data = result.cuenta,
                                        ),""",
    "call.respond(HttpStatusCode.NotFound, mapOf(\"error\" to \"La sesión no pertenece a esa mesa\"))": """call.respond(
                                    HttpStatusCode.NotFound,
                                    mapOf("error" to "La sesión no pertenece a esa mesa"),
                                )""",
    "call.respond(HttpStatusCode.InternalServerError, mapOf(\"error\" to \"Respuesta inesperada\"))": """call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "Respuesta inesperada"),
                            )""",
    "call.respond(HttpStatusCode.InternalServerError, mapOf(\"error\" to \"No se pudieron listar las cuentas\"))": """call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "No se pudieron listar las cuentas"),
                        )""",
    "call.respond(HttpStatusCode.Conflict, mapOf(\"error\" to \"La sesión no admite cuentas (estado final)\"))": """call.respond(
                                    HttpStatusCode.Conflict,
                                    mapOf("error" to "La sesión no admite cuentas (estado final)"),
                                )""",
    "mapOf(\"error\" to \"Un pedido seleccionado no existe, no está entregado o ya no tiene saldo\"),": """mapOf(
                                        "error" to
                                            "Un pedido seleccionado no existe, no está entregado o ya no tiene saldo",
                                    ),""",
    "else -> call.respond(HttpStatusCode.InternalServerError, mapOf(\"error\" to \"No se pudo crear la cuenta\"))": """else -> call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "No se pudo crear la cuenta"),
                            )""",
    "call.respond(HttpStatusCode.InternalServerError, mapOf(\"error\" to \"No se pudo obtener la cuenta\"))": """call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "No se pudo obtener la cuenta"),
                            )""",
    "else -> call.respond(HttpStatusCode.InternalServerError, mapOf(\"error\" to \"No se pudo cancelar la cuenta\"))": """else -> call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "No se pudo cancelar la cuenta"),
                            )""",
    "call.respond(HttpStatusCode.InternalServerError, mapOf(\"error\" to \"No se pudo cancelar la cuenta\"))": """call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "No se pudo cancelar la cuenta"),
                            )""",
    "if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) return@get": """if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) {
                                return@get
                            }""",
    "if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) return@post": """if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) {
                                return@post
                            }""",
    "val result = cuentaMesaRepository.obtenerCuenta(database, tri.sesionId, tri.mesaId, cuentaId)": """val result =
                                cuentaMesaRepository.obtenerCuenta(
                                    database,
                                    tri.sesionId,
                                    tri.mesaId,
                                    cuentaId,
                                )""",
    "val result = cuentaMesaRepository.cancelarCuenta(database, tri.sesionId, tri.mesaId, cuentaId)": """val result =
                                cuentaMesaRepository.cancelarCuenta(
                                    database,
                                    tri.sesionId,
                                    tri.mesaId,
                                    cuentaId,
                                )""",
}

for old, new in replacements.items():
    if old not in text:
        raise RuntimeError(f"Expected text not found: {old}")
    text = text.replace(old, new)

TARGET.write_text(text, encoding="utf-8")
