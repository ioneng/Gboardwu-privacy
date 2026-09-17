# Gboard 18.0.3 Telemetry and Diagnostics Investigation

**Status:** Investigation complete; implementation built and statically validated; runtime validation pending  
**Target package:** `com.google.android.inputmethod.latin`  
**Target version:** `18.0.3.954559732-release`  
**Version code:** `175940518`  
**Architecture:** arm64-v8a  
**Feature branch:** `feature/block-gboard-telemetry`  
**Review PR:** #1 — `Block Gboard telemetry`  
**Scope updated:** 2026-09-17

## 1. Purpose

This document records the reverse-engineering investigation and implementation rationale for the **Block Gboard Telemetry** patch.

The investigation was performed against the exact Gboard 18.0.3 build listed above, not against a generic or newer Gboard release. Obfuscated class and method names in this report therefore refer to this specific build and must be treated as version-sensitive anchors.

The core patching principle is:

> Preserve functional keyboard, Google API, model, voice, OCR, configuration, and media requests. Suppress only separable telemetry, diagnostics, crash/performance reporting, tracking sidecars, and system-level telemetry that is not required for the requested feature to work.

The patch intentionally does **not** attempt to make Gboard fully offline or eliminate all network communication.

## 2. Explicit scope boundary

### In scope

The investigation and patch cover dedicated reporting paths such as:

- Gboard Clearcut logging;
- ML Kit / OCR Clearcut logging;
- Google Play Services ClientTelemetry;
- ClientThrottlingTelemetry;
- ClientNotificationTelemetry;
- Daily Ping periodic metrics;
- Primes performance and crash telemetry;
- Tenor `registershare` tracking;
- Cronet Android telemetry / StatsLog integration;
- related consent gates and reporting-side infrastructure where needed to understand the above.

### Out of scope by design

The following functional traffic is deliberately left unchanged for now:

- voice recognition and speech backend requests;
- Agentic Dictation functional requests;
- Tenor search, result delivery, media download, and actual share functionality;
- remote configuration / Phenotype;
- model and module downloads;
- OCR execution itself;
- Google account and authentication operations;
- Pseudonymous ID use where it is part of a functional voice/backend request;
- Trusted Time where used for functional quota/timing behavior;
- other network operations required to deliver an explicitly requested Gboard feature.

Optional data-donation and remote-remediation systems are documented separately rather than silently folded into the telemetry patch.

## 3. Target artifact

The source APKM used for the investigation was:

```text
com.google.android.inputmethod.latin_18.0.3.954559732-release-arm64-v8a-175940518_7dpi_3feat_9613a431add5e33c11e39b620fe209fc_apkmirror.com_apkm
```

Confirmed characteristics:

- app: Gboard - Google Keyboard;
- package: `com.google.android.inputmethod.latin`;
- version: `18.0.3.954559732-release`;
- version code: `175940518`;
- architecture: arm64-v8a;
- minimum API: 32;
- APKM containing `base.apk` plus feature and density splits;
- executable Java/Kotlin code primarily distributed across four DEX files in `base.apk`.

## 4. Investigation method

The work combined:

- APKM extraction;
- DEX string inspection;
- method and field xref analysis;
- targeted method disassembly;
- method-signature, access-flag, register-count, and try-block inspection;
- payload/Parcelable reconstruction;
- call-site enumeration;
- endpoint and service-action inspection;
- manifest inspection;
- targeted native-library string review;
- comparison with unobfuscated or less-obfuscated Google Play Services implementations where useful;
- source-level patch verification against Morphe/dexlib2 instruction helpers;
- exact-target verification against the supplied Gboard APKM.

The investigation distinguishes three levels of confidence:

1. **Confirmed:** direct code path, payload, or transport was traced in the target APK.
2. **Strongly supported:** behavior is clear from call flow and corroborating implementation evidence, but not observed dynamically.
3. **Not established:** a library, string, or capability exists, but a live Gboard reporting path was not proven.

Static analysis cannot prove server-side behavior or guarantee runtime stability. Runtime patching, installation, feature smoke tests, and network observation remain required before release-grade claims are made.

## 5. Executive summary

The dedicated reporting systems identified in this build are:

