# Gboard 18.0.3 Telemetry and Diagnostics Investigation

**Status:** Review draft  
**Target APK:** `com.google.android.inputmethod.latin` 18.0.3.954559732-release  
**Version code:** 175940518  
**Architecture:** arm64-v8a  
**Scope date:** 2026-09-16  

## 1. Purpose and scope

This report documents a static reverse-engineering investigation of telemetry, diagnostics, crash reporting, usage reporting, tracking sidecars, and related Google Play Services reporting paths present in Gboard 18.0.3.954559732-release.

The goal is to identify reporting mechanisms that can be disabled without breaking ordinary keyboard functionality. The intended patching principle is:

> Preserve functional API and network operations, but suppress separate telemetry/reporting sidecars whenever the two can be cleanly separated.

This report intentionally does **not** attempt to block data that is inherently required for an explicitly requested network feature to work. The following remain out of scope for the general telemetry patch unless separately noted:

- voice recognition and speech backend requests;
- Agentic Dictation functional requests;
- Tenor search, download, and result delivery;
- remote configuration / Phenotype;
- model and module downloads;
- OCR execution itself;
- authentication/account operations;
- other feature-required Google API requests.

Optional data-donation or remote-remediation systems are documented separately so they are not mislabeled as ordinary telemetry.

## 2. Test artifact and analysis method

The analysis was performed against the APKM bundle:

`com.google.android.inputmethod.latin_18.0.3.954559732-release-arm64-v8a-175940518_7dpi_3feat_9613a431add5e33c11e39b620fe209fc_apkmirror.com_apkm`

The package identity and build characteristics confirmed during extraction were:

- package: `com.google.android.inputmethod.latin`;
- app: Gboard - Google Keyboard;
- version: `18.0.3.954559732-release`;
- version code: `175940518`;
- architecture: arm64-v8a;
- minimum API: 32;
- executable Java/Kotlin code primarily in four DEX files in `base.apk`.

The investigation used static DEX inspection, string/reference searches, method and field xrefs, targeted disassembly, payload/parcel reconstruction, and comparison with unobfuscated Google Play Services implementations where useful. Obfuscated symbol names therefore refer specifically to this Gboard build and should be treated as version-sensitive anchors.

Static analysis can establish reachable code paths, data structures, call relationships, and likely behavior, but it cannot prove runtime server-side behavior. Any patch should still be validated by building a patched APK, installing it, exercising relevant features, and observing runtime/network behavior.

## 3. Executive summary

The major dedicated telemetry/reporting paths identified in this build are:

1. Gboard application Clearcut logging;
2. ML Kit / OCR Clearcut logging under the `FIREBASE_ML_SDK` log source;
3. ClientTelemetry API-operation telemetry;
4. ClientThrottlingTelemetry;
5. ClientNotificationTelemetry;
6. Daily Ping periodic metrics;
7. Primes performance/crash telemetry;
8. Tenor `registershare` share tracking;
9. Cronet operational telemetry written through Android StatsLog/statsd;
10. an AICore-oriented reflective StatsLog path.

Additional systems investigated but not recommended for inclusion in the general telemetry patch are:

- UsageReporting.API: consent/settings infrastructure;
- Audit.API: consent/compliance audit, notably voice-donation consent changes;
- AppDoctor: remote remediation rather than ordinary analytics;
- Voice Donation: explicit opt-in speech/audio donation and should be handled separately;
- functional network requests needed for requested features.

A key architectural finding is that many telemetry paths can be disabled at narrow terminal send points while leaving the functional operation untouched. This is preferable to disabling Google API clients, networking stacks, consent services, or safety throttling mechanisms.

## 4. Privacy-severity overview

The following ordering is based on the breadth and regularity of behavioral/operational information exposed, not on a claim about legal sensitivity or server-side retention.

### Higher privacy impact

