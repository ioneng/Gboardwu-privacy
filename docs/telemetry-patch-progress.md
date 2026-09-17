# Block Gboard Telemetry — Progress Update

**Branch:** `feature/block-gboard-telemetry`  
**PR:** #1 — `Block Gboard telemetry`  
**Target:** Gboard `18.0.3.954559732-release` (`175940518`, arm64-v8a)  
**Updated:** 2026-09-17

## Current status

The **Block Gboard Telemetry** implementation is functionally complete at the source/build/static-validation stage.

Current state:

- investigation: **complete for the defined telemetry scope**;
- implementation: **complete**;
- public patch/catalog wiring: **complete**;
- repository build/test validation: **passed**;
- exact target APK static validation: **passed**;
- patch bundle export: **passed**;
- runtime/APKM validation: **pending**;
- device/network verification: **pending**.

The feature branch remains separate from `main` and PR #1 is still draft while runtime validation is outstanding.

## Implemented scope

The public patch currently suppresses:

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

- `UsageReporting.API` and Android Usage & diagnostics consent plumbing;
- `Audit.API` consent/compliance records;
- AppDoctor remote remediation;
- authentication/account APIs;
- OCR execution and module install;
- voice recognition and Agentic Dictation functional requests;
- model/module downloads;
- Phenotype / remote configuration;
- Tenor search, download, and actual share behavior;
- Voice Donation consent infrastructure;
- other feature-required network traffic.

## Validation completed

### Repository build/tests

The passing Gradle invocation was:

```text
./gradlew :patches:test :patches:buildAndroid generatePatchesList
```

Confirmed results:

- `:patches:compileKotlin` — passed;
- `:patches:verifyGboardCapabilityWiring` — passed;
- `:patches:buildAndroid` — passed;
- `:patches:test` — passed;
- `:patches:generatePatchesList` — passed;
- full test suite — **271 tests passed**;
- final Gradle result — **BUILD SUCCESSFUL**.

### Exact APK validation

All ten bytecode targets were checked against the supplied Gboard 18.0.3 APKM.

Results:

- 10/10 target methods matched;
- class/signature matches passed;
- access flags matched;
- register counts matched;
- try-block counts matched;
- 56 stock-body/string/method/field sentinels matched;
- Tenor branch continuation was verified against stock control flow;
- completed-success Task helper behavior was checked;
- Daily Ping return-object type relationship was checked.

### Bundle export

A successful Android Morphe patch bundle was produced:

```text
patches-3.10.0.mpp
```

The bundle was exported through an isolated validation workflow so the clean feature branch did not need temporary CI/export files.

### Runtime tooling preparation

Morphe Desktop v1.16.0 was also fetched through the isolated validation branch.

That version supports `.apkm` input directly, so the exact source Gboard APKM can be patched without manually merging its splits first.

## Remaining release gate

The patch has **not yet been runtime-certified**.

Next steps:

1. apply the generated `.mpp` to the exact Gboard 18.0.3 APKM;
2. verify the patcher completes without classifier/target failures;
3. inspect the patched output package and split structure;
4. install on a test device;
5. confirm Gboard launches and ordinary typing works;
6. test OCR / Scan Text;
7. test voice typing / Agentic Dictation where available;
8. test Tenor search, media loading, and actual sharing;
9. check for crashes, retries, WorkManager churn, or GMS error regressions;
10. inspect runtime logs/network traffic to confirm targeted telemetry paths are suppressed;
11. update this document and the investigation report with the runtime result.

## Review focus

Before merge, reviewers should pay particular attention to:

- the central Clearcut `Llvf.l(...)` completed-success no-op;
- whether retaining both central Clearcut suppression and the Gboard logger gate is appropriate defence in depth;
- ClientTelemetry completed-success behavior;
- preservation of real ClientThrottling behavior while suppressing only the telemetry sender;
- preservation of GMS error notification/dialog behavior while suppressing ClientNotificationTelemetry;
- Daily Ping success-result construction;
- Tenor selective branch target and continuation;
- manifest handling of `android.net.http.EnableTelemetry`;
- version sensitivity of the obfuscated method targets.

## Known non-blocking follow-ups

These are separate future privacy options, not blockers for the current patch:

- optional `Disable AppDoctor remote remediation` patch;
- optional `Disable Voice Donation` patch;
- optional suppression of the separate AICore reflective StatsLog path;
- feature-specific network-disable options for users willing to accept functionality loss;
- a fresh telemetry re-audit for any later Gboard version before reusing these obfuscated anchors.

## Documentation

The detailed investigation, rationale, payload analysis, exact targets, exclusions, and validation evidence are documented in:

```text
docs/telemetry-investigation-gboard-18.0.3.md
```

This progress document is intended as the short handoff/status file; the investigation report remains the authoritative technical reference.
