package dev.jason.gboardpatches.patches.gboard.registry

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardPrivacyForkReadmeContractTest {
    @Test
    fun `fork readme documents only fork additions and delegates upstream feature docs`() {
        val readme = Files.readString(
            repositoryRoot().resolve("README.md"),
            StandardCharsets.UTF_8,
        )

        assertTrue(readme.contains("<h1 align=\"center\">GboardWu Privacy</h1>"))
        assertTrue(readme.contains("**Block Gboard Telemetry**"))
        assertTrue(
            readme.contains(
                "https://github.com/jasonwu1994/Gboard-patches/blob/main/README.md",
            ),
        )
        assertTrue(readme.contains("keeps the upstream GboardWu / Gboard Patches feature set"))
        assertFalse(readme.contains("## Included Patches"))
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
    }
}
