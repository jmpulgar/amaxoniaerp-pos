# HKA Payment Gateway Integration — Investigation Report

**Date:** 2026-07-20
**Scope:** Auditoría de producción POS, ítem 6 (INT-CB-001 / INT-CB-002).
**Read-only audit. No changes were made to application code.**
**Audited artifact:** `app/libs/HKACryptoLib03022026.aar` (vendored AAR, SHA-256
not pinned by gradle; resolved at build time from the jar file shipped).

---

## 0. High-level finding

> The current HKA callback integration places **100% of trust at the device**.
> The result Intent is unauthenticated, the launching `MainActivity` is
> `exported="true"` with zero signature/nonce/pending-intent protection, and
> the gateway approval is forwarded straight to `processSale` without any
> backend reconciliation. Any third-party app installed on the same device can
> approve a sale during the 120-second cashier-facing wait window using four
> lines of Java.

The plan-§11.6 criteria — *can the callback be authenticated? is the result
reconcilable?* — are answered **NO** with the current code.

---

## 1. AAR file contents

### 1.1 Top-level AAR layout

`HKACryptoLib03022026.aar` (337 207 bytes) ships only:

```
$ Add-Type -AssemblyName System.IO.Compression.FileSystem
$ [IO.Compression.ZipFile]::OpenRead("HKACryptoLib03022026.aar").Entries

R.txt                                                          # empty
AndroidManifest.xml                          (212 bytes)        # see §1.3
classes.jar                                  (5 442 bytes)      # see §1.2
proguard.txt                                                   # empty
META-INF/com/android/build/gradle/aar-metadata.properties      # standard
jni/{arm64-v8a,armeabi-v7a,x86,x86_64}/libHKACryptoLib.so     # 4 ABIs
```

Of these, **only `classes.jar` and the 4 `libHKACryptoLib.so` files matter** for
runtime. `R.txt`, `proguard.txt`, and `aar-metadata.properties` are empty /
standard scaffolding.

### 1.2 `classes.jar` — exposed public surface

```
com/thefactoryhka/hkacryptolib/MainFactory.class
com/thefactoryhka/hkacryptolib/crypto/ICryptography.class
com/thefactoryhka/hkacryptolib/crypto/AlgorithmProvider.class
com/thefactoryhka/hkacryptolib/crypto/PackagesNameProvider.class
com/thefactoryhka/hkacryptolib/crypto/TokenProvider.class
com/thefactoryhka/hkacryptolib/model/ResponseCryptography.class
a/a.class                          (obfuscated impl of ICryptography)
```

Public `javap` signatures:

```java
public class com.thefactoryhka.hkacryptolib.MainFactory {
    public MainFactory();
    public ICryptography createInstance(android.content.Context);
}

public interface com.thefactoryhka.hkacryptolib.crypto.ICryptography {
    public abstract ResponseCryptography encryptString(String);
    public abstract ResponseCryptography encryptChar(char);
}

public class com.thefactoryhka.hkacryptolib.model.ResponseCryptography {
    byte[] bytes;            // ciphertext payload
    String message;          // error description (null on success)
    boolean isError;         // true if encryption failed
    public byte[] getBytes();
    public void setBytes(byte[]);
    public String getMessage();
    public void setMessage(String);
    public boolean isError();
    public void setError(boolean);
}

public abstract class com.thefactoryhka.hkacryptolib.crypto.AlgorithmProvider {
    private static native String nativeAlgorithm();      // JNI → libHKACryptoLib.so
    private static native String nativeAlgorithmSha();
    private static native String nativeAlgorithmAES();
    public static String a();                            // obfuscated accessor
    public static String b();
    public static String c();
}

public abstract class com.thefactoryhka.hkacryptolib.crypto.TokenProvider {
    public static String a();
    private static native String nativeToken();          // JNI → libHKACryptoLib.so
}

public abstract class com.thefactoryhka.hkacryptolib.crypto.PackagesNameProvider {
    private static native String[] nativeList();         // JNI → libHKACryptoLib.so
    public static ArrayList a();
}

public final class a.a implements ICryptography {        // obfuscated concrete impl
    public final android.content.Context a;
    public a.a(android.content.Context);
    public static ResponseCryptography a(String);
    public static javax.crypto.spec.SecretKeySpec a();   // builds AES SecretKeySpec via JNI
    public final ResponseCryptography encryptString(String);
    public final ResponseCryptography encryptChar(char);
}
```

