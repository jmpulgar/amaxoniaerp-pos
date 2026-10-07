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

## [0.0.9] — 2026-10-07

| Dato | Valor |
|---|---|
| `versionCode` | 9 |
| Variante | `flowerpProdRelease` |
| APK | `app/build/outputs/apk/flowerpProd/release/app-flow-kiosko-v0.0.9.apk` |
| SHA-256 | `6067FD7EC4FD6E07CFC623DC1FB10AFA7D0F3C31C8BF5F1359AE575D09810194` |

### Requisitos
- **Backend nuevo**: envía la imagen propia de cada categoría (`iconUrl`, desde `departamento.foto`) y sirve `/api/data/{país}/{empresa}/departamento/{archivo}` (desde `DATA_BASE_PATH` o redirigiendo a `ASSETS_BASE_URL`). Incluye también lo de 0.0.8.
- Sin migraciones: la columna `departamento.foto` ya existe en el ERP.

### Corregido
- **Imágenes de las categorías:** ahora se muestra la foto cargada en Mantenimiento → Departamento del ERP. Antes el backend no la enviaba y el kiosko usaba la foto del primer producto de la categoría. Si un departamento no tiene foto, se sigue usando la del primer producto.

---

## [0.0.8] — 2026-10-07

| Dato | Valor |
|---|---|
| `versionCode` | 8 |
| Variante | `flowerpProdRelease` |
| APK | `app/build/outputs/apk/flowerpProd/release/app-flow-kiosko-v0.0.8.apk` |
| SHA-256 | `11C8A95AD8CACE7317DBFC5A107AB4D541F5E5A3EB2F06AC00B295C35449C57F` |

### Requisitos
- **Backend nuevo** (envía `cardOptions` en `/config` y acepta `paymentMethodId` en `/pay`). Con el backend anterior el kiosko no muestra tarjetas y vuelve a ir directo a Yappy.
- Sin migraciones de base de datos de la empresa: las tarjetas salen de `caja_forma_pago` (activas, visibles en POS, siglas TDC/TDD/AMEX/TARJETA o `FormaPagoFact` 03/04).
- La base local del kiosko pasa a la versión 3 (migración automática, solo agrega una columna).

### Agregado
- **Formas de pago con tarjeta de la empresa** (p. ej. VISA, MASTERCARD, Tarjeta de débito) con su logo del ERP, junto a Yappy.
- **Pago sin pasarela (pruebas):** si el equipo no tiene terminal de tarjeta, al elegir una tarjeta se muestra una confirmación (total, forma de pago y aviso de que **no se cobra nada**). «Confirmar pago» genera el pedido, la factura y el recibo con la forma de pago elegida (referencia `SIN PASARELA`).
- Personalizador: «Paso X de N» con barra de progreso, lista de pasos tocable para volver a cualquier paso anterior y, en «Revisar orden», un botón «Cambiar» por cada grupo.

### Cambiado
- **Botones del personalizador** en una barra fija: «Cancelar» a la izquierda, «Atrás» al centro (deshabilitado en el primer paso, nunca cambia de lugar) y «Siguiente» / «Agregar a mi orden · $X» a la derecha. Volver atrás conserva lo elegido.
- **«Revisa tu orden» más compacta y moderna:** filas de ~128 dp con foto pequeña, opciones en una sola línea, total de la línea y un selector [− 1 +] (el − se vuelve papelera en 1); sin la ✕ de la esquina ni tarjetas pesadas; selector Comer aquí / Para llevar más delgado; resumen Subtotal / ITBMS / Total más limpio.
- **Más rápida:** transiciones entre pantallas de 200 ms (antes 320 ms), entradas escalonadas más cortas, el menú y sus fotos se precargan en segundo plano al cargar la configuración y la última configuración se guarda en el equipo para mostrar los banners al instante al reiniciar.

### Corregido
- **Pantalla negra entre banners:** los banners (imágenes y videos) se descargan y guardan en caché al configurar la caja y en cada carga de configuración; el siguiente banner aparece con un fundido solo cuando ya está listo, y el anterior se queda visible mientras tanto. Un video único ahora se repite.

### Pendiente
- Yappy sigue fallando: hace falta el registro `[YAPPY]` del backend de un intento fallido. Sospecha principal: Yappy devuelve su error como `{status:{code:"YP-…"}}` (a veces con HTTP 200) y el backend lo reporta como «no devolvió transactionId/token», ocultando el código real.

---

## [0.0.7] — 2026-10-07

| Dato | Valor |
|---|---|
| `versionCode` | 7 |
| Variante | `flowerpProdRelease` |
| APK | `app/build/outputs/apk/flowerpProd/release/app-flow-kiosko-v0.0.7.apk` |
| SHA-256 | `0DA03959275B3F8D3A8D9D9F7F9AE1049E317F4217AF91948A3C242E839AB099` |

