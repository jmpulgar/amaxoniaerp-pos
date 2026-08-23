# 04: Contrato final y evidencia de integración

**What to build:** Verificación de cierre del conjunto: la superficie quedó exactamente como la spec promete y nada sobrevivió por accidente. La interfaz del workflow de sesión tiene exactamente tres operaciones (abrir, cerrar, estado); el repositorio de caja solo conserva queries Archetype A legítimas y primitivas usadas; no quedan restos de los use cases eliminados ni código muerto. Es la puerta de merge de la cadena completa.

**Blocked by:** 03 — Apertura atómica: auto-close + inserción + relectura en una fase.

**Status:** completada

- [x] Interfaz del workflow = exactamente 3 operaciones (open/close/status), verificada contra la spec aprobada
- [x] Sin métodos muertos en el repositorio (recordApertura y persistCierre eliminados) ni referencias residuales a los use cases eliminados
- [x] Hallazgos detekt/ktlint en cero, sin baseline ni suppressions nuevos
- [x] Ratchet de cobertura JaCoCo en verde
- [x] Evidencia de comandos: gradlew build + jacocoTestCoverageVerification + test + detekt + ktlintCheck en verde
- [x] Respuesta explícita "NO" a ¿cambia alguna decisión de negocio?, salvo el delta sancionado de reversión listado
