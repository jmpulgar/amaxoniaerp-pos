# Fase 0 — Identidad de firma compatible

Fecha de captura: 2026-07-12 (America/Bogota)

## Regla de preservación

No generar, reemplazar, mover destructivamente ni rotar el keystore hasta disponer de respaldo
verificado y coordinación explícita. Una actualización compatible debe conservar el certificado
SHA-256 documentado aquí.

## Keystore release actual

- Ubicación: `app/amaxonia-release-key.jks`
- Ubicación absoluta observada:
  `/mnt/d/PROGRAMMING/Kotlin/Amaxonia/amaxoniaerp-pos/app/amaxonia-release-key.jks`
- Alias: `amaxonia-alias`
- Válido hasta: 2053-07-20
- SHA-1 del certificado: `3E:5E:21:4F:98:D4:D8:B3:24:99:BB:DF:79:5A:A7:FD:D9:B3:F3:CF`
- SHA-256 del certificado:
  `92:8F:37:E2:96:14:8F:36:A8:F1:E6:BA:F0:D0:5B:90:E2:EA:7C:3F:5A:4F:A6:69:75:E6:2D:E6:D0:7B:C5:D9`
- SHA-256 del archivo keystore:
  `2e586dc7d74c1aa948ed524eb4182f89e77b90a6062a2db4c53f1fee17ba554b`

No se documentan contraseñas.

## Variantes verificadas

`signingReport` confirmó que `amaxoniaRelease` y `banescoVenezuelaRelease` usan el mismo
keystore, alias y certificado.

`apksigner verify --print-certs` confirmó que los dos APK release generados contienen el mismo
certificado SHA-256:

```text
928f37e296148f36a8f1e6baf0d05b90e2ea7c3f5a4fa66975e62de6d07bc5d9
```

La forma sin separadores anterior es equivalente a la huella SHA-256 documentada.

## Historial Git

El keystore está versionado y aparece en el historial desde el commit `a0f1922` del 2026-03-04.
La configuración con contraseña literal también aparece desde ese commit.

Consecuencia: retirarlo del commit actual evita nuevas exposiciones, pero no lo elimina de clones
ni del historial. Limpiar el historial exigiría reescritura coordinada y potencialmente force-push.
Esa acción queda explícitamente fuera de Fase 0 y debe detenerse para aprobación antes de ejecutarse.

## Próximo paso seguro

Antes de retirar secretos del árbol versionado:

1. Crear y comprobar un respaldo seguro del mismo archivo.
2. Volver a calcular el hash del archivo y las huellas del certificado.
3. Configurar ruta, alias y contraseñas mediante secretos locales/CI.
4. Ensamblar ambos releases y verificar nuevamente con `apksigner`.
5. Confirmar el certificado registrado en Google Play App Signing, si la aplicación está publicada.

Ninguno de esos pasos se ejecutó durante Fase 0.