### 1.3 `.aar/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.thefactoryhka.hkacryptolib">
    <uses-sdk android:minSdkVersion="24" />
</manifest>
```

The AAR contributes **no permissions, no components, no intent-filters** to the
host app. There are no HKA `<activity>`, `<receiver>`, or `<service>` entries
fused in by the manifest merger — confirmed against
`app/build/intermediates/merged_manifests/amaxoniaDebug/processAmaxoniaDebugManifest/AndroidManifest.xml`.

### 1.4 Conclusion about the AAR surface

The SDK is **encryption-only**:

- It exposes `encryptString` and `encryptChar` taking a `String` and returning
  a `ResponseCryptography` bean (bytes + message + isError).
- It exposes NO `decryptString`, NO `verifySignature`, NO nonce helpers, NO
  payment-command builder, NO `callback`/`result` parser.
- All cryptographic state is hidden behind JNI calls into `libHKACryptoLib.so`
  (`nativeAlgorithm`, `nativeAlgorithmSha`, `nativeAlgorithmAES`,
  `nativeToken`, `PackagesNameProvider.nativeList`). The keys live in native
  heap and are never exposed to callers as Java objects.

**Practical takeaway:** the AAR cannot be used on its own to authenticate the
return Intent. Any authentication of the callback must be implemented in our
application code, not come from the library.

---

## 2. AndroidManifest integration points in the host app

### 2.1 `MainActivity` — the implicit callback sink (HIGH)

```xml
<!-- app/src/main/AndroidManifest.xml:14-25 -->
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:theme="@style/Theme.Pos">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

- `android:exported="true"` → any third-party app on the device can target it.
- `android:launchMode="singleTask"` → `onNewIntent` is invoked when HKA returns
  while MainActivity is on the back stack. This is the actual delivery path.
- **No custom action** like `com.thefactory RESULT` is declared. The HKA APK
  comes back via a generic `android.intent.action.MAIN` Intent whose only
  "marker" is that it carries the extra key `codeRapidPay`.
- **No `android:permission`** attribute guards MainActivity, so a `signature`
  protection level for the callback is not in place.

### 2.2 `<queries>` block (Android 11+ visibility)

```xml
<!-- app/src/main/AndroidManifest.xml:6-13 -->
<queries>
    <package android:name="com.thefactory.hkapos.fiscal" />
    <package android:name="com.thefactory.hkapos.fiscal.demo" />
    <package android:name="com.thefactory.hkapos.fiscal.release" />
    <package android:name="com.thefactory.hkapos.fiscal.demo.demo" />
    <package android:name="woyou.aidlservice.jiuiv5" />
    <package android:name="com.sunmi.peripheral.printer" />
</queries>
```

This grants our app visibility into the candidate HKA POS APKs and printer
helpers (`woyou...jiuiv5`, `sunmi...printer`). The four HKA package names are
duplicated as a constant list at
`TheFactoryRapidPayClient.kt:284-291` and looked up via
`packageManager.getPackageInfo(pkg, 0)` at runtime.

### 2.3 Justifications

- No `<receiver>` is defined for HKA broadcasts.
- No `<service>` is defined for HKA IPC.
- No custom `<action>` or `<data>` scheme is declared.

**Net effect:** the entire HKA callback path rides on the implicit `MAIN`
launch of MainActivity plus the presence of the `codeRapidPay` extra.

---

## 3. `RapidPayBridge` implementation, launch & result delivery

