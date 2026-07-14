# Cierre técnico verificable — 2026-07-13

## Resumen ejecutivo

El proyecto queda compilable y firmable para Amaxonia y Banesco Venezuela en debug y release.
La firma de actualización no cambió, R8 y resource shrinking están activos, release no admite
cleartext, los datos sensibles quedan fuera de backups y las guardas de arquitectura, marca y
secretos pasan. Banesco usa ahora la paleta entregada mediante roles Material y aliases neutrales,
sin introducir referencias de marca en `main` ni alterar los colores de Amaxonia.

No se declara 9.9/10. El cierre verificable es **8.2/10**: faltan cobertura global, ejecución real
de instrumentación/smoke tests y eliminar deuda de Detekt/Lint. Compilar AndroidTest no equivale a
ejecutarlo.

## Puntaje

| Área | Inicial | Final | Evidencia / límite |
|---|---:|---:|---|
| Build y flavors | 5.5 | 9.6 | Cuatro APK y dos APK AndroidTest generados |
| Firma y secretos | 4.0 | 9.2 | Certificado/keystore iguales; secreto histórico requiere coordinación externa |
| Seguridad runtime | 4.5 | 9.0 | Keystore Android, migración verificada por diseño, logs seguros, backup/red protegidos |
| Arquitectura | 5.0 | 8.7 | Domain aislado y ViewModels sin infraestructura; quedan consumidores UI del adaptador global |
| Dominio monetario/fiscal | 5.5 | 8.5 | `Money`, value objects y casos de uso; contratos frontera aún usan `Double` por compatibilidad |
| Pruebas | 2.5 | 6.8 | 104 casos únicos; 12.7967% global; instrumentación no ejecutada |
| Compose/UI/accesibilidad | 6.0 | 8.4 | lifecycle-aware, tema semántico y paleta Banesco; falta validación TalkBack/dispositivo |
| Calidad estática | 4.5 | 6.7 | ktlint pasa; Detekt conserva 296 hallazgos y Lint 41–53 warnings |
| CI y automatización | 2.5 | 8.3 | GitLab CI y scripts reproducibles; job estático no queda verde por Detekt |
| **Total ponderado** | **5.9** | **8.2** | Basado en resultados, no en cantidad de archivos |

## Cambios técnicos consolidados

El refactor principal está presente en el commit local `fb4a7c6`: 168 altas, 176
modificaciones, 7 eliminaciones y 2 renombres. El listado completo y auditable se obtiene con:

```bash
git show --name-status fb4a7c6
```

Incluye almacenamiento seguro, logging redactado, configuración release, schemas Room,
repositorios/casos de uso, dominio monetario, coordinadores de UI, pruebas, Kover, Detekt, ktlint,
scripts y GitLab CI.

Cambios Banesco posteriores y todavía no confirmados:

- `app/src/banescoVenezuela/res/values/brand_colors.xml`: paleta Banesco light/dark.
- `app/src/amaxonia/res/values/brand_colors.xml`: roles faltantes con los defaults previos.
- `app/src/main/java/com/amaxonia/pos/ui/theme/Theme.kt`: roles Material completos y neutrales.
- `scripts/verify-brand-resources.sh`: paridad de aliases y valores Banesco auditables.
- `scripts/quality-check.sh` y `scripts/verify-all-flavors.sh`: integración de la guarda.
- Este reporte.

No se eliminaron cambios preexistentes de IDE, launchers, `LoginScreen.kt`, `DashboardScreen.kt`
ni source sets. No se ejecutaron `git clean`, reset, checkout destructivo ni reescritura de
historial. El keystore físico sigue en `app/amaxonia-release-key.jks` (2,792 bytes), ignorado por
Git y no rastreado.

## UI Banesco

La paleta de entrada se asignó semánticamente:

| Rol | Valor |
|---|---|
| primary / acción | `#0C7953` |
| primaryContainer | `#E7F3EE` |
| primary fuerte | `#086642` |
| secondary/navy | `#001689` |
| navy fuerte | `#00116B` |
| error | `#E0271E` |
| background / surface | `#F4F6F7` / `#FFFFFF` |
| outline | `#DDE3E6` |
| texto principal/secundario | `#263238` / `#5F6B72` |

También se actualizaron gradiente, splash y roles oscuros. No se activó color dinámico por
defecto para no sustituir la identidad corporativa. `main` conserva cero referencias
`banesco_*`/`amaxonia_*`.

## Seguridad

- Firma leída desde entorno/propiedades privadas; sin fallback de contraseña.
- `keystore.properties` y `*.jks` ignorados; `git ls-files -- '*.jks' '*.keystore'` devuelve vacío.
- `AndroidKeystoreSecureKeyValueStore` cifra tokens/gateway keys con Android Keystore.
- `VerifiedSecureValueWriter` exige escritura y lectura correcta antes de borrar plaintext legado.
- `SafeLog` centraliza redacción; la guarda no encontró logging Android directo ni secretos
  interpolados.
