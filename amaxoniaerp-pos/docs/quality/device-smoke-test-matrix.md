# Matriz de smoke tests en dispositivo

Estado: **pendiente de ejecución externa**. Compilar un `androidTest` no equivale a ejecutar estas pruebas.

## Preparación

1. Conservar un APK de producción anterior firmado y una copia de datos de prueba no productivos.
2. Usar un terminal POS de cada modelo soportado, una impresora fiscal HKA, una Sunmi compatible y credenciales de sandbox.
3. Registrar versión Android, modelo, flavor, versión instalada, hora y evidencia de cada resultado.
4. No usar tarjetas reales ni información personal real.

## Matriz obligatoria

| Flujo | Amaxonia | Banesco Venezuela | Evidencia esperada |
|---|---:|---:|---|
| Instalación limpia | Pendiente | Pendiente | Instalación y arranque sin crash |
| Actualización sobre APK anterior | Pendiente | Pendiente | `adb install -r` exitoso; datos conservados |
| Compatibilidad de firma | Pendiente | Pendiente | Actualización aceptada sin desinstalar |
| Login válido e inválido | Pendiente | Pendiente | Estados y mensajes correctos; sin secretos en logcat |
| Selección de empresa | Pendiente | Pendiente | Sesión correcta tras reinicio |
| Selección/apertura de caja | Pendiente | Pendiente | Caja y secuencia correctas |
| Venta online | Pendiente | Pendiente | Una factura remota y totales esperados |
| Venta offline | Pendiente | Pendiente | Pendiente local con identificador estable |
| Recuperación de conexión | Pendiente | Pendiente | WorkManager reenvía automáticamente |
| Reenvío de factura pendiente | Pendiente | Pendiente | Estado final `SENT` |
| Prevención de duplicados | Pendiente | Pendiente | Repetir worker no crea otra factura |
| Pago por pasarela | Pendiente | Pendiente | Aprobación, retorno y factura únicos |
| Error/cancelación de pasarela | Pendiente | Pendiente | No factura ni cobra silenciosamente |
| Impresión y reimpresión | Pendiente | Pendiente | Ticket/documento fiscal idéntico al esperado |
| Nota de crédito | Pendiente | Pendiente | Totales, stock y documento fiscal correctos |
| Cierre y reapertura | Pendiente | Pendiente | Estado recuperable; trabajo pendiente continúa |
| Migración de preferencias inseguras | Pendiente | Pendiente | Valor cifrado verificable; plaintext eliminado después |
| Backup/restauración | Pendiente | Pendiente | Tokens, DB fiscal y pasarela no se restauran |

## Instrumentación

Desde Windows/Android Studio con emulador o dispositivo visible:

```text
gradlew.bat :app:connectedAmaxoniaDebugAndroidTest
gradlew.bat :app:connectedBanescoVenezuelaDebugAndroidTest
```

Desde WSL, solo cuando `adb devices` muestre el dispositivo:

```bash
./gradlew :app:connectedAmaxoniaDebugAndroidTest
./gradlew :app:connectedBanescoVenezuelaDebugAndroidTest
```

Las pruebas `AppDatabaseMigrationInstrumentedTest` recorren cada origen 1…9 hasta 10 y validan datos, defaults y tablas. `SecureStorageInstrumentedTest` comprueba Android Keystore y ausencia de plaintext.