### 3.1 In-memory bridge — `RapidPayBridge.kt`

`RapidPayBridge` is an `object` (process singleton) that bridges the suspended
payment coroutine (`HkaPaymentGateway.awaitApproval`) to the Android
`MainActivity.onNewIntent` callback.

```kotlin
// app/src/main/java/com/amaxonia/pos/data/printer/RapidPayBridge.kt
object RapidPayBridge {
    private const val RESULT_TIMEOUT_MS = 120_000L          // 2 min

    @Volatile private var pendingResult: CompletableDeferred<RapidPayResult>? = null
    @Volatile private var pendingCorrelationId: String? = null

    fun setPendingCorrelationId(correlationId: String?)   // pinned before launch
    suspend fun awaitResult(): RapidPayResult             // suspends ≤ 2 min
    fun deliverResult(result: RapidPayResult)             // invoked by MainActivity
    fun pendingCorrelationId(): String?
    fun hasPendingRequest(): Boolean
}
```

`RapidPayResult` is a 3-field struct
(`data class RapidPayResult(approved, message, rawResponse)` at
`TheFactoryRapidPayClient.kt:307`).

### 3.2 Building the request

`TheFactoryRapidPayClient.buildGatewayLaunchPayload` constructs a plaintext
command and encrypts it with the AAR:

```kotlin
// TheFactoryRapidPayClient.kt:55-117
val command = "KRV<amountCents16>|<cedula>|<rifComercio>|"   // buildSaleCommand
val encrypted: ByteArray = encryptCommand(command)            // cryptography.encryptString(...)
                                                              // → ResponseCryptography.bytes
val targetPackage = resolveHkaPackage()                       // first installed of HKA_PACKAGES
GatewayLaunchPayload(
    packageName       = targetPackage,
    activityClassName = "com.thefactory.hkapos.ui.main.HomeActivity",  // TARGET_ACTIVITY
    encryptedCommand  = encrypted,
    backgroundColor   = ...,
    textColor         = ...,
    message           = "Acercá la tarjeta al POS...",
)
```

The command body carries **only the amount, the customer cédula, and the
merchant RIF**. There is no nonce, no idempotency id, no merchant-side
one-time token the device is supposed to echo back.

### 3.3 Launch site

```kotlin
// app/src/main/java/com/amaxonia/pos/ui/payment/PaymentScreen.kt:143-162
is PaymentUiEffect.LaunchGateway -> {
    val payload = effect.payload
    val intent = Intent().apply {
        component = ComponentName(payload.packageName, payload.activityClassName)
        putExtra("commandRapidPay",        payload.encryptedCommand)   // ciphertext bytes
        putExtra("colorBackgroundLoading", payload.backgroundColor)
        putExtra("colorText",              payload.textColor)
        putExtra("messageRapidPay",        payload.message)
    }
    try { context.startActivity(intent) }
    catch (e: ActivityNotFoundException) { SafeLog.e(...) }
}
```

It is a **bare `startActivity`** (no `ActivityResultLauncher`, no
`PendingIntent`, no `IntentSender`). The reply is therefore not bound to the
requester; any future launch of MainActivity is treated as the reply.

### 3.4 Result Intent parsing

Two `MainActivity` paths deliver to the bridge:

```kotlin
// app/src/main/java/com/amaxonia/pos/MainActivity.kt:23-26
private const val EXTRA_RESULT_CODE = "codeRapidPay"
private const val EXTRA_RESULT_DATA = "resultRapidPay"
private const val EXTRA_MESSAGE     = "messageRapidPay"

// MainActivity.kt:67-110
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleRapidPayResult(intent, source = "onNewIntent")
}

private fun handleRapidPayResult(intent: Intent?, source: String) {
    if (intent == null) return
    if (intent.getStringExtra(EXTRA_RESULT_CODE) == null) return   // ← marker
    val correlationId = RapidPayBridge.pendingCorrelationId()
    val responseCode  = intent.getStringExtra(EXTRA_RESULT_CODE).orEmpty()
    if (!RapidPayBridge.hasPendingRequest()) {
        if (correlationId != null) markGatewayResolved(correlationId, responseCode)
        return
    }
    val result = DependencyContainer.theFactoryRapidPayClient.parseResultIntent(intent)
    RapidPayBridge.deliverResult(result)
    if (correlationId != null) markGatewayResolved(correlationId, responseCode)
    // extras removed
}
```

