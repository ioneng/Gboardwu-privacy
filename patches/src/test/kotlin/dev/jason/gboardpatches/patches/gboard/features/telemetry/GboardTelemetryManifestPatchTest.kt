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

        val entry = document.documentElement.childElements("application").single()
            .childElements("meta-data").single()
        assertEquals("false", entry.manifestAndroidAttribute("value"))
    }

    @Test
    fun `missing cronet telemetry flag is rejected`() {
        val document = parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test">
              <application/>
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