- **Gboard Clearcut application metrics** — application-level behavioral and usage metrics.
- **ML Kit `FIREBASE_ML_SDK` Clearcut** — OCR/ML operational event reporting separate from ClientTelemetry.
- **Tenor `registershare`** — directly reports that a GIF was shared.
- **ClientTelemetry** — reports Google API method invocation, result state, timing, service identifiers, and latency.

### Medium privacy impact

- **Daily Ping** — periodic metrics/heartbeat-like reporting.
- **Primes** — performance/crash diagnostics and crash retransmission infrastructure.
- **ClientThrottlingTelemetry** — reports method keys that hit client-side throttling and their configured limits.

### Lower privacy impact

- **ClientNotificationTelemetry** — reports Play Services API availability/error-notification events.
- **Cronet StatsLog** — local Android statsd operational telemetry.
- **AICore StatsLog** — local Android statsd operational measurements around AICore service connectivity.

## 5. Clearcut / Gboard application metrics

### 5.1 Logger creation gate

A central Gboard Clearcut creation gate was identified at:

`Lprn;->b()Z`

Strings and behavior include:

- `shouldNotCreateLogger`;
- `ClearcutLoggerFactory`;
- `shouldCreateLogger(): isGMSCoreSafeToConnect=false`;
- `shouldCreateLogger(): disabled for tests`.

The method returns a suppression decision before two logger creations in `Lgsl;->iM()`.

A conservative patch can force suppression by prepending:

```smali
const/4 v0, 0x1
return v0
```

This is useful as defense in depth because Gboard is already designed to operate when the logger is not created.

### 5.2 Usage & diagnostics consent interaction

Gboard's `BaseClearcutAdapter` uses `UsageReporting.API` to query the Android/Google "Usage & diagnostics" state and to subscribe to changes.

Observed method keys:

- `4501` — query usage-reporting consent state;
- `4507` — register consent-change listener;
- `4508` — unregister listener.

The relevant state is cached in an `AtomicReference<Boolean>` with the following effective meaning:

- `true` — suppress/throw away Clearcut logging;
- `false` — allow logging;
- `null` — consent unresolved; temporarily queue requests.

Normal result handling is privacy-respecting:

- state `1` — allow logging;
- other states — suppress logging.

However, the failure path is **fail-open**: if the consent query fails, Gboard sets the suppression state to `false`, which allows logging attempts.

This is one reason the telemetry patch should directly disable the telemetry sender rather than rely only on the system consent gate.

### 5.3 Central Clearcut submission

A broader and stronger Clearcut transport point was identified at:

`Llvf;->l(Lkth;)Llsz;`

This method receives a Clearcut log-event builder/event and returns a Task-like object. Both ordinary Gboard Clearcut and the independently constructed ML Kit Clearcut path converge here.

A stronger privacy patch should therefore make this method return an immediately successful completed task, for example using the verified helper:

`Llcw;->aZ(Ljava/lang/Object;)Llsz;`

Conceptually:

```smali
const/4 v0, 0x0
invoke-static {v0}, Llcw;->aZ(Ljava/lang/Object;)Llsz;
move-result-object v0
return-object v0
```

This preserves caller expectations while preventing log submission.

### 5.4 Recommendation

Use both:

- the Gboard logger-creation suppression gate; and
- the central Clearcut successful no-op sender.

The first avoids unnecessary logger creation; the second provides broad coverage for embedded components that construct their own Clearcut clients.

## 6. ML Kit / OCR telemetry

ML Kit Vision/OCR in this build produces telemetry through two distinct mechanisms:

1. generic `ClientTelemetry` under an API tag equivalent to `mlkit:vision`;
2. a separate ML Kit event logger using the Clearcut log source `FIREBASE_ML_SDK`.

This means blocking ClientTelemetry alone is insufficient to eliminate ML Kit operational reporting.

The ML Kit Clearcut path serializes an ML Kit event and ultimately reaches the same central Clearcut submission method described above. Therefore a central Clearcut successful no-op should cover this second channel without disabling OCR functionality itself.