```kotlin
// TheFactoryRapidPayClient.kt:138-180
fun parseResultIntent(intent: Intent): RapidPayResult {
    val code       = intent.getStringExtra(EXTRA_RESULT_CODE)
    val resultJson = intent.getStringExtra(EXTRA_RESULT_DATA)
    val message    = intent.getStringExtra(EXTRA_MESSAGE)
    val approved = code == APPROVED_CODE                      // APPROVED_CODE = "200"
    val displayMessage = when { ... }
    return RapidPayResult(approved = approved, message = displayMessage, rawResponse = resultJson)
}
```

Allowed codes (`TheFactoryRapidPayClient.kt:291-298`): `"200"` = approved, any
other string = rejected (`"400"` reported as the typical reject).

### 3.5 Wire-up on the consumer side

```kotlin
// HkaPaymentGateway.kt:46-50
override suspend fun awaitApproval(): GatewayApproval {
    val result = RapidPayBridge.awaitResult()
    return GatewayApproval(approved = result.approved, message = result.message)
}
```

Back in `ExecuteGatewayPaymentUseCase.executeMethod`
(`ExecuteGatewayPaymentUseCase.kt:38-56`), `approval.approved == true`
concludes the gateway; `false` produces
`Result.failure(IllegalStateException(approval.message))` and the sale flow
aborts.

### 3.6 Persistence path

`ExecutePaymentFlowUseCase.executeGatewayIfRequired`
(`:190-212`) writes `gatewayCallbackLedger.markAwaiting(correlationId, ...)` and
pins the id on `RapidPayBridge.setPendingCorrelationId(correlationId)` **before**
launching HKA, so a process death mid-callback is recoverable. If the in-memory
Deferred is gone when the Intent arrives (post-mortem),
`MainActivity.markGatewayResolved(correlationId, responseCode)` flips the Room
row to `RESOLVED` via `runBlocking` on the main thread (`MainActivity.kt:112-122`).
The watchdog `GatewayCallbackWorker` escalates unresolved rows to
`TERMINAL_AWAITING` after 4 retries (`QueueGatewayCallbackUseCase.kt:60-66`).

---

## 4. Authentication of the callback

**Verdict: NO authentication of any kind.**

| Defense | Where it would live | Present? |
|---|---|---|
| Nonce generated on request and verified on response | `TheFactoryRapidPayClient.buildGatewayCommand` | ❌ None. Command is `KRV<amount>\|<cedula>\|<rif>\|`. |
| HMAC / signature over the result extras | `MainActivity.handleRapidPayResult`, `parseResultIntent` | ❌ None. `approved` is a plain string comparison against `"200"`. |
| `intent.getCallingPackage()` / `getCallingActivity()` | `MainActivity.onNewIntent` | ❌ Not invoked anywhere. |
| `PackageManager.getPackageInfo(...).signatures[0]` checked against HKA signing cert | anywhere reachable | ❌ Not present. |
| Listener-bound `ActivityResultLauncher` / `PendingIntent` | `PaymentScreen.kt:143-162` | ❌ Uses bare `startActivity(intent)`. |
| `android:permission="signature"` on MainActivity | `AndroidManifest.xml:14-25` | ❌ No permission attribute. |
| Custom `<action>` (`com.thefactory RESULT`) | any | ❌ Not declared. |

The only "marker" that flags an Intent as a Rapid Pay result is the presence
of the extra key `codeRapidPay`:

