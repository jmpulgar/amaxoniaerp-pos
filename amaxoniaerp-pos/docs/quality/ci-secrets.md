# Variables protegidas de CI

Configurar en GitLab como variables **masked** y **protected**. No guardar valores en el repositorio.

- `AMAXONIA_KEYSTORE_BASE64`: keystore original codificado en base64.
- `AMAXONIA_KEYSTORE_PASSWORD`: contraseña original del almacén.
- `AMAXONIA_KEY_ALIAS`: alias original.
- `AMAXONIA_KEY_PASSWORD`: contraseña original de la llave.

El job materializa el keystore con permisos restrictivos en `.ci/release.jks`, lo elimina en `after_script` y nunca imprime las variables. Esta configuración no rota ni sustituye el certificado.

La limpieza del secreto en commits históricos **no se realizó**: requiere reescritura coordinada del historial y rotación de cualquier credencial expuesta, operación expresamente fuera de alcance sin autorización y coordinación de Google Play/GitLab.
