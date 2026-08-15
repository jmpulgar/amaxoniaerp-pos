# ADR-002 — Company tenant context

- Estado: Aceptado
- Fecha: 2026-08-14

## Contexto

La resolución de compañía está repetida en múltiples routes y combina claims JWT, `token_type`, `admin_db`, `Company-DB`, `country_code` y selección de base de datos.

## Decisión

El backend tendrá una única seam tipada `CompanyRequestContext` obtenida desde una extensión/plugin/interceptor Ktor común. La resolución y validación del tenant ocurre una vez; las routes consumen el contexto resuelto.

La migración preservará exactamente la semántica multi-tenant vigente y se hará por TASK con characterization tests.

## Consecuencias

- Se elimina progresivamente la validación duplicada.
- Las routes dejan de conocer detalles de resolución de base de datos.
- Cambiar reglas de selección de empresa queda fuera del refactor arquitectónico.