```kotlin
// MainActivity.kt:80
if (intent.getStringExtra(EXTRA_RESULT_CODE) == null) return
```

### Forgeability test (theoretical)

A third-party app on the same device could complete a sale as approved via:

```kotlin
val intent = Intent().apply {
    component = ComponentName("com.amaxonia.pos", "com.amaxonia.pos.MainActivity")
    // OR implicitly:
    // action = Intent.ACTION_MAIN
    putExtra("codeRapidPay", "200")
    putExtra("resultRapidPay", "{\"message\":\"OK\"}")
}
startActivity(intent)
```

`MainActivity` is `exported="true"` so any caller is admitted. As long as the
cashier is mid-flight (the 120 s `RapidPayBridge.awaitResult` window), the
in-memory `pendingResult: CompletableDeferred` is active and
`hasPendingRequest() == true`, so `deliverResult(RapidPayResult(approved=true))`
completes the current sale without anyone touching the card.

---

## 5. Reconcilability

**Verdict: the result is trusted locally, immediately, and is never reconciled.**

### 5.1 What MainActivity reads from the Intent

Only three strings:

- `codeRapidPay` — `"200"` / `"400"` etc. (short status string).
- `resultRapidPay` — a JSON object that the POS only reads for the human
  message field (`message` / `msg` / `responseMessage`, see
  `parseApprovedMessage` at `TheFactoryRapidPayClient.kt:182-191`). The full
  JSON is held in memory as `RapidPayResult.rawResponse` but **never
  persisted**.
- `messageRapidPay` — fallback status string.

### 5.2 What is persisted to `transaction_log`

```kotlin
// TransactionLogEntity.kt:229-243
@Query(
    "UPDATE transaction_log SET gatewayCallbackStatus = :status, " +
        "gatewayCallbackNextAttemptAt = 0, gatewayCallbackLeasedUntil = 0, " +
        "gatewayRawResponse = :rawResponse, updatedAt = :updatedAt " +
        "WHERE clientCorrelationId = :id",
)
suspend fun markGatewayResolved(id: String, status: String, rawResponse: String?, ...)
```

`gatewayRawResponse` is populated with the **short `codeRapidPay` string only**
(see `MainActivity.kt:90` `val responseCode = intent.getStringExtra(EXTRA_RESULT_CODE).orEmpty()`,
then `markGatewayResolved(correlationId, responseCode)` at `:113`). The full
`resultRapidPay` JSON — which would carry the authorization code, RRN, last4,
host reference — is dropped on the floor.

### 5.3 What the backend receives

The `SalesApiImpl.processSale` POST body carries a `ProcessSaleRequestDto`
whose payment block is built without any gateway reference
(`GatewayPaymentRequest` in `GatewayPayment.kt:17-23` carries only `methods`,
`customerIdentifier`, `exchangeRate`, `isMultiCurrency`). The backend therefore
has no way to know:

- whether the gateway was even invoked,
- what authorization code / RRN the bank returned,
- whether the local "200" matches the issuer state.

### 5.4 Verification endpoints

Workspace grep against both `amaxoniaerp-pos` and `amaxoniaerp-backend`
returned **zero** matches for any client↔server payment verification call.
(The `thefactory` matches in the backend are the PANAMA e-invoicing PAC
client — `features/electronicinvoice/pac/thefactory/*` — and are unrelated to
the Rapid Pay gateway.)

### 5.5 Reconciler behaviour on no-show

When HKA never returns (process death before `onNewIntent`),
`GatewayCallbackWorker` (`GatewayCallbackWorker.kt` in full) escalates the row
to `TERMINAL_AWAITING`. The worker itself does not poll the bank or HKA —
the comment at `GatewayCallbackWorker.kt:13-23` states explicitly "no API to
poll". Beyond escalating the row, recovery requires manual intervention.

---

## 6. Risks and gaps (plan-§11.6 criteria)

