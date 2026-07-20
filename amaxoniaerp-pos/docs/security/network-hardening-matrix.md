# Network Security Hardening — Matrix

Applies to `amaxoniaerp-pos`. Effective policy is controlled exclusively by
`network_security_config.xml` (referenced via `android:networkSecurityConfig`
in the merged manifest) because `targetSdk = 36 >= 28`. The legacy
`android:usesCleartextTraffic` attribute on `<application>` is ignored by the
OS when `networkSecurityConfig` is present.

## Effective policy by build

| Build variant | Source set that wins | `base-config cleartextTrafficPermitted` | Domain exceptions |
|---|---|---|---|
| `amaxoniaDebug` | `src/debug/res/xml/network_security_config.xml` | `false` | `192.168.2.10`, `192.168.2.16`, `10.0.2.2`, `localhost`, `127.0.0.1` |
| `amaxoniaRelease` | `src/main/res/xml/network_security_config.xml` | `false` | _(none)_ |
| `banescoVenezuelaDebug` | `src/debug/res/xml/network_security_config.xml` | `false` | `192.168.2.10`, `192.168.2.16`, `10.0.2.2`, `localhost`, `127.0.0.1` |
| `banescoVenezuelaRelease` | `src/main/res/xml/network_security_config.xml` | `false` | _(none)_ |

## Why `main` blocks and `debug` opens

- **Release**: backend is HTTPS-only (`https://api.listoerp.app/`). Hard
  blocking prevents accidental cleartext fallback on redirects, misconfig,
  or any future HTTP host. No exceptions.
- **Debug**: developer backend at `http://192.168.2.10:8080/` (per
  `build.gradle.kts` release-base-url comment). Quirurgical exception per
  host; `includeSubdomains="false"` so each IP must be explicit.

## How to add a development host

1. Edit **only** `app/src/debug/res/xml/network_security_config.xml`.
2. Add `<domain includeSubdomains="false">{IP-or-host}</domain>` inside the
   existing `<domain-config cleartextTrafficPermitted="true">`.
3. **Never** edit `app/src/main/res/xml/network_security_config.xml` to add
   hosts — that would weaken release.

## Non-HTTP traffic (unaffected by this policy)

- TCP socket to HKA fiscal printer (`TheFactoryPrinterImpl`,
  `HkaConnectionHelper`, `TheFactoryRapidPayClient.listGateways`) at LAN
  `192.168.x.x:port`. Cleartext flag controls HTTP/HTTPS only; raw TCP
  is not gated by `networkSecurityConfig`.
- AIDL bind to Sunmi printer service: internal IPC, not network traffic.
- Intents to HKA POS app: cross-app IPC, not network traffic.

## Verification commands

```sh
./gradlew :app:processAmaxoniaDebugManifest
./gradlew :app:processAmaxoniaReleaseManifest

# After each, inspect:
#   app/build/intermediates/merged_manifests/{flavor}{buildType}/AndroidManifest.xml
# Expect: android:networkSecurityConfig="@xml/network_security_config"
# Expect: NO android:usesCleartextTraffic="true" attribute

# Confirm effective XML:
#   amaxoniaDebug    uses src/debug/res/xml/network_security_config.xml  (with exceptions)
#   amaxoniaRelease  uses src/main/res/xml/network_security_config.xml   (no exceptions)
```
