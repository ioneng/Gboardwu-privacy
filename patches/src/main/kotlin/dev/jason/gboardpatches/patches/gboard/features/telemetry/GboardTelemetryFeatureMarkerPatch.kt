package dev.jason.gboardpatches.patches.gboard.features.telemetry

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.manifestAndroidAttribute
import dev.jason.gboardpatches.patches.gboard.shared.setManifestAndroidAttribute
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Document

internal val gboardTelemetryFeatureMarkerPatch = resourcePatch(
    description = "標記 Telemetry Blocking feature 已被打入 target APK，共用 settings UI 過濾。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        this["res/xml/$MITM_NETWORK_SECURITY_CONFIG_FILE", false].apply {
            parentFile.mkdirs()
            writeText(MITM_NETWORK_SECURITY_CONFIG_XML)
        }
        document("AndroidManifest.xml").use(::applyTelemetryMitmValidationManifest)
    }

    finalize {
        applyFeatureMarker(TELEMETRY_FEATURE_MARKER_NAME)
    }
}

internal fun applyTelemetryMitmValidationManifest(document: Document) {
    val application = document.documentElement.childElements("application").singleOrNull()
        ?: error("Expected exactly one application element in AndroidManifest.xml")
    val currentNetworkSecurityConfig =
        application.manifestAndroidAttribute("networkSecurityConfig")
    check(
        currentNetworkSecurityConfig == null ||
            currentNetworkSecurityConfig == MITM_NETWORK_SECURITY_CONFIG_RESOURCE,
    ) {
        "Refusing to replace existing android:networkSecurityConfig: $currentNetworkSecurityConfig"
    }
    application.setManifestAndroidAttribute(
        "networkSecurityConfig",
        MITM_NETWORK_SECURITY_CONFIG_RESOURCE,
    )
}

internal const val TELEMETRY_FEATURE_MARKER_NAME =
    "dev.jason.gboardpatches.feature.telemetry_blocking"

internal const val MITM_NETWORK_SECURITY_CONFIG_FILE =
    "gboard_telemetry_mitm_network_security_config.xml"
internal const val MITM_NETWORK_SECURITY_CONFIG_RESOURCE =
    "@xml/gboard_telemetry_mitm_network_security_config"

internal val MITM_NETWORK_SECURITY_CONFIG_XML = """
    <?xml version="1.0" encoding="utf-8"?>
    <network-security-config>
        <base-config>
            <trust-anchors>
                <certificates src="system" />
                <certificates src="user" />
            </trust-anchors>
        </base-config>
    </network-security-config>
""".trimIndent() + "\n"