Listed by severity for safe callback handling.

### 6.1 Callback is forgeable (plan criterion INT-CB-001) — `CRITICAL`

- `MainActivity.exported=true` + no signature permission + no custom action.
- Any installed app can fire `Intent(...).putExtra("codeRapidPay","200")` at
  MainActivity while a card-payment sale is mid-flight and the sale will close
  as approved.
- Knowledge required to forge: the publicly-listed POS package name (declared
  in `<queries>`) and the well-known SDK extra keys
  (`codeRapidPay`/`resultRapidPay`/`messageRapidPay`).

### 6.2 No nonce / challenge on the request (plan criterion INT-CB-001) — `HIGH`

- The sale command `KRV<amount16>|<cedula>|<rif>|` is fully derivable from the
  sale total. Nothing client-side is HMAC'd into the command for echoing on
  the response, so 6.1 cannot be patched with a payload comparison alone.
- Fix direction: introduce a random-per-sale nonce, include it in the
  `commandRapidPay` payload envelope, and require it (signed) back on the
  `resultRapidPay` envelope. Both sides must agree on the cryptographic
  scheme — and crucially the HKA SDK must echo it.

### 6.3 No backend reconciliation (plan criterion INT-CB-002) — `CRITICAL`

- The backend commits the invoice without ever learning the gateway outcome.
- A forged or replayed approval has no post-hoc audit trail in the backend.
- Fix direction: persist `resultRapidPay.rawResponse` (auth code, RRN, host
  reference, card last4 if disclosed) in `transaction_log.gatewayRawResponse`
  (already nullable, just unused for the JSON), and POST a server-side
  reconciliation / capture step that the issuer or HKA can answer with truth.

### 6.4 No MITM resistance on the relay path — `MEDIUM`

- The TCP socket used by `HkaConnectionHelper.listGateways` /
  `checkPrinterStatus` (`HkaConnectionHelper.kt:60-93`,
  `TheFactoryRapidPayClient.kt:170-198`) and the Intent reply path are both
  cleartext. `hkacryptolib` only encrypts the outgoing command string
  (`encryptString`). There is no `decrypt` call site in our code, so the
  response is processed as plaintext after byte-decoding.
- Anyone able to read loopback packets or Activity extras (eg. via an
  accessibility service or a debug-bridge) sees the result before the user.

### 6.5 Audit trail collapses to a 3-letter string — `MEDIUM`

- `gatewayRawResponse` only ever holds `"200"` / `"400"`. The bank JSON with
  the authorization code, RRN, card last4, host reference is dropped.
- There is nothing for fraud / chargeback investigation to follow.

### 6.6 `runBlocking` on the main thread — `MEDIUM`

- `MainActivity.markGatewayResolved` runs `runBlocking { ... }` on the main
  thread (`MainActivity.kt:112-122`). The Room write is small but a slow disk
  can ANR. More importantly: the bridge's pending-result Deferred is re-routed
  through this same path, so a forged Intent arriving during process-death
  recovery still propagates the forged `responseCode` to the row and the
  watchdog then believes the callback landed successfully.

### 6.7 AAR lacks any helper for response verification — `INFORMATIONAL`

- The AAR exposes only `encryptString` / `encryptChar` and the
  `ResponseCryptography` bean. There is no `decrypt`, no `verify`, no nonce
  generator, no MAC helper. So even if the protocol were upgraded, the
  authentication must be added in our code or in a separate library; HKA's
  AAR cannot do it for us.

### 6.8 AAR provenance / pinning — `INFORMATIONAL`

- `app/build.gradle.kts:224` registers `files("libs/HKACryptoLib03022026.aar")`
  as `implementation`. The AAR on disk is not checksum-pinned by the gradle
  script; swapping the file with a malicious one is technically possible for
  anyone with commit access to `app/libs/`. Recommendation: hash-pin and
  sign-verify the AAR at build time.

### 6.9 JNI internals are opaque — `INFORMATIONAL`