No evidence was found that recognized OCR text itself is placed in the generic ClientTelemetry payload. The ML Kit event schema should still be treated as operational telemetry and suppressed by the Clearcut transport patch.

## 7. ClientTelemetry

### 7.1 Role

`ClientTelemetry.API` is Google Play Services method/API invocation telemetry. It is not the functional API itself and is not required for the API call to complete.

The service client binds to:

`com.google.android.gms.common.internal.service.IClientTelemetryService`

with start action:

`com.google.android.gms.common.telemetry.service.START`

The first hop is Binder IPC into Google Play Services rather than a direct HTTP request from Gboard.

### 7.2 Payload

The method-invocation record in this build contains fields equivalent to:

1. method key / method ID;
2. result/status code;
3. connection-result/status code;
4. start time;
5. end time;
6. calling module ID;
7. calling entry point;
8. GCore service ID;
9. latency.

The batching object contains a telemetry configuration/version and a list of invocation records.

No typed text, suggestions, clipboard contents, recognized OCR text, or similar content was found in this specific ClientTelemetry payload.

### 7.3 Producers

In addition to generic GoogleApiManager method telemetry, direct producers were found for functional subsystems including:

- Cronet dynamite;
- authentication/account operations;
- DroidGuard;
- Trusted Time;
- ML Kit Vision.

This matters because patching only the GoogleApiManager enablement gate would leave direct producers intact.

### 7.4 Central patch point

The preferred narrow target is:

`Llbu;->a(Llbd;)Llsz;`

It constructs the TaskApiCall used to transmit telemetry through Play Services.

Returning a completed-success Task prevents service/Binder transmission while keeping callers on their success path. This is safer than forcing a transport failure because it avoids retry/failure behavior.

### 7.5 Functional impact

Blocking this sender should not disable OCR, Auth, Trusted Time, DroidGuard, Cronet, or the underlying feature that generated the telemetry. The functional operation and the telemetry sidecar are separate.

## 8. ClientThrottlingTelemetry

### 8.1 Role

Client-side throttling is a real functional mechanism. Play Services supplies per-method concurrency limits through connection metadata under:

`com.google.android.gms.common.internal.CONNECTION_THROTTLING_CONFIG`

Gboard's GoogleApi machinery enforces those limits locally. When a call exceeds its configured maximum, a separate telemetry record is generated.

The important distinction is:

- the **throttling mechanism** is functional and should remain;
- **ClientThrottlingTelemetry** merely reports that throttling occurred.

### 8.2 Payload

The throttling event contains:

- throttling limit;
- Gboard package name;
- Google API method key.

The package name resolves to `com.google.android.inputmethod.latin`.

No user-entered content was found in this event object.

Events are accumulated and batched, with values observed equivalent to a roughly 5-second batch delay and up to 100 records.

### 8.3 Service

The dedicated service is:

`com.google.android.gms.common.internal.service.IClientThrottlingTelemetryService`

with action:

`com.google.android.gms.common.telemetry.throttling.service.START`

### 8.4 Central patch point

The central sender is:

`Llbr;->a(Lkzr;)V`

It has a single relevant caller in the investigated path. Replacing the sender body with `return-void` suppresses reporting while preserving the actual throttling behavior.

### 8.5 Functional impact

Expected functional impact is minimal because only reporting is removed. Do **not** disable the underlying connection-throttling checks.

## 9. ClientNotificationTelemetry

### 9.1 Role

This channel is narrower than its name suggests. It does not track ordinary Gboard notifications. It belongs to Google Play Services API-availability/error-resolution handling.

When a Google API problem results in either:

- an Android notification; or
- a Play Services error/resolution dialog,

a telemetry record is sent.

### 9.2 Payload

The payload contains:

- client method key, or `-1` if absent;
- package name;
- timestamp;
- Play Services connection/error code;
- boolean indicating notification versus dialog behavior.

In this build the boolean is observed as:

- `false` — notification path;
- `true` — dialog/resolution path.

