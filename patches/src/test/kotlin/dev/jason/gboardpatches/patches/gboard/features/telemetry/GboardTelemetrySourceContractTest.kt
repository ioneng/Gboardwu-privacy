package dev.jason.gboardpatches.patches.gboard.features.telemetry

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Test

class GboardTelemetrySourceContractTest {
    @Test
    fun `telemetry patch keeps functional APIs out of scope and exposes runtime policy guards`() {
        val bytecode = read(
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/telemetry/" +
                "GboardTelemetryBytecodePatch.kt",
        )
        val marker = read(
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/telemetry/" +
                "GboardTelemetryFeatureMarkerPatch.kt",
        )

        listOf(
            "Llvf;->l(Lkth;)Llsz;",
            "Lprn;->b()Z",
            "Llcw;->aZ(Ljava/lang/Object;)Llsz;",
            "Llbu;",
            "Llbr;",
            "Llbo;",
            "DailyPingWorker;",
            "Primes-nativecrash-sidecar",
            "PrimesLifeboatReceiver",
            "Lqvz;->j:Lnxp;",
            "Failed to register Tenor share",
            "goto/32 :tenor_after_register_share",
            "Lacru;",
            "android.net.http.EnableTelemetry",
            "TELEMETRY_RUNTIME_SHOULD_BLOCK_CLEARCUT",
            "TELEMETRY_RUNTIME_SHOULD_BLOCK_GOOGLE_PLAY_SERVICES",
            "TELEMETRY_RUNTIME_SHOULD_BLOCK_DAILY_PING",
            "TELEMETRY_RUNTIME_SHOULD_BLOCK_PRIMES",
            "TELEMETRY_RUNTIME_SHOULD_BLOCK_TENOR",
            "TELEMETRY_RUNTIME_SHOULD_BLOCK_CRONET",
        ).forEach { token -> check(token in bytecode) { "Missing $token" } }

        listOf(
            "GoogleAuth.API",
            "PseudonymousId.API",
            "TrustedTime.API",
            "ModuleInstall.API",
            "Audit.API",
            "AppDoctorReceiver",
            "VoiceDonation",
            "android.permission.INTERNET",
            "networkSecurityConfig",
            "usesCleartextTraffic",
        ).forEach { token -> check(token !in bytecode) { "Out-of-scope token $token" } }

        check("dev.jason.gboardpatches.feature.telemetry_blocking" in marker)
        check("android.net.http.EnableTelemetry" !in marker)
        check("setManifestAndroidAttribute(\"value\", \"false\")" !in marker)
    }

    private fun read(relative: String): String = Files.readString(repoRoot().resolve(relative))

    private fun repoRoot(): Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("patches/src/main/kotlin")) }
}