1. Gboard application Clearcut logging;
2. ML Kit / OCR Clearcut logging using log source `FIREBASE_ML_SDK`;
3. Google Play Services ClientTelemetry;
4. ClientThrottlingTelemetry;
5. ClientNotificationTelemetry;
6. Daily Ping periodic metrics;
7. Primes performance/crash telemetry;
8. Tenor `registershare` tracking;
9. Cronet Android telemetry written through Android StatsLog/statsd.

The final patch suppresses those paths without intentionally disabling the corresponding functional APIs.

The investigation also classified several nearby systems that should **not** be treated as ordinary telemetry:

- `UsageReporting.API` — consent/settings infrastructure;
- `Audit.API` — consent/compliance audit activity, including Voice Donation consent records;
- AppDoctor — remote remediation;
- Voice Donation — explicit opt-in data donation;
- functional voice, OCR, model, auth, Tenor search, and remote-config traffic.

## 6. Relative privacy severity

This ordering is qualitative and relative to the telemetry systems in this build. It is not a claim that any one channel contains typed text.

| Relative severity | System | Reason |
| --- | --- | --- |
| High | Gboard Clearcut / app metrics | Application-level behavior and metrics; closest to Gboard feature activity |
| High | ML Kit `FIREBASE_ML_SDK` Clearcut | Separate ML Kit operational event stream beyond ClientTelemetry |
| Medium-high | Tenor `registershare` | Explicitly records share activity for Tenor media |
| Medium-high | ClientTelemetry | Reports which Google API methods execute, outcome/status, timing, module/service metadata |
| Medium | Daily Ping | Periodic reporting/metrics activity |
| Medium | Primes | Performance and crash diagnostics, including native crash handling |
| Low-medium | ClientThrottlingTelemetry | Reports API throttle events, method/package/limit metadata |
| Low | ClientNotificationTelemetry | Reports GMS availability/error notification or resolution-dialog events |
| Low | Cronet StatsLog telemetry | Operational networking telemetry routed through Android statsd |

No evidence was found that the three GMS Client* telemetry payloads contain ordinary typed text, suggestions, clipboard text, OCR output, or notification message bodies.

## 7. Clearcut: Gboard application metrics

### 7.1 Gboard logger gate

A clear Gboard-side gate exists at:

```text
Lprn;->b()Z
```

Relevant strings include:

```text
shouldNotCreateLogger
ClearcutLoggerFactory
shouldCreateLogger(): isGMSCoreSafeToConnect=false
shouldCreateLogger(): disabled for tests
```

Returning true suppresses creation of the normal Gboard Clearcut logger.

The patch keeps this as a defence-in-depth control.

### 7.2 Central Clearcut submission

The investigation later found a stronger target:

```text
Llvf;->l(Lkth;)Llsz;
```

This is the low-level Clearcut log-event submission path.

It has only two direct caller families relevant here:

```text
normal Gboard Clearcut log builder
        -> Llvf.l(...)

ML Kit event logger
        -> Llvf.l(...)
```

The patch replaces the submission with a completed-success Task rather than throwing or returning null. This prevents telemetry transmission while preserving caller control flow.

### 7.3 Why both controls are retained

The Gboard logger-creation gate reduces unnecessary logger setup for the main Gboard metrics path.

The central `Llvf.l(...)` no-op provides broader coverage and also captures embedded Clearcut users such as ML Kit.

Together they provide a stronger guarantee than manipulating only the system Usage & diagnostics checkbox state.

## 8. ML Kit / OCR secondary telemetry

The investigation confirmed that ML Kit has an event logger beyond GMS ClientTelemetry.

The path is approximately:

```text
ML Kit / OCR operation
    |
    +-- ClientTelemetry producer (`mlkit:vision`)
    |
    +-- ML Kit event logger
            -> serialized ML Kit event
            -> log source `FIREBASE_ML_SDK`
            -> Clearcut submission
```

This is important because blocking ClientTelemetry alone would not eliminate ML Kit operational event logging.

The central Clearcut submission no-op at `Llvf.l(...)` covers this secondary ML Kit path.

This does **not** imply that Firebase Analytics is active. `FIREBASE_ML_SDK` is the Clearcut log-source name used by the embedded ML Kit component.

