package dev.jason.gboardpatches.patches.gboard.features.telemetry

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardTelemetryFeatureMarkerPatch = resourcePatch(
    description = "標記 Telemetry Blocking feature 已被打入 target APK，共用 settings UI 過濾。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(TELEMETRY_FEATURE_MARKER_NAME)
    }
}

internal const val TELEMETRY_FEATURE_MARKER_NAME =
    "dev.jason.gboardpatches.feature.telemetry_blocking"
