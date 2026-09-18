package dev.jason.gboardpatches.patches.gboard.features.telemetry

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Test

class GboardTelemetrySourceContractTest {
    @Test
    fun `telemetry patch keeps functional APIs out of scope`() {
        val bytecode = read(
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/telemetry/" +
                "GboardTelemetryBytecodePatch.kt",
        )
        val manifest = read(
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/telemetry/" +
                "GboardTelemetryManifestPatch.kt",
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

        check("android.net.http.EnableTelemetry" in manifest)
        check("setManifestAndroidAttribute(\"value\", \"false\")" in manifest)
    }

    private fun read(relative: String): String = Files.readString(repoRoot().resolve(relative))

    private fun repoRoot(): Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("patches/src/main/kotlin")) }
}