## 9. UsageReporting / Usage & diagnostics consent infrastructure

### 9.1 What it is

Gboard includes the Play Services `UsageReporting.API` service and capability strings including:

```text
usage_and_diagnostics_listener
usage_and_diagnostics_consents
usage_and_diagnostics_check_consents
usage_and_diagnostics_settings_access
el_capitan
stats
```

In this Gboard build, the live consumers traced for the usage-reporting query/listener interface are:

1. Gboard `BaseClearcutAdapter`;
2. Primes Clearcut metric transmission.

It is therefore a consent/settings source for some telemetry, not a universal telemetry uploader.

### 9.2 BaseClearcut behavior

`BaseClearcutAdapter` queries the Usage & diagnostics state and listens for changes.

Method keys observed:

```text
4501 = query state
4507 = register listener
4508 = unregister listener
```

The resolved state controls an internal suppression flag:

```text
suppression = true  -> do not log / clear pending Clearcut work
suppression = false -> logging allowed
suppression = null  -> consent unresolved; work may be temporarily queued
```

The normal opt-in state allows logging; other resolved states suppress it.

### 9.3 Fail-open behavior

If the consent query fails, Gboard sets the suppression state to **allow logging**.

In simplified form:

```text
query succeeds
    -> opted in  -> allow
    -> otherwise -> suppress

query fails
    -> allow
```

Primes has a similar fail-open fallback: failure to fetch the UsageReporting state resolves to permission to transmit.

This means the system checkbox is a real privacy gate, but not a strong fail-closed boundary.

### 9.4 Patch decision

`UsageReporting.API` is intentionally **not patched**.

Reasons:

- it is consent infrastructure, not the telemetry transport itself;
- fabricating consent values is less robust than disabling the terminal sender;
- Google can change enum/state details independently of the telemetry transport;
- direct transport suppression provides stronger privacy behavior;
- retaining the API preserves normal system consent plumbing.

## 10. ClientTelemetry

### 10.1 Service and role

The central target is:

```text
Llbu;->a(Llbd;)Llsz;
```

`Llbu` is the GMS ClientTelemetry logging client implementation.

The service is backed by the GMS telemetry service rather than a direct Gboard HTTP endpoint.

### 10.2 Payload

The method-invocation record represented by `Llau` contains fields corresponding to:

- method key / API operation ID;
- result/status;
- connection result/status;
- start time;
- end time;
- calling module ID;
- calling entry point;
- GCore service ID;
- latency.

No evidence was found in this payload for:

- typed text;
- suggestions;
- clipboard contents;
- OCR-recognized text;
- account names;
- notification message bodies.

### 10.3 Producers found

The central sender receives records from multiple subsystems, including:

- generic GoogleApiManager method-invocation telemetry;
- Cronet-related GMS telemetry;
- Google auth/account operations;
- DroidGuard;
- Trusted Time;
- ML Kit Vision.

Blocking only one producer would therefore be incomplete.

### 10.4 Patch behavior

The central sender is replaced with a **completed-success Task**.

Functional API calls remain separate. Callers receive success from the telemetry submission rather than an exception, null, or retry-triggering failure.

## 11. ClientThrottlingTelemetry

### 11.1 Distinguish real throttling from throttle reporting

Gboard / GMS contains a genuine local API throttling mechanism that protects service concurrency and request limits.

That behavior is preserved.

The reporting transport is separate:

```text
Llbr;->a(Lkzr;)V
```

The payload includes throttle-related metadata such as:

- Google API method key;
- package name;
- active/max-inflight or throttle-limit related values.

No evidence was found that the throttle-report payload carries user text/content.

### 11.2 Patch behavior

The telemetry sender returns immediately with `return-void`.

The actual local throttle decision remains intact.

This distinction is important: disabling the throttle mechanism itself could destabilize functional GMS requests, while disabling only the report is low risk.

## 12. ClientNotificationTelemetry

### 12.1 What it actually reports

This channel does **not** report ordinary Gboard notification content.

It is part of Google Play Services availability/error-resolution machinery.

The flow is:

```text
GMS API problem requiring user intervention
    -> Google Play Services error/update notification or resolution dialog
    -> ClientNotificationTelemetry event
```

