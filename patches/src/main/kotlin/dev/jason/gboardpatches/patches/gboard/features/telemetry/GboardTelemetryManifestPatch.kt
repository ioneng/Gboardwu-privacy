package dev.jason.gboardpatches.patches.gboard.features.telemetry

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.manifestAndroidAttribute
import dev.jason.gboardpatches.patches.gboard.shared.setManifestAndroidAttribute
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Document

internal val gboardTelemetryManifestPatch = resourcePatch(
    description = "停用 Cronet 的 Android StatsLog 遙測，同時保留 Cronet 網路功能。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        this["res/xml/$NETWORK_SECURITY_CONFIG_FILE", false].apply {
            parentFile.mkdirs()
            writeText(NETWORK_SECURITY_CONFIG_XML)
        }
        document("AndroidManifest.xml").use(::applyGboardTelemetryManifest)
    }
}

internal fun applyGboardTelemetryManifest(document: Document) {
    val manifest = document.documentElement
    val application = manifest.childElements("application").singleOrNull()
        ?: error("Expected exactly one application element in AndroidManifest.xml")

    val telemetryEntries = application.childElements("meta-data")
        .filter { metaData ->
            metaData.manifestAndroidAttribute("name") == CRONET_TELEMETRY_META_DATA
        }
        .toList()
    check(telemetryEntries.size <= 1) {
        "Expected at most one $CRONET_TELEMETRY_META_DATA manifest meta-data entry; " +
            "found ${telemetryEntries.size}"
    }
    val telemetryEntry = telemetryEntries.singleOrNull()
        ?: document.createElement("meta-data").also(application::appendChild)
    val currentTelemetryValue = telemetryEntry.manifestAndroidAttribute("value")
    if (currentTelemetryValue != null) {
        check(currentTelemetryValue == "true" || currentTelemetryValue == "false") {
            "Unexpected $CRONET_TELEMETRY_META_DATA value: $currentTelemetryValue"
        }
    }
    telemetryEntry.setManifestAndroidAttribute("name", CRONET_TELEMETRY_META_DATA)
    telemetryEntry.setManifestAndroidAttribute("value", "false")

    val validationEntries = application.childElements("meta-data")
        .filter { metaData ->
            metaData.manifestAndroidAttribute("name") == MITM_VALIDATION_MARKER
        }
        .toList()
    check(validationEntries.size <= 1) {
        "Expected at most one $MITM_VALIDATION_MARKER manifest meta-data entry; " +
            "found ${validationEntries.size}"
    }
    val validationEntry = validationEntries.singleOrNull()
        ?: document.createElement("meta-data").also(application::appendChild)
    validationEntry.setManifestAndroidAttribute("name", MITM_VALIDATION_MARKER)
    validationEntry.setManifestAndroidAttribute("value", MITM_VALIDATION_MARKER_VALUE)

    val currentNetworkSecurityConfig = application.manifestAndroidAttribute("networkSecurityConfig")
    check(
        currentNetworkSecurityConfig == null ||
            currentNetworkSecurityConfig == NETWORK_SECURITY_CONFIG_RESOURCE,
    ) {
        "Refusing to replace existing android:networkSecurityConfig: $currentNetworkSecurityConfig"
    }
    application.setManifestAndroidAttribute("networkSecurityConfig", NETWORK_SECURITY_CONFIG_RESOURCE)
}

internal const val CRONET_TELEMETRY_META_DATA = "android.net.http.EnableTelemetry"
internal const val NETWORK_SECURITY_CONFIG_FILE = "network_security_config.xml"
internal const val NETWORK_SECURITY_CONFIG_RESOURCE = "@xml/network_security_config"
internal const val MITM_VALIDATION_MARKER = "dev.jason.gboardpatches.validation.telemetry_mitm"
internal const val MITM_VALIDATION_MARKER_VALUE = "execute-v3"
internal val NETWORK_SECURITY_CONFIG_XML = """
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
