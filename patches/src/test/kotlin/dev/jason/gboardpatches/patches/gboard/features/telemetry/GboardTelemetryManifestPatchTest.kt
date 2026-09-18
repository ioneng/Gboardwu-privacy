package dev.jason.gboardpatches.patches.gboard.features.telemetry

import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.manifestAndroidAttribute
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GboardTelemetryManifestPatchTest {
    @Test
    fun `cronet telemetry flag is forced off idempotently`() {
        val document = parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test">
              <application>
                <meta-data android:name="$CRONET_TELEMETRY_META_DATA" android:value="true"/>
              </application>
            </manifest>
            """.trimIndent(),
        )

        applyGboardTelemetryManifest(document)
        applyGboardTelemetryManifest(document)

        val application = document.documentElement.childElements("application").single()
        val telemetryEntries = application.childElements("meta-data")
            .filter { it.manifestAndroidAttribute("name") == CRONET_TELEMETRY_META_DATA }
            .toList()
        val validationEntries = application.childElements("meta-data")
            .filter { it.manifestAndroidAttribute("name") == MITM_VALIDATION_MARKER }
            .toList()

        assertEquals(1, telemetryEntries.size)
        assertEquals("false", telemetryEntries.single().manifestAndroidAttribute("value"))
        assertEquals(1, validationEntries.size)
        assertEquals(
            MITM_VALIDATION_MARKER_VALUE,
            validationEntries.single().manifestAndroidAttribute("value"),
        )
        assertEquals(
            NETWORK_SECURITY_CONFIG_RESOURCE,
            application.manifestAndroidAttribute("networkSecurityConfig"),
        )
    }

    @Test
    fun `missing cronet telemetry flag is created disabled`() {
        val document = parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test">
              <application/>
            </manifest>
            """.trimIndent(),
        )

        applyGboardTelemetryManifest(document)

        val application = document.documentElement.childElements("application").single()
        val telemetryEntry = application.childElements("meta-data")
            .single { it.manifestAndroidAttribute("name") == CRONET_TELEMETRY_META_DATA }
        assertEquals("false", telemetryEntry.manifestAndroidAttribute("value"))
        assertEquals(
            NETWORK_SECURITY_CONFIG_RESOURCE,
            application.manifestAndroidAttribute("networkSecurityConfig"),
        )
    }

    @Test
    fun `duplicate cronet telemetry flags are rejected`() {
        val document = parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test">
              <application>
                <meta-data android:name="$CRONET_TELEMETRY_META_DATA" android:value="true"/>
                <meta-data android:name="$CRONET_TELEMETRY_META_DATA" android:value="false"/>
              </application>
            </manifest>
            """.trimIndent(),
        )

        assertThrows(IllegalStateException::class.java) {
            applyGboardTelemetryManifest(document)
        }
    }

    @Test
    fun `existing different network security config is rejected`() {
        val document = parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test">
              <application android:networkSecurityConfig="@xml/existing_config">
                <meta-data android:name="$CRONET_TELEMETRY_META_DATA" android:value="true"/>
              </application>
            </manifest>
            """.trimIndent(),
        )

        assertThrows(IllegalStateException::class.java) {
            applyGboardTelemetryManifest(document)
        }
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
}