No notification body, title, typed text, clipboard data, account name, or other content was found in the parcel.

### 9.3 Service

The service is:

`com.google.android.gms.common.internal.service.IClientNotificationTelemetryService`

with action:

`com.google.android.gms.common.telemetry.notification.service.START`

### 9.4 Central patch point

The central sender is:

`Llbo;->a(Lkzn;)V`

Replacing this sender with `return-void` should leave the actual error notification/dialog and recovery flow intact while suppressing the report that it occurred.

## 10. Functional Google API calls versus telemetry

Investigation of Gboard's Google Play Services API usage showed that normal functional calls include clients for tasks such as:

- visible account/account capability queries;
- optional module availability/install checks, including Vision OCR;
- Phenotype/remote configuration;
- pseudonymous/Zwieback identifier retrieval;
- Trusted Time;
- regional payment/PIX support;
- Dynamic Links;
- Help and Feedback;
- consent/compliance Audit;
- optional/sign-in infrastructure.

Some method keys observed include:

- GoogleAuth: `1676`, `1682`;
- ModuleInstall/OCR: `27301`;
- Pseudonymous ID: `3901`;
- Trusted Time: `29801`;
- Pay/PIX: `7340`;
- Feedback: `6010`, `6011`;
- Audit: `6901`.

Ordinary typing/key handling itself appears predominantly local. The presence of ClientTelemetry does not imply one server API call per keypress.

These functional APIs should not be disabled by the telemetry patch.

## 11. Daily Ping

A dedicated periodic worker was identified at:

`Lcom/google/android/libraries/inputmethod/dailyping/DailyPingWorker;->c()Lwzc;`

It is associated with the string/event family `daily_ping_work`.

The preferred patch is a successful no-op Worker result rather than killing the worker or returning null. A construction path was identified that returns the expected successful result/future object.

This preserves WorkManager semantics and avoids repeated retries while suppressing the periodic reporting operation.

## 12. Primes performance and crash telemetry

Several Primes paths were identified.

### 12.1 Primes module startup

Target:

`Lqjg;->fG(Landroid/content/Context;Lptt;)V`

String marker:

`PrimesModule.onCreate`

A `return-void` at entry prevents initialization. Downstream callbacks inspect the relevant initialized field and already return if it is null, which supports safe no-op behavior.

### 12.2 Native crash sidecar

Target:

`Lcom/google/android/libraries/performance/primes/metrics/crash/NativeCrashHandlerImpl;->a(Luaj;)V`

The method starts the `Primes-nativecrash-sidecar` daemon thread. Prepending `return-void` suppresses this component.

### 12.3 Lifeboat retransmission

Target:

`Lcom/google/android/libraries/performance/primes/transmitter/LifeboatReceiver;->onReceive(Context,Intent)V`

Markers include:

- `PrimesLifeboatReceiver`;
- `MetricSnapshot`;
- `Transmitters`.

This receiver can reflectively instantiate transmitters for crash snapshots. Prepending `return-void` suppresses that retransmission mechanism.

### 12.4 UsageReporting consent

Primes' Clearcut transmitter independently checks UsageReporting consent. It allows transmission for some opt-in states but, importantly, its consent-query failure fallback is also **fail-open** and permits transmission.

Direct suppression of the Primes transport/startup paths therefore gives a stronger privacy guarantee than relying on the checkbox service.

## 13. Tenor register-share tracking

The endpoint:

`https://tenor.googleapis.com/v2/registershare`

is associated with an expression metrics/share path.

The relevant code lives inside a larger multi-purpose method, so returning from the whole method would be unsafe. The appropriate patch is a selective control-flow skip over only the Tenor register-share block, continuing execution at the later non-share logic.

This should preserve GIF search, display, selection, and ordinary feature behavior while suppressing the separate "this GIF was shared" registration request.

Because Tenor may use share signals for ranking/personalization, blocking the event could theoretically affect recommendation quality, but it should not be required to deliver the selected GIF itself.

