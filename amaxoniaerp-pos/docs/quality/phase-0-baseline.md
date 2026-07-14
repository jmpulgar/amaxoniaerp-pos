# Fase 0 — Línea base técnica verificable

Fecha de captura: 2026-07-12 (America/Bogota)

## Alcance

Esta fase establece una referencia auditable antes de modificar arquitectura, persistencia,
pagos, facturación, impresión o sincronización. No cambia contratos de red, schemas Room,
formatos fiscales, redondeos ni el certificado de firma.

El repositorio ya contenía cambios locales no confirmados al iniciar esta fase. En particular,
los source sets de marca y los aliases `brand_*` ya estaban presentes en el árbol de trabajo.
Esta fase los valida; no atribuye autoría ni reemplaza esos cambios.

## Contrato de recursos de marca

El código común bajo `app/src/main` tiene **0** referencias a recursos `banesco_*` o
`amaxonia_*`.

Recursos neutrales consumidos desde código común:

- `brand_logo`
- `brand_mark`
- `brand_name`
- `brand_logo_description`
- `brand_receipt_name`
- `brand_print_test_message`
- `brand_welcome_*`
- `brand_website_*`
- `brand_support_*`
- 36 roles de color `brand_*`

Ambos source sets definen exactamente `brand_logo`, `brand_mark`, los diez strings de marca
y los 36 colores requeridos:

- `app/src/amaxonia/res/`
- `app/src/banescoVenezuela/res/`

Consumidores comunes revisados:

- `app/src/main/java/com/amaxonia/pos/ui/login/LoginScreen.kt`
- `app/src/main/java/com/amaxonia/pos/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/amaxonia/pos/ui/welcome/WelcomeScreen.kt`
- `app/src/main/java/com/amaxonia/pos/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/amaxonia/pos/ui/theme/Theme.kt`
- `app/src/main/java/com/amaxonia/pos/data/printer/TheFactoryPrinterImpl.kt`
- `app/src/main/res/values/themes.xml`

## Matriz de build ejecutada

Se ejecutó una única invocación Gradle con las siguientes tareas:

```text
:app:assembleAmaxoniaDebug
:app:assembleAmaxoniaRelease
:app:assembleBanescoVenezuelaDebug
:app:assembleBanescoVenezuelaRelease
:app:testAmaxoniaDebugUnitTest
:app:testAmaxoniaReleaseUnitTest
:app:testBanescoVenezuelaDebugUnitTest
:app:testBanescoVenezuelaReleaseUnitTest
:app:lintAmaxoniaDebug
:app:lintAmaxoniaRelease
:app:lintBanescoVenezuelaDebug
:app:lintBanescoVenezuelaRelease
```

Resultado exacto: `BUILD SUCCESSFUL in 14m 31s`; 236 tareas accionables,
97 ejecutadas y 139 `UP-TO-DATE`.

También se ejecutaron:

```text
:app:assembleAmaxoniaDebugAndroidTest
:app:assembleBanescoVenezuelaDebugAndroidTest
```

Resultado exacto: `BUILD SUCCESSFUL in 57s`; 106 tareas accionables,
58 ejecutadas y 48 `UP-TO-DATE`.

## APKs de referencia

Los binarios permanecen en `app/build/outputs/apk/` y no se incorporan a Git. Sus hashes
permiten identificar exactamente esta captura local:

| Variante | SHA-256 del APK |
|---|---|
| Amaxonia debug | `de08d0ab7c07eecde3fcee5df20a46a47da33c81064aada82ea3657d0d5f22d8` |
| Amaxonia release | `113846c381206db3b52bbcbfc7b44e4e6bff43592ba2dc1578fbccaa8ecfebcc` |
| Banesco Venezuela debug | `4630bbde78e4f046143a8cb598a5c44cce8b9d2fff4526e5e1a10a285318ed5b` |
| Banesco Venezuela release | `3eb750ba5abe465ee25564d8fc6261a672e6091a5a79e4ac758f57336bf8faa3` |
| AndroidTest Amaxonia debug | `cd1e57fff93fcec92b0a2717924348956630de98de01e5106b192bf1fb5f5b10` |
| AndroidTest Banesco debug | `7951d302fce6b63684ac309d7fa4adf185bc55f2a7ad2c137fe72ebce1ee5062` |

Los hashes de APK no se consideran builds reproducibles entre máquinas: son identificadores
de los artefactos de esta captura. La identidad compatible para futuras actualizaciones es el
certificado documentado en `docs/security/signing-baseline.md`.

## Pruebas existentes

Inventario único:

| Archivo | Casos | Cobertura funcional |
|---|---:|---|
| `ExampleUnitTest.kt` | 1 | Placeholder `2 + 2` |
| `PrinterTypePolicyTest.kt` | 3 | Política de impresoras por país |
| `PanamaInvoiceTicketFormatterTest.kt` | 3 | Texto, QR y fallback de ticket Panamá |
| `ExampleInstrumentedTest.kt` | 1 | Placeholder: comprueba package name |

Los 7 casos unitarios se ejecutaron en debug y release de ambos flavors: 28 ejecuciones,
0 fallos, 0 errores y 0 omitidos.

El único test instrumentado compiló en APKs separados para ambos flavors. No se ejecutó porque
ADB no pudo iniciar dentro del entorno WSL (`/dev/bus/usb` no disponible y socket no permitido).
No se declara por tanto evidencia de ejecución en dispositivo.

No existe una tarea Jacoco/Kover ni cobertura configurada. Cobertura de Fase 0: **no disponible**.

## Android Lint

| Variante | Errores | Advertencias | Resultado de tarea |
|---|---:|---:|---|
| Amaxonia debug | 0 | 107 | PASS |
| Amaxonia release | 0 | 106 | PASS |
| Banesco Venezuela debug | 0 | 107 | PASS |
| Banesco Venezuela release | 0 | 106 | PASS |

Principales familias de advertencias de Amaxonia debug:

- `DefaultLocale`: 48
- `UnusedResources`: 14
- `GradleDependency`: 14
- `NewerVersionAvailable`: 9
- `IconLocation`: 5
- `MonochromeLauncherIcon`: 4
- `Aligned16KB`: 3
- `StaticFieldLeak`: 2

Lint está verde a nivel de tarea, pero la deuda no es cero. No se añadieron suppressions,
baselines ni exclusiones para obtener este resultado.

## Advertencias de compilación y entorno

- `CartScreen.kt:444` usa el overload obsoleto de `Modifier.menuAnchor()`.
- `local.properties` contiene un `sdk.dir` no válido para este entorno; Gradle encontró el SDK
  por otra configuración y pudo completar la matriz.

Ambas se registran sin corregir en esta fase para no mezclar cambios no imprescindibles.

## Estado de Fase 0

**VERDE para build, unit tests, compilación de AndroidTest y Android Lint.**

No significa 9.9/10: la seguridad, cobertura, caracterización de flujos críticos y deuda lint
permanecen abiertas y están documentadas antes de cualquier refactorización.
