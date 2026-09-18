package dev.jason.gboardpatches.patches.gboard.features.telemetry

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
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
        copyResources(
            "gboard/validation",
            ResourceGroup("xml", NETWORK_SECURITY_CONFIG_FILE),
        )
    }

    finalize {
        document("AndroidManifest.xml").use(::applyGboardTelemetryManifest)
    }
}

internal fun applyGboardTelemetryManifest(document: Document) {
    val manifest = document.documentElement
    val application = manifest.childElements("application").singleOrNull()
        ?: error("Expected exactly one application element in AndroidManifest.xml")
    val entries = application.childElements("meta-data")
        .filter { metaData ->
            metaData.manifestAndroidAttribute("name") == CRONET_TELEMETRY_META_DATA
        }
        .toList()
    check(entries.size == 1) {
        "Expected exactly one $CRONET_TELEMETRY_META_DATA manifest meta-data entry; " +
            "found ${entries.size}"
    }
    val entry = entries.single()
    val current = entry.manifestAndroidAttribute("value")
    check(current == "true" || current == "false") {
        "Unexpected $CRONET_TELEMETRY_META_DATA value: $current"
    }
    entry.setManifestAndroidAttribute("value", "false")

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