- The sensitive routines (`nativeAlgorithm`, `nativeAlgorithmAES`,
  `nativeToken`, `PackagesNameProvider.nativeList`) live inside
  `libHKACryptoLib.so`. They are not auditable from the Kotlin side; they
  must be inspected via disassembly (`objdump -d libHKACryptoLib.so`,
  `strings`, `nm`) for a complete security review. Out of scope for this
  read-only audit.

---

## 7. Recommended follow-up (out of scope — presented for triage)

These fixes are NOT applied by this audit; they are proposed for the next
iteration. Listed in dependency order:

1. **Persist full `resultRapidPay` JSON to `transaction_log`.** Code change
   localized to `MainActivity.handleRapidPayResult` (forward the full
   `rawResponse`, not just `codeRapidPay`). Audit-friendly, no breaking change.
2. **Add a per-sale nonce to `commandRapidPay`.** Requires HKA SDK support;
   verify with the vendor whether the command envelope accepts a
   pass-through field echoed back unchanged.
3. **Restrict MainActivity callback acceptance** to:
   - `intent.getCallingPackage()` ∈ {HKA POS APK package names}, OR
   - a `PendingIntent` round-trip that binds the response to the requester.
4. **POST the gateway reference to the backend in the sale body** so
   `amaxoniaerp-backend` can later call back to the issuer / HKA server for
   settlement confirmation.
5. **Replace `runBlocking` in `MainActivity.markGatewayResolved`** with a
   lifecycle-aware scope, or move the persist path to the
   `GatewayCallbackWorker` (the row is already being flipped by the worker
   anyway).
6. **Pin the AAR by SHA-256** in `build.gradle.kts` and validate on CI.
7. **Reverse-engineer `libHKACryptoLib.so`** to validate the keys / algorithm
   choices are sound before promoting this integration to other tenant
   countries.

---

## 8. Source citations (file:line)

| Topic | Reference |
|---|---|
| AAR inclusion | `app/build.gradle.kts:224` |
| AAR public API (consumers of `MainFactory`) | `app/src/main/java/com/amaxonia/pos/data/printer/HkaConnectionHelper.kt:5,23,79-86`; `TheFactoryRapidPayClient.kt:10,39,259-267`; `TheFactoryPrinterImpl.kt:16,33` |
| MainActivity exported + singleTask | `app/src/main/AndroidManifest.xml:14-25`; merged `app/build/intermediates/merged_manifests/amaxoniaDebug/processAmaxoniaDebugManifest/AndroidManifest.xml:38-50` |
| `<queries>` HKA packages | `app/src/main/AndroidManifest.xml:6-13` |
| Result handling (callback) | `app/src/main/java/com/amaxonia/pos/MainActivity.kt:23-26, 67-110, 112-122` |
| In-memory bridge | `app/src/main/java/com/amaxonia/pos/data/printer/RapidPayBridge.kt` (whole file) |
| Gateway intent builder | `TheFactoryRapidPayClient.kt:55-117, 232-258, 273-291` |
| Launch site (bare startActivity) | `app/src/main/java/com/amaxonia/pos/ui/payment/PaymentScreen.kt:143-162` |
| Use case wiring | `ExecuteGatewayPaymentUseCase.kt:38-56`; `HkaPaymentGateway.kt:46-50`; `ExecutePaymentFlowUseCase.kt:190-212` |
| Ledger / DAO columns | `TransactionLogEntity.kt:33-69, 195-340` |
| Watchdog reconciler | `GatewayCallbackWorker.kt` (whole file); `QueueGatewayCallbackUseCase.kt:60-66` |
| Backend HKA references (e-invoicing only) | `amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/pac/thefactory/*` |

---

**Status:** Investigation of ítem 6 complete. **No code changes were made.**
The findings feed into a follow-up risk ticket for callback authentication +
backend reconciliation; both are outside the scope of the 1-6 binary-gate
audit batch.
