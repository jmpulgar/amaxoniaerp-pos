# Cross-system wire contracts (Android POS ↔ Ktor backend)

Estos fixtures documentan el contrato HTTP/JSON **EXISTENTE**. No son una
especificación aspiracional: si Android y backend discrepan en un payload,
eso es una decisión de contrato que debe escalarse, no un test a romper.

## Configuración canónica de serialización (idéntica en ambos lados)

```kotlin
Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
```

Consecuencias para los fixtures:

- Los campos con valor default se **omiten** del JSON (`encodeDefaults=false`).
  Un campo ausente equivale al default declarado en el DTO.
- `null` explícito no aparece en el cable (`explicitNulls=false`).
- Claves desconocidas se toleran al decodificar (`ignoreUnknownKeys=true`),
  lo que permite campos solo-backend en respuestas.

## Productor/consumidor por familia

| Fixture | Produce | Consume | Test Android | Test backend |
|---|---|---|---|---|
| auth/login-request | Android | Backend | ContractFixtureTest | BackendContractFixtureTest |
| auth/login-response | Backend | Android (ignora `countryCode`/`schemaType`) | ídem | ídem |
| auth/company-select-* | ídem | ídem | ídem | ídem |
| sale/process-sale-request | Android | Backend | + golden original | ídem |
| sale/process-sale-response | Backend | Android | ídem | ídem |
| sale/credit-sale-request | Android (`formaPago=credito`, CXC, saldo>0) | Backend | ídem | ídem |
| sale/partial-credit-collection-request | Android (`esCobroCreditoPrevio=true`, `idFactura`=factura original) | Backend | ídem | ídem |
| caja/caja-apertura-request | Android | Backend | ídem | ídem |
| caja/caja-cierre-request | Android (claves snake literales) | Backend (`@SerialName`) | ídem | ídem |
| mesas/mesa-abrir-sesion-request | Android | Backend | ídem | ídem |
| mesas/mesa-crear-pedido-request | Android | Backend | ídem | ídem |
| mesas/mesa-crear-cuenta-request | Android | Backend | ídem | ídem |
| creditnote/create-credit-note-request | Android | Backend (+ golden original) | ídem | ídem |
| creditnote/confirm-credit-note-fiscal-request | Android | Backend | ídem | ídem |
| fiscal/confirm-factura-fiscal-request | Android | Backend | ídem | ídem |

## Asimetrías conocidas (documentadas, NO errores)

1. **Respuestas login/company-select**: el backend añade `countryCode` y
   `schemaType`; Android los ignora vía `ignoreUnknownKeys`.
2. **CreateCreditNoteResponse**: backend añade opcional `fiscalMessage`;
   Android lo ignora.
3. **caja-cierre-request**: Android declara las claves snake_case como nombres
   de propiedad literales; el backend las recibe vía `@SerialName`. Los bytes
   del cable son idénticos.
4. **useHka20**: `Boolean=false` (default) en Android vs `Boolean?=null` en
   backend. Ausente en ambos cables cuando no aplica.
5. Varios wrappers de respuesta Android llevan campo `error` extra que el
   backend no envía; es tolerado por omisión.
6. **SaleInvoiceInput.codEstatus**: requerido en Android (siempre viaja en el
   request, valor 2) pero default `=2` en backend, cuyo re-encode lo omite.
   Los tests backend de la familia sale hacen decode-assert (no round-trip
   estricto); el cable Android→backend es correcto tal cual.
