<h1 align="center">Gboard Privacy Patches</h1>

<p align="center">
  A privacy-focused fork of <a href="https://github.com/jasonwu1994/Gboard-patches">jasonwu1994/Gboard-patches</a>, retaining the upstream feature set while adding privacy hardening on top.
</p>

<p align="center">
  <a href="https://github.com/jasonwu1994/Gboard-patches"><img alt="Upstream project" src="https://img.shields.io/badge/Upstream-jasonwu1994%2FGboard--patches-181717?style=for-the-badge"></a>
  <a href="https://morphe.software/add-source?github=ioneng/Gboardwu-privacy"><img alt="Add this fork to Morphe" src="https://img.shields.io/badge/Morphe-Add%20Privacy%20Fork-00A8FF?style=for-the-badge"></a>
</p>

<p align="center">
  Upstream project and original feature work by <a href="https://github.com/jasonwu1994">jasonwu1994</a>.
</p>

## Overview

This repository is a fork of the original [Gboard Patches](https://github.com/jasonwu1994/Gboard-patches) project. The existing feature set, patch architecture, and most non-privacy functionality are inherited from upstream.

The main focus of this fork is to **add privacy-focused patches on top of the upstream project** while continuing to benefit from upstream features and updates. Privacy additions aim to reduce optional telemetry, diagnostics, crash/performance reporting, and tracking sidecars without disabling network activity that is required for explicitly requested Gboard features.

Where practical, fork-specific privacy work is kept isolated from upstream feature implementations so future upstream changes can be merged with minimal conflict.

## Included Patches

### Upstream Project-Built Features

These features are inherited from the original Gboard Patches project. They were designed and built upstream rather than being privacy-specific additions of this fork.

<details>
  <summary><code>Clipboard Enhancements</code></summary>

  Lets you enhance clipboard retention time, item count limits, preview lines, countdown and creation-time labels, order index, grid columns, and optionally render only the first 1,000 characters on each clipboard card.
</details>

<details>
  <summary><code>Web Clipboard</code></summary>

  Hosts a phone-powered Web Clipboard portal that lets desktop browsers sync with Gboard over the same LAN, with a pairing code gate and an optional Quick Settings Tile.

  Preview:

  <img alt="Web Clipboard pairing gate" src="docs/assets/features/web-clipboard/01-pairing-gate.png" width="720">

  <img alt="Web Clipboard conversation view" src="docs/assets/features/web-clipboard/02-conversation-view.png" width="720">

</details>

<details>
  <summary><code>Floating Web Search</code></summary>

  Open a floating web page directly from Gboard to quickly search for the information you need.
</details>

<details>
  <summary><code>FTP Server</code></summary>

  Hosts an FTP server on your phone so desktop FTP clients can browse, upload, download, and resume file transfers over the same LAN. It supports anonymous or password-protected access, a configurable control and passive port range, read-only mode, <code>/sdcard</code> or a user-selected folder as the root, live transfer progress, retained partial uploads, and an optional Quick Settings Tile.
</details>

<details>
  <summary><code>Long-Press Editing Shortcuts</code></summary>

  Add Select all, Undo, Copy, Cut, Paste, and Redo long-press shortcuts to English QWERTY and Zhuyin, with an optional globe-key drag gesture that follows the same physical key positions across supported alphabet layouts.
</details>

<details>
  <summary><code>Swipeable Custom Top Row</code></summary>

  Lets you swipe the keyboard top row horizontally to open customizable text and JavaScript slots.
</details>

<details>
  <summary><code>Incognito Mode Toggle</code></summary>

  Add an Incognito toggle to the Access Point toolbar and configure clipboard and voice typing availability while Incognito mode is active.
</details>

<details>
  <summary><code>Custom Symbols</code></summary>

  Adds a dedicated symbols tab and a quick access entry from the comma long-press popup.
</details>

<details>
  <summary><code>Simple Calculator</code></summary>

  Adds an optional inline calculator for arithmetic expressions typed in any text field. The result appears in Gboard's suggestion row; tap it to replace the expression, or long-press it to copy the result.
</details>

<details>
  <summary><code>G Logo on Spacebar</code></summary>

  Show the G Logo on the spacebar and hide the language label.
</details>

<details>
  <summary><code>Rounded Keyboard Panel</code></summary>

  Customize which corners of the keyboard panel are rounded, and set the top and bottom radii separately.
</details>

<details>
  <summary><code>Latin Globe Key Ignore Interval</code></summary>

  Add an independent English globe key ignore interval override for post-typing language-switch delay.
</details>

<details>
  <summary><code>Emojis, stickers & GIFs Tab Order</code></summary>

Customize the bottom tab order in Gboard's Emojis, stickers & GIFs panel with drag-and-drop reordering.
</details>

<details>
  <summary><code>Backup &amp; Restore</code></summary>

  Exports all Gboard Patches settings to a portable JSON backup and restores only the modules you select, with per-module and per-key results. It also exports, compares, and restores Gboard's raw PB/XML flag-store files.
</details>

### Privacy Patches

Privacy-focused patches added by this fork reduce optional analytics and reporting while preserving functional Gboard features and the network requests those features need to operate.

<details>
  <summary><code>Block Gboard Telemetry</code></summary>

  Suppresses dedicated telemetry, diagnostics, performance/crash reporting, and tracking sidecars identified in the supported Gboard 18.0.3 build, while deliberately leaving feature-required network activity intact.

  **Implemented scope**

  - central Clearcut event submission, covering Gboard logging and ML Kit / OCR `FIREBASE_ML_SDK` logging;
  - Gboard's Clearcut logger creation gate as defence in depth;
  - Google Play Services `ClientTelemetry`, `ClientThrottlingTelemetry`, and `ClientNotificationTelemetry` reporting;
  - Daily Ping periodic metrics;
  - Primes startup, native crash sidecar, and Lifeboat crash retransmission;
  - Tenor `/v2/registershare` share tracking only;
  - Cronet Android StatsLog telemetry via `android.net.http.EnableTelemetry=false`.

  **Explicitly retained by design**

  - UsageReporting / Usage & diagnostics consent plumbing;
  - Audit API consent/compliance records;
  - AppDoctor remote remediation;
  - authentication and account operations;
  - OCR execution itself;
  - voice recognition and Agentic Dictation functional requests;
  - model/module downloads and remote configuration;
  - Tenor search, download, and result delivery;
  - Voice Donation consent plumbing;
  - other network traffic required for explicitly requested Gboard features.

  See the [comprehensive telemetry investigation](docs/telemetry-investigation-gboard-18.0.3.md) and [implementation progress / validation handoff](docs/telemetry-patch-progress.md) for technical details and current validation status.
</details>

### Upstream Gboard Feature Unlocks

These hidden-setting and rollout-gate unlocks are inherited from the upstream Gboard Patches project.

<details>
  <summary><code>AI Writing Tools</code></summary>

  Enables the <code>Text correction &gt; Writing tools</code> setting with support for all languages.
</details>

<details>
  <summary><code>Advanced Voice Typing</code></summary>

  Enable Advanced Voice Typing with automatic punctuation, and separately enable automatic punctuation for Traditional Chinese voice typing, which does not support Advanced Voice Typing.
</details>

<details>
  <summary><code>Enable OCR / Scan Text</code></summary>

  Enable the OCR / Scan Text feature with Latin, Chinese, Japanese, Korean, and Devanagari recognition backends.
</details>

<details>
  <summary><code>English QWERTY Up-Flick Uppercase</code></summary>

  Flick up on the English QWERTY keyboard to toggle uppercase and lowercase.
</details>

<details>
  <summary><code>Enable Inline Autofill Suggestions</code></summary>

  Enables inline autofill suggestions in supported contexts.
</details>

<details>
  <summary><code>Grammar Checker</code></summary>

  Enables the <code>Text correction &gt; Grammar check</code> setting and its related rollout gate.
</details>

<details>
  <summary><code>Inline Suggestions</code></summary>

  Enables the <code>Text correction &gt; Smart Compose</code> setting and its related rollout gate.
</details>

<details>
  <summary><code>Key Shape Selection</code></summary>

  Enables the <code>Key shape</code> option inside theme details without forcing rounded keys by default.
</details>

<details>
  <summary><code>Use Bluetooth Microphone</code></summary>

  Enables the <code>Voice typing &gt; Use Bluetooth microphone</code> setting and its related rollout gate.
</details>

<details>
  <summary><code>Change emoji size</code></summary>

  Enables Gboard's emoji size setting.
</details>

<details>
  <summary><code>Enable cursor trackpad mode</code></summary>

  Enables the long-press-spacebar trackpad, cursor lock mode, and the required scrub-move preference.
</details>

<details>
  <summary><code>Enable split keyboard</code></summary>

  Enables Gboard's split keyboard layout.
</details>

<details>
  <summary><code>Enable accessibility layout</code></summary>

  Enables accessibility layout.
</details>

<details>
  <summary><code>Quick Insert</code></summary>

Enables the Quick Insert panel and toolbar access point.
</details>

<details>
  <summary><code>Hyperspeed Typing Animation</code></summary>

Shows the animation during sustained fast typing with support for all keyboards.
</details>

<details>
  <summary><code>Close Proactive Suggestions</code></summary>

Shows a dismiss button in the proactive suggestions bar.
</details>

<details>
  <summary><code>Clipboard Custom Character Limit</code></summary>

  Lets you set the maximum number of characters stored for each text clipboard item, with Gboard's stock 20,000-character limit as the default.
</details>

<details>
  <summary><code>Access Points menu style</code></summary>

  Lets you switch between the new and legacy Access Points menu styles.
</details>

<details>
  <summary><code>Top Toolbar Item Count</code></summary>

  Lets you customize the top toolbar item count.
</details>

<details>
  <summary><code>Settings Homepage Override</code></summary>

  Lets you switch between the new and legacy Gboard settings homepage styles.
</details>

<details>
  <summary><code>Developer options</code></summary>

  Enable Developer options and the Flag Editor, allowing you to modify flag values.
</details>

<details>
  <summary><code>Package Rename</code></summary>

  Renames the patched package so it can be installed alongside the official Gboard app.
</details>

### Upstream Taiwan-focused Features

These Traditional Chinese and Zhuyin workflow features are inherited from the upstream Gboard Patches project.

<details>
  <summary><code>Zhuyin Slide Input</code></summary>

  On the Zhuyin keyboard, swipe up or down to enter English letters without switching to another keyboard layout.
</details>

<details>
  <summary><code>Zhuyin Quick Traditional/Simplified Toggle</code></summary>

  Swipe up on the Zhuyin <code>ㄥ</code> key to quickly toggle between Traditional and Simplified Chinese.
</details>

<details>
  <summary><code>Zhuyin Bottom Row Key Sizes</code></summary>

  Adjusts the seven bottom-row slot sizes on the Zhuyin keyboard, including <code>?123</code>, <code>，</code>, the globe key, space, <code>ㄦ</code>, backspace, and the IME action key.
</details>

## Install

Add this privacy-focused fork as a Morphe source:

- [Open this fork in Morphe](https://morphe.software/add-source?github=ioneng/Gboardwu-privacy)
- Or manually add `https://github.com/ioneng/Gboardwu-privacy`

Original upstream project: [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches)

## Build

Before running Gradle locally, authenticate to Morphe's GitHub Packages registry with either:

- `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`
- `GITHUB_ACTOR` and `GITHUB_TOKEN` as environment variables

Build the Android patch bundle:

```powershell
.\gradlew.bat :patches:buildAndroid
```

Regenerate patch metadata:

```powershell
.\gradlew.bat generatePatchesList
```

Generated outputs:

- `patches/build/libs/*.mpp`
- `patches-list.json`
- `patches-bundle.json`

## License

Released under the [GNU General Public License v3.0](LICENSE).
