# Historial de versiones — Flow ERP Kiosko (`amaxoniaerp-kiosk`)

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado [SemVer](https://semver.org/lang/es/).
Mientras la versión sea `0.x.y` la app está en etapa de pruebas: cualquier versión puede cambiar contratos o flujos.

**Cómo publicar una versión nueva**
1. Subir `versionCode` (entero, siempre mayor que el anterior) y `versionName` en `app/build.gradle.kts`.
2. Mover lo de `[Sin publicar]` a una sección nueva `[x.y.z] — AAAA-MM-DD` con el `versionCode`, el APK y su SHA-256.
3. Generar con `.\gradlew.bat testFlowerpDevDebugUnitTest assembleFlowerpProdRelease --no-parallel`.
4. Indicar los requisitos de backend / admin PHP que esa versión necesita.

---

## [Sin publicar]

_Nada todavía._

---

## [0.0.2] — 2026-10-06

| Dato | Valor |
|---|---|
| `versionCode` | 2 |
| Variante | `flowerpProdRelease` |
| APK | `app/build/outputs/apk/flowerpProd/release/app-flowerp-prod-release.apk` |
| SHA-256 | `C9CFFE5709911540961E01222BC275EAE3D37D8637EEC6AF465FED4AB78F8476` |

### Requisitos
- Backend desplegado con el commit del kiosco (endpoints `/api/v1/kiosk/*`).
- Base de la empresa migrada con `db/2026/2026-10-06-KIOSCO-AUTOSERVICIO.sql` (admin PHP, `execute_queries_pa.py`).
- Menú "Kioscos" registrado con `db/2026/2026-10-06-INSERT-INTO-MODULO-KIOSCOS.sql` (selectra_conf_pyme).

### Corregido
- La URL del servidor por defecto apuntaba a `https://api.amaxonia.com/`, que no existe. Ahora es `https://api.listoerp.app/` (el mismo backend del POS).
- La pantalla de emparejamiento ahora explica dónde se genera el código (Configuración → Kioscos → «Generar código») y da un ejemplo de base de datos.
- El backend compara el vencimiento del código con el reloj de la base de datos: el servidor del backend (Caracas, UTC-4) y la base PA (UTC-5) tienen husos distintos y el código aparecía vencido al instante.

---

## [0.0.1] — 2026-10-06

Primera versión de pruebas para SUNMI K2 (Android 9 / API 28, vertical 1080×1920).

| Dato | Valor |
|---|---|
| `versionCode` | 1 |
| Variante | `flowerpProdRelease` (marca Flow ERP, backend `https://api.amaxonia.com/`) |
| Firma | `app/flowerp-kiosk-release.jks` (alias `flowerp-kiosk`, fuera de git) |
| APK | `app/build/outputs/apk/flowerpProd/release/app-flowerp-prod-release.apk` (8,2 MB) |
| SHA-256 | `93A1CC9D4689BFF44CB4096356C6FEA1DCCFAB0A122FB9CBCC9DB06B4EB1CDA9` |

### Requisitos
- **Backend** `amaxoniaerp-backend` con los endpoints de kiosco (B1–B9) y Yappy (`/orders/{id}/yappy`, `paymentMethods` en `/config`). Sin cambios de esquema obligatorios.
- **Admin PHP**: caja con `yappy_device_id` / `yappy_group_id`, credenciales Yappy en Parámetros Generales y forma de pago `YAPPY`.
- **Opcional**: columna `parametros_generales.yappy_tipo_qr` (`doc/runbooks/optional_yappy_tipo_qr.sql`) para elegir QR Dinámico/Híbrido desde el admin; sin ella se usa Dinámico.

### Agregado
- Flujo completo de autoservicio: Emparejamiento → Attract (video/imagen en loop) → Comer aquí / Para llevar → Menú → Personalizador de combos y modificadores → Revisión → Portamesa (solo destino MESAS + Comer aquí) → Cliente (Consumidor Final o RUC/Cédula) → Método de pago → Pago → Número de pedido.
- **Pago con Yappy**: QR en pantalla, consulta del estado cada 3 s, cuenta regresiva, cancelación, nuevo código y cambio de método. El servidor confirma el pago con Yappy antes de facturar; si el cliente cancela después de pagar, el pago se registra igual.
- Pantalla de selección de método de pago (se salta si solo hay uno).
- Outbox local de pagos aprobados (Room) con reintento automático al iniciar y al volver al Attract.
- Impresión del recibo fiscal 80 mm (CUFE/QR) en la impresora Sunmi del K2.
- Temporizador de inactividad (60 s + aviso de 20 s), modo pantalla baja, alto contraste y textos ES/EN.
- Modo kiosco con Lock Task (Device Owner), arranque automático y menú de administración (5 toques + clave).
- Flavor de marca `flowerp`: colores Flow ERP (índigo, azul, lavanda), isotipo e ícono del POS.
- Diseño estilo autoservicio de comida rápida: botones de 120 dp, tarjetas grandes, animaciones y transiciones entre pantallas.

### Corregido (auditoría Android 9 / K2)
- Credenciales del kiosco guardadas sin cifrar: ahora usan `EncryptedSharedPreferences`, con recuperación automática si el KeyStore se corrompe.
- La impresión se daba por exitosa aunque la impresora no estuviera conectada. Ahora: reconexión con reintentos, espera de calentamiento en arranque en frío, detección de sin papel / tapa abierta / cortador y confirmación real de cada recibo.
- El temporizador de inactividad podía reiniciar el kiosco a mitad de un cobro.
- Procesos de pantallas anteriores seguían corriendo (ViewModels sin ciclo de vida); restos del cliente anterior tras volver al Attract.
- El monto de pago mostraba el subtotal sin impuestos en lugar del total cotizado.
- Orden del flujo: el portamesa aparecía para cualquier "Comer aquí" y después del cliente.
- La terminal de tarjeta simulada estaba activa en producción (ahora solo en `dev`).
- Las fotos de productos y banners con rutas relativas del servidor no cargaban.
- El emparejamiento en producción sugería la dirección del emulador.
- El aviso "Pide tu comprobante en el mostrador" no alcanzaba a mostrarse cuando fallaba la impresión.

### Limitaciones conocidas
- Sin terminal de tarjeta real: en producción solo se cobra con Yappy.
- Pendiente de validar en un K2 físico: impresora, fotos reales, fluidez y Yappy real (la cancelación usa `PUT /transaction/{id}`, no confirmada contra la API).