The central sender is:

```text
Llbo;->a(Lkzn;)V
```

### 12.2 Payload

The live payload producer constructs a record containing:

```text
clientMethodKey
packageName
timestamp
connectionResult/errorCode
notification-vs-dialog flag
```

No notification title/body/message text, typed text, clipboard data, OCR text, or PendingIntent contents were found in this payload.

### 12.3 Patch behavior

The sender is replaced with `return-void`.

The actual GMS error notification/dialog is created elsewhere and remains functional.

## 13. Daily Ping

The worker target is:

```text
Lcom/google/android/libraries/inputmethod/dailyping/DailyPingWorker;->c()Lwzc;
```

The stock worker participates in periodic `daily_ping_work` metrics/reporting.

The patch returns a normal successful worker-result object rather than killing the worker framework or returning null.

This preserves WorkManager-like success semantics and avoids retries or scheduler churn.

## 14. Primes performance and crash telemetry

Several independent Primes paths were identified.

### 14.1 Primes module startup

Target:

```text
Lqjg;->fG(Landroid/content/Context;Lptt;)V
```

String evidence includes:

```text
PrimesModule.onCreate
```

The patch returns before Primes initialization.

Later callbacks already tolerate the relevant uninitialized state and return without performing work.

### 14.2 Native crash handler

Target:

```text
Lcom/google/android/libraries/performance/primes/metrics/crash/NativeCrashHandlerImpl;->a(Luaj;)V
```

This path starts native crash sidecar handling and includes the string:

```text
Primes-nativecrash-sidecar
```

The patch returns before sidecar startup.

### 14.3 Lifeboat receiver

Target:

```text
Lcom/google/android/libraries/performance/primes/transmitter/LifeboatReceiver;->onReceive(Landroid/content/Context;Landroid/content/Intent;)V
```

This receiver can reflectively instantiate Primes metric transmitters after crash/lifeboat events.

The patch returns before transmitter execution.

### 14.4 Primes Clearcut transmitter

Primes also contains a Clearcut metric snapshot transmitter. Its consent logic independently consults UsageReporting and contains a fail-open fallback when the consent lookup fails.

The combination of:

- preventing Primes startup;
- suppressing native crash handling;
- suppressing Lifeboat transmission;
- central Clearcut submission no-op;

provides layered coverage.

## 15. Tenor `registershare`

### 15.1 Endpoint

The target build contains:

```text
https://tenor.googleapis.com/v2/registershare
```

This is distinct from Tenor search and media delivery.

### 15.2 Call site

The share-registration block exists inside a larger multipurpose expression-metrics method:

```text
Lgqq;->E(Lwnj;Lgkc;)V
```

Returning from the entire method would be too broad and could suppress unrelated behavior.

### 15.3 Patch behavior

The patch inserts a selective branch around only the Tenor share-registration block and resumes at the same continuation used by stock control flow.

This is intended to preserve:

- GIF search;
- GIF/media download;
- actual user sharing;
- the rest of the containing metrics/processing method;

while preventing the separate `registershare` tracking request.

The continuation point was checked against the exact target DEX.

## 16. Cronet Android telemetry

The target manifest contains:

```xml
<meta-data
    android:name="android.net.http.EnableTelemetry"
    android:value="true" />
```

Cronet's Android telemetry path writes operational data through Android `StatsLog`/statsd rather than through a dedicated Gboard HTTP telemetry endpoint.

The patch changes the metadata value to:

```xml
android:value="false"
```

This disables Cronet's Android telemetry integration while retaining Cronet networking itself.

## 17. AICore reflective StatsLog path

A separate reflective StatsLog implementation was found with references to:

```text
android.util.StatsLog
android.util.StatsEvent
StatsEvent.Builder
Failed to write stats reflectively. Telemetry is disabled.
```

Its callers lead into AICore service-connection/lifecycle reporting such as connection outcomes and timing.

This appears to be local Android statsd telemetry rather than a dedicated Gboard uploader.

It is **not currently included** in the Block Gboard Telemetry patch. It is classified as a low-priority optional strict-privacy candidate because:

- it appears operational rather than content-bearing;
- it is separate from the higher-value reporting paths already blocked;
- the current patch scope is intentionally conservative where functionality and semantics are less thoroughly proven.

