# `MoneyCharacterizationTest` — fallo preexistente

## Test afectado

```
MoneyCharacterizationTest > currentMultiCurrencyDisplayRoundsToTwoDecimals
    org.junit.ComparisonFailure at MoneyCharacterizationTest.kt:61
```

Archivo: `amaxoniaerp-pos/app/src/test/java/com/amaxonia/pos/ui/payment/MoneyCharacterizationTest.kt:51-64`

## Causa raíz

El test espera `assertEquals("365.37", state.totalAmountBsText)` — i.e. punto como
separador decimal. La JVM de Windows bajo locale `es-*` formatea con coma decimal
(`365,37`), produciendo `ComparisonFailure`. NO es un bug de negocio; es una
asunción de locale del test que no fue forzada (e.g. falta
`Locale.setDefault(Locale.US)` en `@Before` o `@FixedLocale`).

## Evidencia de preexistencia (verificada 2026-07-20)

Ejecutado contra HEAD prístine (`git stash` de todos los cambios del roadmap 0-3):

```powershell
PS> git stash push -u -m "verify-money-test-preexisting"
Saved working directory and index state On main: verify-money-test-preexisting

PS> .\gradlew.bat :app:testAmaxoniaDebugUnitTest --tests "*MoneyCharacterizationTest*"
> Task :app:testAmaxoniaDebugUnitTest

MoneyCharacterizationTest > currentMultiCurrencyDisplayRoundsToTwoDecimals FAILED
    org.junit.ComparisonFailure at MoneyCharacterizationTest.kt:61
9 tests completed, 1 failed

PS> git stash pop  # restaurado
Dropped refs/stash@{0}
```

El test falla de forma idéntica **sin ningún cambio** del roadmap aplicado →
totalmente independiente del trabajo.

## Impacto en el roadmap

Ninguno. El roadmap 0-3 NO toca `MoneyCharacterizationTest.kt`, ni `Money.format`,
ni el `PaymentState` field formatting salvo el agregado de campos
`duplicateInvoice` y `monedaSecundariaLabel` (que no son los que el test evalúa).

## Recomendación de fix (para futuro PR separado, no parte del roadmap)

```kotlin
import org.junit.Before
import java.util.Locale

@Before
fun forceUsLocale() {
    Locale.setDefault(Locale.US)
}
```

O usar la regla JUnit `@Rule @JvmField val localeRule = LocaleRule(Locale.US)`
si la dependencia `junit-rules` estuviese disponible.

## Status

**No bloqueante** para production-ready del roadmap. Se documenta aquí como
pendiente separado.
