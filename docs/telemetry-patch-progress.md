# Block Gboard Telemetry - Progress Update

**Branch:** `feature/block-gboard-telemetry`  
**Merged baseline PR:** #1, `Block Gboard telemetry`  
**Merged baseline commit:** `74c17f4a173995eeb069c910a5a5a852f1d1d084`  
**Target:** Gboard `18.0.3.954559732-release` (`175940518`, arm64-v8a)  
**Updated:** 2026-09-18

## Current status

PR #1 merged the original all-blocking telemetry patch after static, build, device, and network validation.

Development then continued on this branch to add **per-source telemetry controls** inside the existing Gboard Patches settings UI.

Current configurable state:

- investigation for the defined Gboard 18.0.3 telemetry scope: **complete**;
- baseline all-blocking implementation: **merged and device/network validated**;
- configurable settings/runtime policy: **implemented**;
- all six configurable groups default to **blocked**;
- product catalog and feature-marker wiring: **updated**;
- conditional control-flow validation: **hardened to verify exact branch destinations**;
- repository tests/build/generation: **passed** on GitHub Actions run `35327198906` at commit `8e3002f45a9be0472b4be45884ed873bac4a237b`;
- current configurable build device regression: **still required** before claiming the allow/unblock paths are runtime validated.

The production branch contains no PCAPdroid user-CA trust configuration.

## Configurable telemetry groups

The settings screen exposes six independent groups:

1. **Gboard & ML Kit Clearcut**
2. **Google Play services telemetry**
3. **Gboard Daily Ping**
4. **Primes diagnostics**
5. **Tenor share tracking**
6. **Cronet network telemetry**

Every preference defaults to blocking. Preference read failures also fail closed to blocking.

Changes are persisted in Gboard Patches settings and take effect after restarting Gboard from the Patches toolbar so process-local runtime policy caches are recreated.

## Current implementation

The configurable implementation uses runtime policy guards rather than permanently replacing every stock path.

When a group is blocked, its narrow telemetry path takes the same successful/no-op behavior used by the merged baseline patch. When a group is allowed, execution branches back to the original Gboard code.

Current bytecode targets:

| # | Target | Blocked behavior |
| ---: | --- | --- |
| 1 | `Llvf;->l(Lkth;)Llsz;` | Clearcut submission returns completed-success Task |
| 2 | `Lprn;->b()Z` | suppress Gboard Clearcut logger creation |
| 3 | `Llbu;->a(Llbd;)Llsz;` | ClientTelemetry returns completed-success Task |
| 4 | `Llbr;->a(Lkzr;)V` | ClientThrottlingTelemetry no-op |
| 5 | `Llbo;->a(Lkzn;)V` | ClientNotificationTelemetry no-op |
| 6 | `DailyPingWorker;->c()Lwzc;` | return successful worker result |
| 7 | `Lqjg;->fG(Context,Lptt;)V` | skip Primes startup |
| 8 | `NativeCrashHandlerImpl;->a(Luaj;)V` | skip native-crash sidecar |
| 9 | `LifeboatReceiver;->onReceive(Context,Intent)V` | skip Lifeboat retransmission |
| 10 | `Lgqq;->E(Lwnj;Lgkc;)V` | skip only Tenor `/v2/registershare` block |
| 11 | `Lacru;->c(Context,Lacrp;)Z` | force Cronet telemetry decision false |

The public patch now depends on:

- `gboardPatchesSettingsPatch`;
- `gboardTelemetryFeatureMarkerPatch`;
- `gboardTelemetryBytecodePatch`.

The old standalone `GboardTelemetryManifestPatch.kt` was removed. Cronet configurability is implemented at its stock telemetry decision method instead, allowing the switch to restore stock behavior when telemetry is explicitly allowed.

Feature marker:

```text
dev.jason.gboardpatches.feature.telemetry_blocking
```

## Explicitly retained

The patch continues to leave these functional or consent-related paths unchanged:

