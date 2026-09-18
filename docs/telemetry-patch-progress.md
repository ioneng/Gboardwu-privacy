# Block Gboard Telemetry - Progress Update

**Branch:** `feature/block-gboard-telemetry`  
**PR:** #1, `Block Gboard telemetry`  
**Target:** Gboard `18.0.3.954559732-release` (`175940518`, arm64-v8a)  
**Updated:** 2026-09-18

## Current status

The **Block Gboard Telemetry** implementation is complete for the defined Gboard 18.0.3 scope.

Current state:

- investigation: **complete for the defined telemetry scope**;
- implementation: **complete**;
- public patch and catalog wiring: **complete**;
- repository build and test validation: **passed**;
- exact target APK static validation: **passed**;
- patch bundle export: **passed**;
- patched-device functional smoke testing: **passed for the primary exercised paths**;
- Tenor `/v2/registershare` runtime network validation: **passed**;
- Cronet manifest handling: **corrected after runtime validation and rebuilt successfully**.

The production feature branch does **not** contain the temporary user-CA trust configuration used for PCAPdroid HTTPS inspection.

## Implemented scope

The public patch suppresses:

1. central Clearcut submission, including Gboard and ML Kit `FIREBASE_ML_SDK` Clearcut;
2. Gboard Clearcut logger creation as defence in depth;
3. Google Play Services ClientTelemetry;
4. ClientThrottlingTelemetry reporting while preserving actual throttling;
5. ClientNotificationTelemetry reporting while preserving GMS error UI;
6. Daily Ping periodic metrics while returning a successful worker result;
7. Primes startup;
8. Primes native-crash sidecar startup;
9. Primes Lifeboat transmission;
10. Tenor `/v2/registershare` tracking only;
11. Cronet Android telemetry via `android.net.http.EnableTelemetry=false`.

The implementation intentionally leaves functional online features intact.

## Explicitly retained

The following remain unchanged by design:

- `UsageReporting.API` and Android Usage and diagnostics consent plumbing;
- `Audit.API` consent and compliance records;
- AppDoctor remote remediation;
- authentication and account APIs;
- OCR execution and module install;
- voice recognition and Agentic Dictation functional requests;
- model and module downloads;
- Phenotype and remote configuration;
- Tenor search, download, and actual share behavior;
- Voice Donation consent infrastructure;
- other feature-required network traffic.

## Build and static validation

The decisive Gradle command remains:

```text
./gradlew :patches:test :patches:buildAndroid generatePatchesList
```

The final production manifest correction was rebuilt on isolated validation branch `validation/telemetry-final-build`.

Final validation run:

```text
GitHub Actions run: 35308028774
Result: success
Artifact: telemetry-final-squashed-mpp
Artifact ID: 10531778763
SHA-256: c1a5d83c194fee759b114b05fe9e1474822abf5957b3793a58b0676e4575a08d
```

The run passed the test/build step and uploaded the MPP artifact successfully.

Earlier exact target validation established:

- 10/10 target methods matched;
- class and signature matches passed;
- access flags matched;
- register counts matched;
- try-block counts matched;
- 56 stock-body, string, method, and field sentinels matched;
- Tenor branch continuation matched stock control flow;
- completed-success Task helper behavior matched the expected task type;
- Daily Ping success object matched the declared worker return interface.

## Runtime validation

Runtime testing was performed on an OPPO CPH2765 running Android 16, using the supported Gboard 18.0.3 target.

The coexistence build used package `dev.jason.com.google.android.inputmethod.latin`. Morphe Manager reported version 1.31.1 with Patcher 1.14.0.

The following exercised paths remained functional:

- cold startup after selecting the existing **Add Gboard Signature Bypass** patch;
- ordinary typing;
- voice typing;
- OCR / Scan Text;
- Tenor category and autocomplete traffic;
- fresh Tenor search;
- Tenor GIF media loading;
- selecting a GIF and sending it to the input box.

Package-scoped logcat inspection did not show the targeted Clearcut or ClientTelemetry terms during the exercised paths. ColorOS log flow control can drop rows, so this is supportive evidence rather than an exhaustive absence proof.

### Tenor direct network check

PCAPdroid was used for app-scoped capture.

For direct HTTPS inspection, a temporary validation-only build trusted user-installed CAs and PCAPdroid QUIC blocking forced Tenor onto decryptable HTTPS. In the decisive fresh-search test, the complete HTTP request list showed:

- `GET /v2/search` -> HTTP 200;
- a subsequent `media.tenor.com` GIF fetch -> HTTP 200;
- the selected GIF was successfully sent to the input box;
- no `/v2/registershare` request appeared.

This is direct runtime evidence for the intended Tenor behavior on the tested build: functional search and media delivery remain available while the dedicated share-registration tracking request is suppressed.

The user-CA trust configuration is confined to `validation/telemetry-mitm` and is intentionally excluded from production.

## Cronet manifest correction

Runtime inspection found that the first production-style patched APK still contained:

```text
android.net.http.EnableTelemetry=true
```

The Morphe Manager 1.31.1 / Patcher 1.14.0 path could also present the decoded manifest without that meta-data entry during patch execution.

The production patch now handles that state explicitly:

- manifest mutation runs during `execute`;
- an existing single `android.net.http.EnableTelemetry` entry is forced to `false`;
- a missing entry is created with value `false`;
- duplicate matching entries are rejected rather than guessed;
- unexpected existing values are rejected.

Regression tests cover existing, missing, duplicate, and unexpected-value cases.

A temporary MITM validation build using the same execute-stage manifest mutation successfully persisted its network-security configuration on-device, which provided device-side confirmation that this lifecycle stage survives the tested patching path.

## Known limitations

The runtime session did not deliberately force every rare Google Play Services availability or error-notification state. Those paths remain covered primarily by exact-target static analysis and repository tests.

Package-scoped logcat cannot prove that no targeted event can ever occur under every possible feature state. The runtime evidence should therefore be read together with the static target analysis rather than as a universal traffic absence claim.

Obfuscated anchors are version-sensitive. A later Gboard version needs a fresh target audit before this patch is treated as compatible.

## Review focus

Before merge, review should focus on:

- central Clearcut `Llvf.l(...)` completed-success behavior;
- the defence-in-depth Gboard logger gate;
- ClientTelemetry completed-success behavior;
- preservation of real ClientThrottling behavior while suppressing only reporting;
- preservation of GMS error UI while suppressing ClientNotificationTelemetry;
- Daily Ping success-result construction;
- the Tenor selective branch target and continuation;
- execute-stage Cronet manifest handling;
- version sensitivity of the obfuscated targets.

## Documentation

The detailed investigation, rationale, payload analysis, exact targets, exclusions, static evidence, and runtime evidence are documented in:

```text
docs/telemetry-investigation-gboard-18.0.3.md
```

This file is the concise status and handoff record. The investigation report remains the authoritative technical reference.