- Backup excluye preferencias, bases de datos, DataStore y crash logs en cloud y device transfer.
- Release usa `usesCleartextTraffic="false"`; HTTP debug está limitado por Network Security Config.
- R8 y resource shrinking están activos. Mappings finales: Amaxonia 55,000,594 bytes; Banesco
  55,003,762 bytes.

La credencial histórica continúa en commits antiguos. No se reescribió el historial porque exige
coordinación de GitLab/Google Play y autorización explícita.

## Arquitectura y casos de uso

La guarda verifica: `domain` sin Android/data; ViewModels sin DAO, HTTP, Room, WorkManager,
serializadores ni `DependencyContainer`; UI sin DTO/entidad de persistencia; Flow lifecycle-aware.

Extracciones principales: `ValidatePaymentUseCase`, `CalculateSaleTotalsUseCase`,
`BuildSaleItemsUseCase`, `BuildSaleRequestUseCase`, `ExecuteGatewayPaymentUseCase`,
`QueueOfflineInvoiceUseCase`, `SynchronizePendingInvoicesUseCase`,
`ConfirmFiscalDocumentUseCase`, `PrintInvoiceUseCase`, `HandlePaymentFailureUseCase`,
`PrepareSaleUseCase`, `ExecutePaymentFlowUseCase`, casos de login/carrito/notas de crédito y
servicio de impresión de cierre.

ViewModels refactorizados o descargados mediante coordinadores/casos de uso: Payment, Dashboard,
Cart, Login, Cierre de Caja, Credit Notes y Draft Invoices. `DependencyContainer` permanece como
adaptador de composición en algunas Routes/Screens; no se eliminó con una migración big-bang.

## Pruebas

Resultado: **104 tests unitarios únicos**, ejecutados en las cuatro combinaciones flavor/build
type: 416 ejecuciones, 0 fallos. Categorías:

- Money, value objects, impuestos, descuentos, promociones y redondeo.
- JSON golden de venta y nota de crédito.
- Tickets de factura/cierre Panamá.
- Flujo de pago online/offline, gateway, confirmación fiscal, impresión y errores.
- Sincronización parcial, reintento, leasing e idempotencia.
- Login, PaymentViewModel y coordinadores de carrito/dashboard.
- Escritura segura verificada, logs y captura tipada de errores.

Hay 4 casos instrumentados en 3 archivos: migraciones Room 1…9→10, Android Keystore y
SyncScheduler/WorkManager. Los APK compilaron para ambos flavors, pero no se ejecutaron porque
`adb devices` no mostró dispositivo.

Cobertura Kover Amaxonia Debug:

| Área | Líneas | Ramas |
|---|---:|---:|
| Payment use cases | 720/726 = 99.17% | 307/398 = 77.14% |
| Sync use cases | 40/40 = 100% | 12/14 = 85.71% |
| Money | 46/52 = 88.46% | 28/42 = 66.67% |
| Combinado crítico anterior | 806/818 = 98.53% | 347/454 = 76.43% |
| Aplicación global | **12.7967%** | No usado como criterio global |

Por tanto no se alcanzaron 75% global ni 85% de ramas en todo el núcleo crítico.

## Resultados exactos

- Checkpoint Kotlin/ktlint/test puntual: `BUILD SUCCESSFUL in 1m 31s`, 61 tareas.
- Primera matriz desde `clean`: falló en 22m 56s porque Kover no encontró su archivo temporal al
  concurrir con test (`kover-agent.args`). No fue un fallo de aserción ni producción.
- Cuatro suites aisladas: `BUILD SUCCESSFUL in 2m 37s`, 136 tareas, 9 ejecutadas.
- Cuatro builds + cuatro lint + dos AndroidTest APK + ktlint:
  `BUILD SUCCESSFUL in 6m 35s`, 278 tareas, 46 ejecutadas.
- Kover aislado: `BUILD SUCCESSFUL in 2m 16s`, 39 tareas, 5 ejecutadas.
- Verificación release/firma: `BUILD SUCCESSFUL in 22s`, 103 tareas, 7 ejecutadas.
- Guardas de arquitectura, marca y seguridad: PASS.
- Detekt: FAIL, 296 hallazgos: 148 MagicNumber, 52 LongMethod, 37 ReturnCount,
  20 TooManyFunctions, 16 LongParameterList, 15 CyclomaticComplexMethod y
  8 TooGenericExceptionCaught.