## 14. UsageReporting.API / Usage & diagnostics

### 14.1 Classification

UsageReporting.API is **consent/settings infrastructure**, not an uploader of Gboard telemetry events.

It exposes capabilities such as:

- `usage_and_diagnostics_listener`;
- `usage_and_diagnostics_consents`;
- `usage_and_diagnostics_check_consents`;
- `usage_and_diagnostics_settings_access`;
- `stats`.

Gboard's live consumers found in this investigation are primarily:

- BaseClearcutAdapter;
- Primes Clearcut transmitter.

### 14.2 Fail-open concern

Both the Gboard Clearcut path and the Primes path contain failure behavior that permits logging/transmission when the consent query fails.

This should be documented as a privacy weakness in the stock behavior, but it is **not** a reason to patch UsageReporting itself.

### 14.3 Recommendation

Leave UsageReporting intact. Disable the actual telemetry senders instead.

Falsifying the device consent state would be more brittle and could affect unrelated Google components. Direct sender suppression is cleaner and more version-auditable.

## 15. Audit.API

### 15.1 Role

Audit.API binds to a Google Play Services audit service and is used in this Gboard build for consent/compliance records, including explicit Voice Donation consent changes.

Strings/callers include concepts equivalent to:

- user toggles voice-donation setting;
- user opt-in voice donation;
- user opt-out voice donation;
- failure to send the corresponding audit record.

### 15.2 Classification

This is better classified as consent/compliance infrastructure than behavioral analytics.

### 15.3 Recommendation

Do **not** disable Audit.API in the general telemetry patch.

If a user revokes an optional consent, it is preferable for the corresponding consent/audit state to be able to propagate rather than leaving local and remote consent records inconsistent.

## 16. AppDoctor

### 16.1 Role

AppDoctor appears to be Google's remote-remediation framework rather than conventional analytics.

Observed markers include:

- `content://com.google.android.gms.common.appdoctor/fixes`;
- `com.google.android.libraries.appdoctor.ACTION_TELE_DOCTOR_FIX`;
- operations equivalent to `get_fixes`, `mark_fix_attempted`, and `mark_fix_completed`.

The system receives a fix/remediation item from Play Services, applies it, and reports remediation lifecycle state using a fix identifier.

### 16.2 Privacy and functionality

This does reveal that a remotely supplied fix was attempted/completed, but its main purpose is remediation, not behavioral telemetry.

Disabling it could reduce remote mutability/control but may also remove Google's ability to repair bad state/configuration.

### 16.3 Recommendation

Do not include AppDoctor in `Block Gboard Telemetry`.

If desired, expose it as a separate optional patch such as `Disable AppDoctor remote remediation`.

## 17. Cronet telemetry

### 17.1 Manifest opt-in

Gboard explicitly enables Cronet telemetry through manifest metadata equivalent to:

```xml
<meta-data android:name="android.net.http.EnableTelemetry" android:value="true" />
```

### 17.2 Transport

The investigated Chromium/Cronet implementation routes these operational statistics into Android `StatsLog`/statsd rather than to a Gboard-specific HTTP telemetry endpoint.

This covers operational information around the networking engine and request/response behavior. Cronet itself can operate with telemetry disabled and substitutes a no-op logging path.

### 17.3 Recommendation

For a strict privacy build, set `android.net.http.EnableTelemetry` to `false`.

This should leave Cronet networking functional while suppressing the statsd telemetry path.

Because this is OS-level diagnostics rather than a dedicated Gboard analytics uploader, it is lower priority than Clearcut/ClientTelemetry.

## 18. Reflective StatsLog / AICore telemetry

Another path dynamically references Android stats logging classes such as:

- `android.util.StatsLog`;
- `android.util.StatsEvent`;
- `StatsEvent.Builder`.

The associated failure message indicates telemetry is disabled when reflective stats writes fail.

Following callers led primarily to AICore service connection/lifecycle measurements, including service-death/binding/disconnection situations.

