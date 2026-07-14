# Fase 0 — Inventario de seguridad runtime y release

Este documento es solo un inventario. No se cambió runtime, firma, backup, red, logging ni R8 en
Fase 0.

## Hallazgos críticos

### Firma

- El keystore release está versionado.
- `storePassword` y `keyPassword` están definidos como literales en `app/build.gradle.kts`.
- La exposición también existe en el historial Git.
- Identidad y restricciones de compatibilidad: `docs/security/signing-baseline.md`.

### Datos persistidos

`LocalStore` guarda en Preferences DataStore sin una abstracción cifrada:

- `auth_snapshot`, que contiene token de autenticación.
- `company_session`, que contiene token de empresa y nombres de bases.
- `the_factory_gateway_key`.
- configuración de impresora y pasarela.
- último resultado de pago.

### Backup

- `android:allowBackup="true"` está activo.
- `backup_rules.xml` no contiene exclusiones efectivas.
- `data_extraction_rules.xml` conserva el TODO de plantilla y no excluye datos sensibles.

### Red

- `android:usesCleartextTraffic="true"` está activo en el manifest común y por tanto también en
  release.
- Debug usa una URL HTTP local; release configura una URL HTTPS.
- No existe Network Security Config separada por build type.

### Logs sensibles

Se detectaron logs directos con:

- `gatewayKey`.
- identificación del cliente y su versión normalizada.
- RIF del comercio.
- comandos/envelopes enviados a HKA.
- respuesta raw de la pasarela.
- JSON de resultado HKA.
- cuerpos de error devueltos por el endpoint de ventas.

Sitios prioritarios:

- `PaymentViewModel.kt:968-977` y `PaymentViewModel.kt:1009`.
- `TheFactoryRapidPayClient.kt:124`.
- `SalesApiImpl.kt:44`.

La búsqueda amplia identificó 22 sitios de log potencialmente sensibles. La fase de corrección
debe clasificarlos individualmente y no limitarse a una sustitución textual.

### Release hardening

- `isMinifyEnabled = false` en release.
- No existe evidencia todavía de reglas R8 verificadas para Ktor, Kotlin Serialization, Room,
  Sunmi y el AAR fiscal HKA.
- `AppDatabase` usa `exportSchema = false`, lo que impide conservar schemas para pruebas completas
  de migración.

## Riesgos de corrección

- Cambiar almacenamiento sin migrar datos puede cerrar sesiones o perder configuración de pasarela.
- Cambiar backup puede afectar transferencias de dispositivo, aunque es necesario excluir secretos.
- Activar R8 sin pruebas puede romper serialización/reflexión o integraciones con SDKs propietarios.
- Deshabilitar cleartext globalmente puede afectar debug local; debe separarse por build type.
- Eliminar logs fiscales sin reemplazo seguro puede dificultar soporte; se necesita logging redactado
  y condicionado por build.

## Estado

Todos estos puntos quedan abiertos para la fase de seguridad. Ninguno se ocultó mediante suppress,
exclusión, baseline o desactivación de verificaciones.
