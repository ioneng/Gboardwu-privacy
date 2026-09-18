package dev.jason.gboardpatches.patches.gboard.validation

import app.morphe.patcher.patch.ResourcePatchContext
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.manifestAndroidAttribute
import dev.jason.gboardpatches.patches.gboard.shared.setManifestAndroidAttribute
import org.w3c.dom.Document

context(context: ResourcePatchContext)
internal fun applyMitmValidationResources() = with(context) {
    this["res/xml/$MITM_NETWORK_SECURITY_CONFIG_FILE", false].apply {
        parentFile.mkdirs()
        writeText(MITM_NETWORK_SECURITY_CONFIG_XML)
    }
    document("AndroidManifest.xml").use(::applyMitmValidationManifest)
}

internal fun applyMitmValidationManifest(document: Document) {
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

internal const val MITM_NETWORK_SECURITY_CONFIG_FILE =
    "gboard_mitm_control_network_security_config.xml"
internal const val MITM_NETWORK_SECURITY_CONFIG_RESOURCE =
    "@xml/gboard_mitm_control_network_security_config"

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