This appears to be local Android statsd operational telemetry rather than prompt/content upload.

### Recommendation

Classify as low-severity system telemetry. Suppressing it is reasonable for a strict privacy build, but it should be treated as optional/low priority relative to Clearcut and Play Services telemetry senders.

## 19. Firebase components

Firebase-related infrastructure is present, including Dynamic Links/common components, but the audit did **not** find an active conventional Firebase Analytics or Crashlytics reporting implementation in this build.

The Dynamic Links code contains logic indicating that Firebase Analytics would need to be added/enabled for Dynamic Link event logging, which supports the conclusion that Firebase Analytics is not an active additional telemetry channel here.

No separate Firebase analytics/crash kill switch is presently justified.

## 20. Native-library sweep

A targeted native string scan found many metrics/event definitions, including keyboard decoder/input-session/training/cache style metrics, but these appear to be event producers feeding already identified reporting infrastructure rather than independent upload SDKs.

No convincing evidence was found for common third-party analytics/crash SDKs such as:

- Sentry;
- Crashlytics;
- Adjust;
- AppsFlyer;
- Branch;
- Mixpanel;
- Amplitude;
- Bugsnag;
- Datadog;
- New Relic.

The significant native crash-reporting path remains Primes' native crash sidecar, already covered above.

## 21. Voice Donation

The investigation surfaced an additional privacy-relevant subsystem distinct from normal telemetry.

Native speech/SODA code contains an `AudioLoggingUploadTaskInput` concept, and Gboard contains substantial explicit Voice Donation consent state/UI such as:

- voice-donation opt-in timestamp;
- voice-donation opt-out timestamp;
- promo/renewal banners;
- consent expiration/reset behavior;
- explicit opt-in and opt-out audit records.

This looks like optional speech/audio data donation rather than ordinary voice recognition.

### Recommendation

Do not silently bundle this into the telemetry patch because it is a separately consented feature.

If desired, create a separate `Disable Voice Donation` patch that removes donation eligibility/upload behavior while leaving normal voice typing functional.

## 22. Functional network traffic intentionally left unchanged

The following classes of network behavior are intentionally outside the current patch scope:

- voice-recognition/S3 backend traffic;
- Agentic Dictation functional traffic;
- Tenor search/result/media requests;
- remote configuration;
- model/module downloads;
- OCR functionality;
- GoogleAuth/account operations;
- Trusted Time functional calls;
- DroidGuard functional calls;
- other feature-required Google API requests.

Some of these requests can contain identifiers or operational metadata. For example, a PseudonymousId/Zwieback identifier is used in at least some voice-request/authentication context. These are legitimate future privacy-review topics, but blocking them now would risk breaking the requested feature and violates the current patch boundary.

## 23. Recommended `Block Gboard Telemetry` scope

The recommended patch set for this Gboard build is:

1. **Central Clearcut submission** — return completed-success Task.
2. **Gboard Clearcut logger-creation gate** — suppress logger creation as defense in depth.
3. **Daily Ping worker** — return successful no-op result.
4. **Primes startup** — `return-void`.
5. **Primes native crash handler** — `return-void`.
6. **Primes Lifeboat receiver** — `return-void`.
7. **ClientTelemetry sender** — return completed-success Task.
8. **ClientThrottlingTelemetry sender** — `return-void`.
9. **ClientNotificationTelemetry sender** — `return-void`.
10. **Tenor `registershare`** — selective skip of only the tracking block.
11. **Cronet telemetry manifest flag** — set `android.net.http.EnableTelemetry=false`.
12. **AICore/StatsLog path** — optional strict-privacy suppression after implementation review.

The patch should explicitly **not** disable:

- UsageReporting consent infrastructure;
- Audit.API;
- functional Google API clients;
- actual API throttling;
- OCR itself;
- speech recognition;
- Agentic Dictation;
- model downloads;
- remote configuration;
- Tenor search/download/media delivery.

## 24. Suggested separate optional patches

