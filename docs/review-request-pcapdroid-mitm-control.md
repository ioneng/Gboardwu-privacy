# Review request: PCAPdroid MITM control build does not trust user CA

## Status

Investigation requested.

The configurable telemetry validation build can decrypt Tenor HTTPS through PCAPdroid when a temporary user-CA trust configuration is added. Attempts to create an equivalent pre-telemetry GboardWu control build with the same trust intent have failed on-device.

The failure is explicit in PCAPdroid:

```text
The client does not trust the proxy's certificate for tenor.googleapis.com
(OpenSSL Error([('SSL routines', '', 'sslv3 alert certificate unknown')]))
```

Do not treat the current control builds as valid A/B controls until this is explained.

## Goal

Create a clean control for the Tenor telemetry test:

```text
CONTROL
pre-telemetry GboardWu
+ Package Rename / normal selected patches
+ temporary PCAPdroid user-CA trust
+ no telemetry blocker
+ no telemetry runtime/settings
+ no Cronet telemetry override

TEST
current configurable telemetry patch
+ all six telemetry blocks OFF
+ the same temporary PCAPdroid user-CA trust
```

The purpose is to determine whether stock/pre-telemetry GboardWu issues `/v2/registershare` for the same Tenor GIF search-and-send sequence.

## Known-good reference

Branch:

```text
validation/configurable-telemetry-mitm
```

Commit:

```text
2a5dac4f2da58d97b69b95157748dcb8d54f0370
```

Base configurable commit:

```text
c434700d5b5266321528d0ac6aa2252b10776dc8
```

CI run:

```text
35343494400
```

In this build the telemetry feature-marker resource patch writes a network-security config containing:

