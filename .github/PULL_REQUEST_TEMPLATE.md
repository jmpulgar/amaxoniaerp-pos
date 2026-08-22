# PR template

Al abrir un Pull Request copia/completa esta plantilla. CI requiere los gates
listados en `doc/ARCHITECTURE.md`.

```markdown
## Qué cambia
(una línea funcional; referencia a TASK del PLAN)

## ¿Cambia una decisión de negocio?
[Sí/No]. Si Sí: cuál y quién la aprobó.

## País / modalidad afectada
[PA | VE digital | VE HKA-20 | ninguno/transversal]

## ¿Toca contrato HTTP/JSON?
[Sí/No]. Si Sí: fixture de contracts/ actualizado + ambos ContractFixtureTest.

## ¿Toca schema/migración?
[Sí/No]. Si Sí: STOP — requiere decisión explícita (ver RUNBOOK_DATABASE_MIGRATION).

## ¿Toca PAC/HKA/fiscal?
[Sí/No]. Si Sí: matriz success/rejection/uncertain/failure cubierta por tests.

## UI
[Sí/No]. Si Sí: capturas o previews; sin cambios de comportamiento salvo los declarados.

## Evidencia
- Comandos ejecutados y resultado (gates por repo)
- Tests nuevos/modificados

## Rollback
(cómo revertir; riesgos de datos si los hay)
```