### Requisitos
- Backend con el cambio de `expiresAt` (se envía como instante UTC, p. ej. `2026-10-07T15:04:05Z`). La app también entiende el formato anterior sin zona, así que funciona con un backend sin desplegar, pero conviene desplegarlo.
- Sin migraciones nuevas.

### Corregido
- **Yappy: «Tu pedido expiró».** La app no entendía la hora de vencimiento de la cotización (el servidor la mandaba sin zona horaria) y la daba siempre por vigente, así que intentaba cobrar cotizaciones de más de 10 minutos que el servidor rechazaba. Ahora la lee en cualquier formato, vuelve a cotizar 60 s antes de que venza y, si no la puede leer, cotiza de nuevo en vez de reutilizarla.

### Quitado
- Botón «Contraste» del pie de pantalla (queda «Pantalla baja» e idioma).

### Pendiente
- Cierre inesperado («Flow ERP Kiosko keeps stopping»): falta el registro del error del equipo para saber la causa.
- Yappy que se queda en «Generando tu código…»: falta revisar los registros `[YAPPY]` del backend del momento de la prueba.

---

## [0.0.6] — 2026-10-06

| Dato | Valor |
|---|---|
| `versionCode` | 6 |
| Variante | `flowerpProdRelease` |
| APK | `app/build/outputs/apk/flowerpProd/release/app-flow-kiosko-v0.0.6.apk` |
| SHA-256 | `0D892E9F4F2BEC174B782D76BA0B0D825143E4182D005A1F33EE4ACC8D426FF7` |

### Requisitos
- Los mismos de 0.0.5 (mismo backend; sin migraciones nuevas).
- Los banners del ERP (`banner_1`..`banner_3`) ahora se muestran como póster vertical **2:3** (p. ej. 1024 × 1536 o 1080 × 1620). Imágenes de otra proporción se recortan por los lados.

### Cambiado
- Experiencia de pedido rediseñada siguiendo el flujo de los kioscos de autoservicio de comida rápida (colores Flow ERP):
  - **Inicio (Attract):** el banner ocupa la parte superior como póster y debajo hay un panel blanco con el logo, el botón grande «Iniciar una nueva orden» y los idiomas Español / English.
  - **«¿Dónde vas a comer hoy?»:** pantalla blanca con dos tarjetas cuadradas (Comer aquí / Para llevar) y «Cancelar orden».
  - **Menú:** barra lateral con logo, modalidad, «Inicio» y las categorías con su imagen; página de inicio «Descubre nuestro menú» (mosaicos por categoría y «Productos recomendados»); cuadrícula de productos de 3 columnas (5 en horizontal); barra inferior con la bolsa, el total, «Ver mi orden» y «Cancelar orden».
  - **«También te sugerimos»:** al tocar «Ver mi orden» se ofrecen acompañantes, bebidas o postres antes de revisar el pedido («No, gracias» para seguir).
  - **Personalizador por pasos:** un paso por grupo de opciones («Selecciona tamaño del combo», «Elige tu bebida»…) con la lista de pasos a la izquierda, avance automático en las opciones de una sola elección y un paso final «Revisar orden» con nota para cocina, cantidad y «Agregar a mi orden». Botones «Atrás», «Cancelar» y «Siguiente».
  - **Revisa tu orden:** botones «Pedir más» y «Orden completa».
  - **Factura:** primero se pregunta «¿Necesitas personalizar tu factura?» con «No, gracias» (Consumidor Final) o «Sí, con mis datos».
  - Pantalla baja, contraste e idioma pasan a un pie de pantalla discreto en lugar de la barra superior.
  - Estilo visual plano: fondo blanco, tarjetas con borde gris fino, botones rectangulares en índigo Flow, sin degradados ni sombras en las superficies de pedido. Se quitó el indicador de pasos Pedido → Datos → Pago.

---

## [0.0.5] — 2026-10-06

| Dato | Valor |
|---|---|
| `versionCode` | 5 |
| Variante | `flowerpProdRelease` |
| APK | `app/build/outputs/apk/flowerpProd/release/app-flow-kiosko-v0.0.5.apk` |
| SHA-256 | `1A42937D204F48052CABE5DFC6CA3B174CE8C6A40B4AAE7DD0DA088C1EAA215D` |

### Requisitos
- Los mismos de 0.0.4 (mismo backend; sin migraciones nuevas).

### Corregido
- En el equipo real todo se veía demasiado grande: la interfaz estaba diseñada para 1080 × 1920 a densidad mdpi y el kiosco reporta una densidad mayor (y a veces un tamaño de fuente del sistema mayor), así que botones, títulos y logos salían 1,5–2 veces más grandes. Los diálogos (inactividad, panel de administración, confirmaciones, desbloqueo) también se veían ampliados.