- Lint (0 errores): Amaxonia debug 41 warnings; Amaxonia release 52; Banesco debug 53;
  Banesco release 52. En Banesco debug: 17 GradleDependency, 13 NewerVersionAvailable,
  7 UnusedResources, 6 IconLocation, 3 Aligned16KB, 2 UseKtx, 2 ObsoleteSdkInt,
  1 VectorRaster y 1 versión AGP.
- ktlint: PASS.

## Firma y hashes

Keystore SHA-256 final (igual a Fase 0):
`2e586dc7d74c1aa948ed524eb4182f89e77b90a6062a2db4c53f1fee17ba554b`.

Ambos release:

- Certificado SHA-1: `3e5e214f98d4d8b32499bbdf795aa7fdd9b3f3cf`.
- Certificado SHA-256: `928f37e296148f36a8f1e6baf0d05b90e2ea7c3f5a4fa66975e62de6d07bc5d9`.

Hashes APK iniciales de Fase 0:

| Variante | Inicial |
|---|---|
| Amaxonia debug | `de08d0ab7c07eecde3fcee5df20a46a47da33c81064aada82ea3657d0d5f22d8` |
| Amaxonia release | `113846c381206db3b52bbcbfc7b44e4e6bff43592ba2dc1578fbccaa8ecfebcc` |
| Banesco debug | `4630bbde78e4f046143a8cb598a5c44cce8b9d2fff4526e5e1a10a285318ed5b` |
| Banesco release | `3eb750ba5abe465ee25564d8fc6261a672e6091a5a79e4ac758f57336bf8faa3` |

Hashes APK finales:

| Variante | Final |
|---|---|
| Amaxonia debug | `0a68691b576e7cf6c05412c9454554387cde30aa1bcecbb14a9996c2a905365a` |
| Amaxonia release | `76efd8ed4ebd610e8dacf814dfd574d3f4ff7ed2d9825ef2bd7b5b9b671f6a60` |
| Banesco debug | `449e6fc2f43a1c99430ae10aaba4879b278f6b156b27a0ef14b89c84db27fb5e` |
| Banesco release | `82d43cc977373e241a9f268b09638ef7127c7b06dced00f08a43d36422585ad6` |
| AndroidTest Amaxonia | `eb55703a7aa98f5f1e5cab2ea43adda849ac5cb10e381e0de6e5b4b78b2e70bf` |
| AndroidTest Banesco | `88e5b574fd564bff7e92808a3644a7c9a1b1015ce37b0acf5a53b0d3cd7cab57` |

Un cambio de hash es esperado por código/recursos/R8; la compatibilidad de actualización se
demuestra por el certificado idéntico, no por un APK byte a byte idéntico.

## CI y reproducción

GitLab CI está en `.gitlab-ci.yml`. Variables masked/protected requeridas:
`AMAXONIA_KEYSTORE_BASE64`, `AMAXONIA_KEYSTORE_PASSWORD`, `AMAXONIA_KEY_ALIAS` y
`AMAXONIA_KEY_PASSWORD`.

```bash
./scripts/verify-brand-resources.sh
./scripts/verify-architecture.sh
./scripts/verify-security.sh
./scripts/verify-all-flavors.sh
./scripts/test-critical.sh
./scripts/verify-release.sh
./gradlew :app:ktlintCheck :app:detekt
./gradlew :app:koverHtmlReportAmaxoniaDebug :app:koverXmlReportAmaxoniaDebug :app:koverLogAmaxoniaDebug
```

Instrumentación desde Windows/dispositivo:

```text
gradlew.bat :app:connectedAmaxoniaDebugAndroidTest
gradlew.bat :app:connectedBanescoVenezuelaDebugAndroidTest
```

La matriz manual completa está en `docs/quality/device-smoke-test-matrix.md`; ningún flujo figura
como aprobado sin dispositivo, impresora, pasarela sandbox y APK anterior.

## Bloqueos y deuda restante

1. Ejecutar migraciones, Keystore y WorkManager instrumentados en dispositivo/emulador.
2. Ejecutar smoke tests de actualización, venta online/offline, pasarela, impresión y notas de crédito.
3. Subir cobertura global de 12.7967% a 75% y ramas críticas de 76.43% a 85%.
4. Corregir 296 hallazgos Detekt y los warnings Lint accionables sin baseline general.
5. Reducir consumidores UI de `DependencyContainer` hasta una raíz de composición.
6. Validar TalkBack, contraste, fuente 200% y layouts POS/tablet físicamente.
7. Coordinar limpieza/rotación de secretos históricos sin cambiar el certificado de actualización.
8. Ejecutar el pipeline GitLab con secretos protegidos; hoy `static-quality` falla por Detekt.

Hasta completar esos puntos y volver a ejecutar toda la matriz, el proyecto no satisface los
criterios auditables de 9.9/10.