Two privacy-adjacent systems should remain separate from the general telemetry patch:

### Disable AppDoctor remote remediation

Purpose: prevent Play Services from remotely delivering/executing AppDoctor fixes and reporting fix lifecycle state.

Trade-off: reduces remote mutability but may remove supported remediation/recovery behavior.

### Disable Voice Donation

Purpose: suppress optional speech/audio donation and related promotion/eligibility while preserving ordinary voice typing.

Trade-off: removes an explicitly consented contribution feature rather than ordinary telemetry.

## 25. Patch-safety principles

Implementation should follow these rules:

- prefer a narrow sender no-op over disabling an entire functional client;
- return successful Tasks where callers expect asynchronous success;
- preserve WorkManager success semantics for periodic workers;
- preserve Google API throttling itself while disabling its reporting sidecar;
- preserve Play Services error notifications/dialogs while disabling notification telemetry;
- selectively bypass Tenor share tracking rather than returning from a large multi-purpose method;
- avoid broad INTERNET permission removal or generic network blocking;
- keep transformations version-sensitive and verified against stock method shape/sentinels;
- make patches idempotent where possible;
- fail patch application when expected stock structure is not present rather than patching an unknown version blindly.

## 26. Runtime validation plan

Static analysis is not the final validation step. Before release, the patched build should be exercised for:

- ordinary typing and suggestions;
- settings and language switching;
- GIF/Tenor search and share;
- OCR/ML Kit flows;
- voice typing;
- Agentic Dictation if available;
- account-related features;
- model/module downloads;
- Play Services error/recovery behavior;
- app cold/warm startup;
- crash-free idle/background operation;
- WorkManager stability.

Network/IPC validation should look specifically for absence of:

- Clearcut submissions;
- `ClientTelemetry` Binder transmissions;
- `ClientThrottlingTelemetry` Binder transmissions;
- `ClientNotificationTelemetry` Binder transmissions;
- Tenor `/v2/registershare` requests;
- Primes transmitter activity.

At the same time, functional traffic required for enabled features should remain present and successful.

## 27. Confidence and open questions

### High confidence

The following are strongly supported by exact-build static analysis:

- Clearcut logger and central log submission are telemetry;
- ML Kit has a separate `FIREBASE_ML_SDK` Clearcut path;
- ClientTelemetry, ClientThrottlingTelemetry, and ClientNotificationTelemetry are distinct reporting services;
- Daily Ping and Primes are diagnostics/metrics infrastructure;
- Tenor `/v2/registershare` is separate share tracking;
- UsageReporting is consent infrastructure and contains fail-open consumers;
- AppDoctor is remote remediation;
- Audit.API participates in Voice Donation consent/audit handling;
- Cronet telemetry is explicitly enabled and routes through StatsLog/statsd.

### Medium confidence / implementation review needed

- exact safest patch anchor for the reflective AICore StatsLog path;
- whether retaining both the Gboard logger gate and central Clearcut no-op is preferable to central no-op alone in the final implementation;
- whether any future version routes additional non-telemetry Clearcut-like traffic through the same central method.

### Runtime-only questions

- server-side destinations and retention after Play Services receives Binder telemetry;
- feature-specific behavior changes caused by removal of ranking/diagnostic signals;
- exact on-device traffic reduction under real use.

## 28. Final assessment

For the defined scope—dedicated analytics, diagnostics, crash/performance reporting, share tracking, and reporting sidecars around functional APIs—the inventory for Gboard 18.0.3 is now effectively comprehensive.

The strongest implementation strategy is not to remove networking wholesale. Instead, patch the narrow terminal telemetry transports while preserving functional operations and returning normal success contracts to callers. This approach offers broad privacy reduction with substantially lower breakage risk.

The most important implementation change relative to the initial investigation is to use the **central Clearcut submission no-op** in addition to the original Gboard logger-creation gate, because ML Kit constructs its own Clearcut telemetry path and would otherwise remain active.