- `UsageReporting.API` and Android Usage & diagnostics consent plumbing;
- `Audit.API` consent/compliance records;
- AppDoctor remote remediation;
- authentication and account APIs;
- OCR execution and module install;
- voice recognition and Agentic Dictation functional requests;
- model and module downloads;
- Phenotype and remote configuration;
- Tenor search, download, media delivery, insertion, and user-visible sharing;
- Voice Donation consent infrastructure;
- other feature-required network traffic.

## Repository validation

The reusable branch verification workflow runs:

```text
./gradlew test :patches:buildAndroid generatePatchesList
```

Current configurable implementation:

```text
GitHub Actions run: 35327198906
Result: success
Head: 8e3002f45a9be0472b4be45884ed873bac4a237b
```

The run compiled the new dexlib2 control-flow tests and passed the complete repository test/build/generation gate.

The telemetry regression tests verify:

- exact stock-branch destination for completed-success Task guards;
- exact stock-branch destination for return-void guards;
- exact stock-branch destination for forced-boolean guards;
- exact stock-branch destination for Daily Ping;
- both Tenor register-share and continuation branch destinations;
- runtime ABI register shapes for the six policy calls.

The product catalog declares the telemetry feature marker, the current consumer files, all six runtime calls, and a synchronized SHA-256 digest.

## Baseline exact-target and device validation

Before PR #1 was merged, exact target validation established:

- 10/10 original telemetry target methods matched;
- class/signature, access flags, register counts, and try-block counts matched;
- 56 stock-body/string/method/field sentinels matched;
- the Tenor skip continuation matched stock control flow;
- completed-success Task behavior matched the expected task type;
- Daily Ping success result matched the worker return interface.

Runtime testing on an OPPO CPH2765 / Android 16 exercised:

- cold startup;
- ordinary typing;
- voice typing;
- OCR / Scan Text;
- Tenor category/autocomplete traffic;
- fresh Tenor search;
- GIF media loading;
- selecting and sending a GIF.

A temporary validation-only build trusted user-installed CAs so PCAPdroid could decrypt HTTPS. With QUIC blocked for the test, the decisive Tenor capture showed:

- `GET /v2/search` -> HTTP 200;
- a subsequent `media.tenor.com` GIF fetch -> HTTP 200;
- the selected GIF successfully sent;
- no `/v2/registershare` request.

This validates the blocked Tenor behavior of the merged baseline. It does not by itself validate the new user-selectable allow path.

## Cronet implementation history

The stock target manifest contains:

```text
android.net.http.EnableTelemetry=true
```

During baseline development, runtime inspection showed that an early manifest mutation did not persist as expected. The merged PR #1 implementation therefore hardened an execute-stage manifest patch that forced the metadata value to `false`.

That static manifest implementation was valid for an always-blocking patch but could not support a user switch that restores stock behavior.

The configurable follow-up therefore removes the production manifest mutation and instead guards:

```text
Lacru;->c(Landroid/content/Context;Lacrp;)Z
```

When Cronet blocking is enabled, the guard returns `false`. When it is disabled, control resumes at the stock method, including its normal `android.net.http.EnableTelemetry` handling.

The earlier execute-stage manifest work remains useful validation history, but it is no longer the production mechanism on this branch.

## Remaining validation before second PR

Repository validation is green. The remaining gate is a device regression pass of the **current configurable build**, with particular attention to:

- default all-blocked startup and ordinary keyboard use;
- changing each telemetry group and restarting Gboard;
- confirming an allowed group actually reaches its stock path;
- confirming a blocked group still suppresses its reporting path;
- Tenor search/send behavior with Tenor blocking enabled and disabled;
- Cronet policy behavior with its switch enabled and disabled;
- the separate `:primes_lifeboat` process loading the saved Primes policy;
- rare Google Play services availability/error-notification paths where practical.

Obfuscated anchors remain version-sensitive. A later Gboard version requires a fresh target audit.

## Documentation

The detailed investigation and rationale remain in:

```text
docs/telemetry-investigation-gboard-18.0.3.md
```

This file is the current branch status/handoff record.