## 18. Audit.API

`Audit.API` was traced into consent/compliance activity, including Voice Donation consent changes.

Relevant behavior includes audit records around events such as:

```text
user opt-in voice donation
user opt-out voice donation
```

This is not ordinary behavioral analytics.

Blocking Audit.API could create an undesirable mismatch where the local client records a consent change but the corresponding consent/compliance audit record is not delivered.

**Decision:** retain Audit.API unchanged.

## 19. AppDoctor

AppDoctor is a remote-remediation framework rather than a conventional analytics pipeline.

The target build references:

```text
content://com.google.android.gms.common.appdoctor/fixes
com.google.android.libraries.appdoctor.ACTION_TELE_DOCTOR_FIX
```

and reports remediation lifecycle state such as:

```text
get_fixes
mark_fix_attempted
mark_fix_completed
```

with fix identifiers.

This means AppDoctor does expose remote mutability/control, but disabling it is conceptually different from blocking telemetry.

**Decision:** do not include AppDoctor in the default telemetry patch. If desired, implement a separate optional patch such as `Disable AppDoctor remote remediation`.

## 20. Voice Donation

The native speech/SODA stack contains evidence of optional upload/data-donation functionality, including:

```text
speech.soda.AudioLoggingUploadTaskInput
```

Gboard also contains explicit Voice Donation consent UI and state tracking.

This is materially different from ordinary speech recognition traffic:

```text
voice recognition -> functional request required to provide requested feature
voice donation    -> optional contribution of speech/audio data
```

The available evidence indicates that Voice Donation is consent-gated.

**Decision:** do not silently fold Voice Donation into the general telemetry patch. A separate optional `Disable Voice Donation` patch can be considered later if desired.

## 21. Firebase components

Firebase-related shared infrastructure is present, particularly Dynamic Links support.

The investigation did **not** establish an active Firebase Analytics or Crashlytics pipeline in this target build.

Dynamic Links code includes behavior indicating that Analytics would need to be added for event logging.

**Conclusion:** no additional Firebase Analytics/Crashlytics kill switch is required for the current telemetry patch.

## 22. Native-library sweep

A targeted native string review found many native metrics/event definitions, including keyboard, decoder, input-session, writing-tools, training/cache, and speech-related metric names.

These appear to be event/protobuf producers feeding the reporting infrastructure already mapped rather than independent third-party analytics SDKs.

No conventional third-party analytics/crash SDKs were identified, including no positive evidence for active:

- Sentry;
- Crashlytics;
- Adjust;
- AppsFlyer;
- Branch;
- Mixpanel;
- Amplitude;
- Bugsnag;
- Datadog;
- New Relic;
- Singular/Kochava/Snowplow-style independent analytics stacks.

## 23. Functional Google APIs deliberately preserved

The investigation separately identified several functional GMS APIs. These should not be confused with the telemetry sidecars.

Examples include:

| API / subsystem | Functional role | Decision |
| --- | --- | --- |
| GoogleAuth.API | account visibility/capability checks | preserve |
| ModuleInstall.API | optional ML/OCR module availability/install | preserve |
| Phenotype.API | remote flags/configuration/experiments | preserve |
| PseudonymousId.API | functional backend/voice request context in some paths | preserve for now |
| TrustedTime.API | Agentic Dictation quota/timing support | preserve |
| Pay.API | Brazilian PIX clipboard/payment feature | preserve |
| DynamicLinks.API | migration/sharing link handling | preserve |
| Help.API | user-initiated help | preserve |
| Feedback.API | user-initiated/support feedback | preserve |
| Audit.API | consent/compliance audit | preserve |
| SignIn/shared GMS infrastructure | shared setup; no telemetry conclusion | preserve |
| DroidGuard | functional service/integrity-related infrastructure | preserve |

The patch blocks reporting about some of these operations through ClientTelemetry without disabling the functional operation itself.

## 24. Implemented bytecode targets

The final implementation contains ten bytecode targets.

