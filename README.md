<h1 align="center">GboardWu Privacy</h1>

<p align="center">
  A privacy-focused fork of <a href="https://github.com/jasonwu1994/Gboard-patches">GboardWu / Gboard Patches</a> with an additional telemetry-blocking patch for Gboard.
</p>

<p align="center">
  <a href="https://morphe.software/add-source?github=ioneng/Gboardwu-privacy"><img alt="Add this fork to Morphe" src="https://img.shields.io/badge/Morphe-Add%20Privacy%20Fork-00A8FF?style=for-the-badge"></a>
  <a href="https://github.com/jasonwu1994/Gboard-patches/blob/main/README.md"><img alt="Upstream README" src="https://img.shields.io/badge/Upstream-README-181717?style=for-the-badge"></a>
</p>

## About this fork

This repository keeps the upstream GboardWu / Gboard Patches feature set and adds a privacy-focused patch on top.

The fork-specific addition is **Block Gboard Telemetry**, designed for the supported Gboard `18.0.3.954559732-release` target. It suppresses dedicated telemetry, diagnostics, crash/performance reporting, and Tenor share-registration tracking while deliberately preserving network traffic required for normal Gboard features.

Upstream project and original feature work: [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches).

## Privacy patch

<details open>
  <summary><code>Block Gboard Telemetry</code></summary>

  **Blocked reporting paths**

  - central Clearcut event submission, covering Gboard logging and ML Kit / OCR `FIREBASE_ML_SDK` logging;
  - Gboard's Clearcut logger creation gate as defence in depth;
  - Google Play Services `ClientTelemetry`, `ClientThrottlingTelemetry`, and `ClientNotificationTelemetry` reporting;
  - Daily Ping periodic metrics;
  - Primes startup, native crash sidecar, and Lifeboat crash retransmission;
  - Tenor `/v2/registershare` share tracking only;
  - Cronet Android StatsLog telemetry via `android.net.http.EnableTelemetry=false`.

  **Preserved by design**

  - UsageReporting / Usage & diagnostics consent plumbing;
  - Audit API consent/compliance records;
  - AppDoctor remote remediation;
  - authentication and account operations;
  - OCR execution itself;
  - voice recognition and Agentic Dictation functional requests;
  - model/module downloads and remote configuration;
  - Tenor search, download, media delivery, and user-visible GIF sharing;
  - Voice Donation consent plumbing;
  - other network traffic required for explicitly requested Gboard features.

  **Runtime validation**

  On the supported Gboard 18.0.3 target, ordinary typing, voice input, OCR, Tenor search, GIF media loading, and sending a GIF were exercised successfully.

  A decrypted PCAPdroid capture of a fresh Tenor search showed `GET /v2/search` and the GIF media fetch, with no `/v2/registershare` request after the GIF was sent.

  See:
  - [Telemetry investigation](docs/telemetry-investigation-gboard-18.0.3.md)
  - [Validation / progress handoff](docs/telemetry-patch-progress.md)
</details>

## Use this fork

Add this repository as a Morphe source:

- [Open this fork in Morphe](https://morphe.software/add-source?github=ioneng/Gboardwu-privacy)
- Or manually add `https://github.com/ioneng/Gboardwu-privacy`

## Upstream README

The upstream project already documents the full GboardWu / Gboard Patches feature set, installation details, build instructions, and other project information.

**Read the original upstream README here:**

[jasonwu1994/Gboard-patches — README.md](https://github.com/jasonwu1994/Gboard-patches/blob/main/README.md)