```xml
<network-security-config>
    <base-config>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

and sets:

```text
android:networkSecurityConfig="@xml/gboard_telemetry_mitm_network_security_config"
```

Observed on-device result:

- PCAPdroid successfully exposed readable Tenor HTTP requests.
- `GET /v2/autocomplete` was visible.
- `GET /v2/search` was visible.
- GIF requests from `media.tenor.com` were visible.
- Some individual streams later showed `stream reset by client (CANCEL)`, but the client did accept the proxy CA and substantial HTTPS traffic was decrypted.
- With all telemetry blocks OFF, `/v2/registershare` was not found in the visible HTTP request list. This is why a clean pre-telemetry control is needed.

Important: this validation build intentionally did not restore the old Cronet manifest override. The configurable telemetry bytecode remained the production candidate.

## Control base

The control branch was deliberately based on the last pre-telemetry commit:

```text
ca0ebe6e36f19cad382ef70f6ae783c770917263
```

Branch:

```text
validation/gboardwu-mitm-control
```

The control must remain free of the telemetry patch.

## Failed control attempt 1

The first control attached a temporary CA-trust resource patch as a dependency of `Add Gboard Signature Bypass`.

Relevant final commit for that attempt:

```text
e643bcdaf95392982ccf497c7d2ad3a9a25b4d72
```

CI run:

```text
35350121321
```

CI passed after updating the branch-specific contract test.

Problem with the design:

- The CA-trust helper only ran if Signature Bypass was selected.
- That was not a reliable guarantee for the user's actual patch selection.
- On-device PCAPdroid still could not establish the intended trusted MITM path.

This attempt is superseded.

## Failed control attempt 2

The CA-trust resource patch was moved to a dependency of the default-on `Package Rename` public patch.

Commit:

```text
40fc90e9549feaa56ee2c19493148cda2c4eb00e
```

CI run:

```text
35353035720
```

CI passed.

Observed on-device result remained:

```text
The client does not trust the proxy's certificate for tenor.googleapis.com
...
sslv3 alert certificate unknown
```

This proves that simply wiring the helper as a Package Rename dependency did not produce the same effective result as the known-good configurable telemetry MITM build.

## Failed control attempt 3

To remove dependency-order ambiguity, the CA mutation was changed to run directly inside `GboardPackageRenameResourcePatch`.

Final commit:

```text
6e121fef04e491d01834ebdaa0d51784a3efcaf3
```

CI run:

```text
35354120814
```

CI passed.

The implementation:

- writes `res/xml/gboard_mitm_control_network_security_config.xml`;
- sets `android:networkSecurityConfig` directly during the Package Rename resource patch;
- retains system and user trust anchors;
- does not add the telemetry blocker;
- restores the normal public Package Rename dependency contract.

Observed on-device result: still the same certificate rejection. PCAPdroid reports that the GboardWu client does not trust the proxy certificate.

This is the current unexplained failure.

## Why this is interesting

The trust XML concept is proven to work in the configurable telemetry validation build, but apparently not in the pre-telemetry control even when the same trust-anchor semantics are applied.

Therefore the unresolved difference may be outside the obvious XML contents. Possible areas include:

1. The generated control patch may not actually mutate the final installed APK manifest/resource table even though repository-level tests and the MPP build pass.
2. Patch execution/finalization ordering may cause another resource or manifest operation to overwrite or discard `android:networkSecurityConfig`.
3. Package Rename behavior on the older pre-telemetry tree may interact differently with resource compilation, manifest serialization, or resource IDs.
4. The final APK selected by the patcher may not contain the expected XML resource, despite the MPP containing the patch implementation.
5. The known-good configurable validation build may contain another relevant change between `ca0ebe6` and `c434700` that unintentionally affects TLS trust or manifest/resource handling.
6. There may be a difference in the exact patch selection between the working configurable build and the attempted control build.
7. The installed control APK may be stale or may not be the APK produced with the newest MPP. This should be ruled out with direct APK inspection rather than assumed.

These are hypotheses only. None is confirmed.

## Required investigation

Please compare the known-good configurable MITM build with the failed control at the final patched-APK level, not only source level.

At minimum:

1. Patch the same target Gboard:
   `18.0.3.954559732-release-arm64-v8a`.
2. Use the same selected patch set as the user's normal coexistence build.
3. Produce one APK with the known-good configurable MITM patch and one with the pre-telemetry control.
4. Decode or inspect both final APKs.
5. Verify the `<application>` element in each final `AndroidManifest.xml`.
6. Verify the exact `android:networkSecurityConfig` resource reference in each.
7. Verify the referenced XML exists in the final APK and resolves correctly.
8. Compare compiled resource-table entries and IDs if the text-level manifest/XML look identical.
9. Compare all manifest/resource mutations between `ca0ebe6` and `c434700` that could affect network security config handling.
10. Confirm which public patches were actually selected for each generated APK.
11. If possible, add an automated final-artifact assertion so CI fails unless the built test APK contains the expected manifest attribute and both `system` and `user` trust anchors.

Do not assume a green MPP build proves the final patched APK contains the intended CA-trust configuration.

## Reproduction

1. Install PCAPdroid's CA certificate as a user CA.
2. Enable PCAPdroid TLS decryption.
3. Patch and install the control GboardWu build using Package Rename so the app is installed as:
   `dev.jason.com.google.android.inputmethod.latin`.
4. Start a PCAPdroid capture for that app.
5. Open Gboard GIF search and perform a Tenor search.
6. Observe the `tenor.googleapis.com:443` connection.

Current failed control result:

```text
Status: Error
Decryption: Error
Payload: 0 B
The client does not trust the proxy's certificate for tenor.googleapis.com
(OpenSSL Error([('SSL routines', '', 'sslv3 alert certificate unknown')]))
```

Known-good configurable MITM result:

- readable HTTP requests to `tenor.googleapis.com`;
- readable Tenor search/autocomplete requests;
- readable GIF media request;
- therefore the proxy certificate is accepted in that build.

## Constraints

- Keep all CA-trust changes strictly validation-only.
- Do not merge user-CA trust into the production telemetry patch.
- Do not reintroduce the old Cronet manifest telemetry override as part of this investigation.
- The control must remain pre-telemetry so it is a meaningful comparison.
- Do not weaken or remove the telemetry implementation merely to make the control decrypt.
- Prefer final-APK evidence over source-level assumptions.
- Follow `AGENTS.md`, including exact evidence for build/test claims.

## Success criteria

The investigation is complete when one of these is established with final-APK evidence:

1. A pre-telemetry GboardWu control build accepts PCAPdroid's user CA and exposes the same class of decrypted Tenor requests as the configurable validation build; or
2. A concrete, reproducible reason is identified for why the control cannot accept the same CA configuration, with the exact differing artifact or runtime mechanism documented.

Only after a valid control exists should the `/v2/registershare` A/B comparison be treated as decisive.