| # | Target | Behavior |
| ---: | --- | --- |
| 1 | `Llvf;->l(Lkth;)Llsz;` | central Clearcut submission -> completed-success Task |
| 2 | `Lprn;->b()Z` | Gboard Clearcut logger gate -> suppress logger creation |
| 3 | `DailyPingWorker;->c()Lwzc;` | return successful worker result |
| 4 | `Lqjg;->fG(Context,Lptt;)V` | skip Primes startup |
| 5 | `NativeCrashHandlerImpl;->a(Luaj;)V` | skip native crash sidecar startup |
| 6 | `LifeboatReceiver;->onReceive(Context,Intent)V` | skip Primes Lifeboat transmission |
| 7 | `Llbu;->a(Llbd;)Llsz;` | ClientTelemetry -> completed-success Task |
| 8 | `Llbr;->a(Lkzr;)V` | ClientThrottlingTelemetry -> `return-void` |
| 9 | `Llbo;->a(Lkzn;)V` | ClientNotificationTelemetry -> `return-void` |
| 10 | `Lgqq;->E(Lwnj;Lgkc;)V` | selective branch around Tenor `registershare` only |

A separate manifest patch disables Cronet telemetry metadata.

## 25. Public patch structure

The public patch is named:

```text
Block Gboard Telemetry
```

Feature ID:

```text
block_gboard_telemetry
```

The public registry entry depends on:

- the telemetry bytecode patch;
- the telemetry manifest/resource patch.

The product catalog was advanced to version `1.13.0` and includes the new version-sensitive feature.

The implementation remains selected-only: the internal telemetry transformations are only active when the public feature is selected.

## 26. Patch-safety design choices

Several implementation rules were followed deliberately.

### 26.1 Prefer terminal telemetry transports

Where possible, suppress the final reporting transport rather than changing an upstream functional producer.

Examples:

- central ClientTelemetry sender rather than every API producer;
- central Clearcut submission rather than every event producer;
- ClientThrottlingTelemetry sender rather than local throttle logic;
- ClientNotificationTelemetry sender rather than GMS error UI.

### 26.2 Return success where callers expect asynchronous completion

For methods returning Tasks/futures, the patch returns a completed-success object rather than null or failure.

This avoids:

- retries;
- failure callbacks;
- null dereferences;
- altered functional API behavior.

### 26.3 Preserve scheduler semantics

Daily Ping returns a successful worker result instead of breaking the worker framework.

### 26.4 Avoid broad method removal

The Tenor target is inside a multipurpose method, so only the register-share block is skipped.

### 26.5 Do not spoof consent unless necessary

UsageReporting remains intact. Privacy is enforced at the reporting transport instead of by fabricating a system opt-out state.

## 27. Build and test validation

The implementation has passed the repository's build/test validation.

Successful Gradle invocation included:

```text
./gradlew :patches:test :patches:buildAndroid generatePatchesList
```

Confirmed successful tasks included:

- `:patches:compileKotlin`;
- `:patches:verifyGboardCapabilityWiring`;
- `:patches:buildAndroid`;
- `:patches:test`;
- `:patches:generatePatchesList`.

The full test suite passed after updating the catalog contract:

```text
271 tests passed
BUILD SUCCESSFUL
```

The successful build generated the Android Morphe patch bundle, including:

```text
patches-3.10.0.mpp
```

## 28. Exact-APK structural validation

The bytecode implementation was independently checked against the exact supplied Gboard 18.0.3 APKM.

Validation results:

- 10/10 hard-coded bytecode targets matched;
- exact class and method signatures matched;
- expected access flags matched;
- register counts matched;
- try-block counts matched;
- 56 stock-body/string/method/field sentinels matched;
- the unusual native-crash method access flags were verified;
- the large Tenor processor register shape was verified;
- the Tenor skip continuation was checked against stock branch targets;
- the completed-success Task helper was verified to construct/resolve the expected task type;
- the Daily Ping success object was verified to implement the declared worker return interface.

This materially reduces the risk of applying a transformation to the wrong obfuscated method, but it does not replace runtime testing.

## 29. Runtime-validation status

Runtime validation is **not yet complete**.

Preparation already completed:

- clean feature branch implementation built successfully;
- `.mpp` patch bundle exported;
- current Morphe Desktop v1.16.0 identified and fetched through an isolated validation workflow;
- Morphe Desktop supports `.apkm` input directly, so the exact source APKM can be used without manually merging splits first.

