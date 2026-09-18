package dev.jason.gboardpatches.patches.gboard.features.telemetry

import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.manifestAndroidAttribute
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GboardTelemetryMitmValidationTest {
    @Test
    fun `user CA trust config is wired idempotently`() {
        val document = parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test">
              <application/>
            </manifest>
            """.trimIndent(),
        )

        applyTelemetryMitmValidationManifest(document)
        applyTelemetryMitmValidationManifest(document)

        val application = document.documentElement.childElements("application").single()
        assertEquals(
            MITM_NETWORK_SECURITY_CONFIG_RESOURCE,
            application.manifestAndroidAttribute("networkSecurityConfig"),
        )
    }

    @Test
    fun `existing different network security config is rejected`() {
        val document = parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test">
              <application android:networkSecurityConfig="@xml/existing_config"/>
            </manifest>
            """.trimIndent(),
        )

        assertThrows(IllegalStateException::class.java) {
            applyTelemetryMitmValidationManifest(document)
        }
    }

    @Test
    fun `validation config trusts system and user CAs`() {
        check(MITM_NETWORK_SECURITY_CONFIG_XML.contains("<certificates src=\"system\" />"))
        check(MITM_NETWORK_SECURITY_CONFIG_XML.contains("<certificates src=\"user\" />"))
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
}