### Cambiado
- La interfaz se adapta a cualquier tamaño y densidad de pantalla de kiosco, vertical u horizontal, e ignora el tamaño de fuente del sistema: el diseño de 1080 × 1920 (o 1920 × 1080 en horizontal) se escala a la pantalla real y ocupa siempre la misma proporción.
- La app sigue la rotación configurada en el equipo, igual que su launcher (ya no fuerza vertical). En pantallas horizontales: Attract con el plato y el saludo lado a lado, inicio de sesión y caja con panel lateral, menú con 4–5 columnas, personalizador con el producto a la izquierda, pedido con resumen lateral, portamesa / datos de factura / pago con tarjeta / Yappy / número de pedido en columnas, y métodos de pago lado a lado.
- Formularios, barras de acción y diálogos tienen un ancho máximo y se centran en pantallas anchas; el modo «pantalla baja» baja menos el contenido en horizontal (200 dp en vez de 380 dp).

---

## [0.0.4] — 2026-10-06

| Dato | Valor |
|---|---|
| `versionCode` | 4 |
| Variante | `flowerpProdRelease` |
| APK | `app/build/outputs/apk/flowerpProd/release/app-flow-kiosko-v0.0.4.apk` |
| SHA-256 | `FF3E8FB0547D7397BFC4CBF3249523956D2F0013D413BD220DD9CE8B0F4314DC` |

### Requisitos
- Los mismos de 0.0.3 (backend desplegado y migración `2026-10-06-KIOSCO-AUTOSERVICIO.sql`, ya aplicada en las 90 bases PA).

### Cambiado
- Nuevo logotipo **Flow ERP Kiosko**: ícono de la app (adaptativo, todas las densidades) y logo interno en Attract, inicio de sesión, selección de caja, menú y modalidad.
- El APK ahora se llama `app-flow-kiosko-v<versión>.apk` (las variantes de desarrollo agregan su nombre, p. ej. `-flowerpDevDebug`).

---

## [0.0.3] — 2026-10-06

| Dato | Valor |
|---|---|
| `versionCode` | 3 |
| Variante | `flowerpProdRelease` |
| APK | `app/build/outputs/apk/flowerpProd/release/app-flowerp-prod-release.apk` |
| SHA-256 | `915B9F13D20F4D6218E230F6417A21ADCE7ADB5143BB8BFD8E60AD1E94986CDD` |

### Requisitos
- Backend desplegado con el kiosco por usuario del sistema: endpoints `/api/v1/kiosk/*` con token de empresa y headers `X-Kiosk-Caja` / `X-Kiosk-Prefix` (ya no existe `POST /api/v1/kiosk/pairing`).
- Base de la empresa migrada con `db/2026/2026-10-06-KIOSCO-AUTOSERVICIO.sql` (admin PHP, `execute_queries_pa.py`). Sin esta migración el kiosco muestra «El kiosco no está habilitado en esta empresa (falta migración)».
- Al menos una caja activa en la empresa y un usuario del sistema con acceso a ella (el mismo con el que se entra al POS).

### Cambiado
- Inicio de sesión con usuario y contraseña del sistema (igual que el POS: `auth/login` + `auth/company`) y selección de caja en el equipo; se elimina el código de emparejamiento.
- Pantalla de inicio de sesión con país (Panamá por defecto / Venezuela) y la URL del servidor oculta en «Opciones avanzadas». Si el usuario tiene varias empresas se elige una.
- Pantalla de caja: lista de cajas activas y «Prefijo de pedidos» (por defecto `K1`, de 1 a 5 letras o números). Usa un prefijo distinto en cada kiosco (K1, K2…).
- Menú de administración: «Re-emparejar» se reemplaza por «Cambiar caja» y «Cerrar sesión».
- Si el servidor revoca la sesión el kiosco vuelve al inicio de sesión; si la caja deja de ser válida vuelve a la selección de caja. Los kioscos con la versión 0.0.2 (emparejados) piden iniciar sesión al actualizar.

### Agregado
- Combos y extras desde el módulo de Combos existente: las opciones marcadas por defecto vienen preseleccionadas en el personalizador (sin pasar el máximo del grupo).
- Banners del Attract desde Parámetros Generales → Configurar Kiosko (imágenes 8 s, videos hasta el final).
- El aviso «Fuera de servicio» muestra el motivo que envía el servidor (por ejemplo, la migración pendiente).

### Corregido
- Una contraseña de administrador incorrecta ya no borra la configuración del kiosco.

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
- Tipo de QR Yappy (`parametros_generales.yappy_tipo_qr`, Dinámico/Híbrido): la columna la crea la migración `db/2026/2026-10-06-KIOSCO-AUTOSERVICIO.sql` y se edita en Parámetros Generales → Configurar Kiosko; sin valor se usa Dinámico.

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