Remaining runtime work:

1. apply `Block Gboard Telemetry` to the exact source APKM;
2. confirm patcher completes without target/classifier failure;
3. inspect the resulting package/splits;
4. install on a test device;
5. verify keyboard startup and ordinary typing;
6. exercise OCR/Scan Text;
7. exercise voice typing / Agentic Dictation where available;
8. exercise Tenor search/download/share and confirm sharing still works;
9. exercise GMS error/availability paths where practical;
10. inspect logs/network behavior for regressions;
11. verify the targeted telemetry requests/transports no longer execute or transmit;
12. verify no unexpected retry loops or worker churn occur.

Until those steps are complete, the correct claim is:

> The patch is compile-clean, test-clean, and exact-target statically validated, but not yet runtime-certified.

## 30. Known residual privacy surfaces

Even with the telemetry patch enabled, Gboard can still send data as part of functional features.

Examples include:

- speech recognition requests;
- Agentic Dictation requests;
- Tenor search queries and media requests;
- Phenotype/configuration requests;
- model/module downloads;
- account/auth operations;
- optional features using pseudonymous identifiers;
- other service calls required by enabled online functionality.

These are intentionally outside the current patch scope.

A future privacy-hardening project could offer independent optional controls for selected functional-network features, but those controls should be explicit because they may break or degrade the corresponding feature.

## 31. Remaining optional investigations / future patches

The following are reasonable separate follow-ups rather than blockers for the current telemetry patch:

- **Disable AppDoctor remote remediation** — removes remote corrective-action infrastructure;
- **Disable Voice Donation** — force optional voice-data donation off while preserving voice recognition;
- **Suppress AICore StatsLog telemetry** — stricter local statsd privacy mode;
- feature-specific network controls for users willing to trade functionality for privacy;
- re-audit when supporting a new Gboard version because obfuscated anchors are version-sensitive.

## 32. Final assessment

For the defined scope — dedicated telemetry, analytics, diagnostics, crash/performance reporting, tracking sidecars, and Cronet system telemetry while preserving functional online features — the investigation is considered **essentially comprehensive for Gboard 18.0.3.954559732-release**.

The highest-value finding after the initial pass was that ML Kit has an independent `FIREBASE_ML_SDK` Clearcut path, which is now covered by the central Clearcut submission no-op.

The implementation deliberately avoids disabling functional Google APIs and instead intercepts reporting sidecars at narrow terminal points.

The code has passed repository build/tests and exact-APK structural validation. The remaining release gate is real patched-APKM installation and runtime/network verification.

---

## Appendix A — key telemetry targets

```text
Central Clearcut
Llvf;->l(Lkth;)Llsz;

Gboard Clearcut logger gate
Lprn;->b()Z

Daily Ping
Lcom/google/android/libraries/inputmethod/dailyping/DailyPingWorker;->c()Lwzc;

Primes startup
Lqjg;->fG(Landroid/content/Context;Lptt;)V

Primes native crash
Lcom/google/android/libraries/performance/primes/metrics/crash/NativeCrashHandlerImpl;->a(Luaj;)V

Primes Lifeboat
Lcom/google/android/libraries/performance/primes/transmitter/LifeboatReceiver;->onReceive(Landroid/content/Context;Landroid/content/Intent;)V

ClientTelemetry
Llbu;->a(Llbd;)Llsz;

ClientThrottlingTelemetry
Llbr;->a(Lkzr;)V

ClientNotificationTelemetry
Llbo;->a(Lkzn;)V

Tenor selective share-registration block
Lgqq;->E(Lwnj;Lgkc;)V
```

## Appendix B — notable method keys observed during investigation

These keys are useful for interpreting ClientTelemetry / GMS API activity in this exact build:

```text
GoogleAuth get accounts             1676
GoogleAuth capability/status        1682
ModuleInstall check/install         27301
PseudonymousId fetch                3901
Pay / PIX path                      7340
Feedback                            6010 / 6011
Audit                               6901
UsageReporting query                4501
UsageReporting listener setup       4507
TrustedTime                         29801
```

These identifiers describe functional or support APIs; their presence does not mean those APIs are disabled by the telemetry patch.
